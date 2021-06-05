package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.annotation.{JsonCreator, JsonIgnoreProperties}
import com.fasterxml.jackson.core.{
  JsonFactory,
  JsonParseException,
  JsonParser,
  JsonProcessingException,
  JsonToken
}
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.databind.exc.{
  InvalidDefinitionException,
  InvalidFormatException,
  MismatchedInputException,
  UnrecognizedPropertyException
}
import com.fasterxml.jackson.databind.introspect._
import com.fasterxml.jackson.databind.util.SimpleBeanPropertyDefinition
import com.twitter.util.WrappedValue
import com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.ValidationError
import com.twitter.util.jackson.caseclass.exceptions.{
  CaseClassFieldMappingException,
  CaseClassMappingException,
  InjectableValuesException,
  _
}
import com.twitter.util.logging.Logging
import com.twitter.util.reflect.{Annotations, Types => ReflectionTypes}
import com.twitter.util.validation.ScalaValidator
import com.twitter.util.validation.conversions.ConstraintViolationOps._
import com.twitter.util.validation.conversions.PathOps._
import com.twitter.util.validation.metadata.{ExecutableDescriptor, MethodDescriptor}
import jakarta.validation.{ConstraintViolation, Payload}
import java.lang.annotation.Annotation
import java.lang.reflect.{
  Constructor,
  Executable,
  InvocationTargetException,
  Method,
  Parameter,
  ParameterizedType,
  Type,
  TypeVariable
}
import org.json4s.reflect.{ClassDescriptor, ConstructorDescriptor, Reflector, ScalaType}
import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

private object CaseClassDeserializer {
  // For reporting an InvalidDefinitionException
  val EmptyJsonParser: JsonParser = new JsonFactory().createParser("")

  /* For supporting JsonCreator */
  case class CaseClassCreator(
    executable: Executable,
    propertyDefinitions: Array[PropertyDefinition],
    executableValidationDescription: Option[ExecutableDescriptor],
    executableValidationMethodDescriptions: Option[Array[MethodDescriptor]])

  /* Holder for a fully specified JavaType with generics and a Jackson BeanPropertyDefinition */
  case class PropertyDefinition(
    javaType: JavaType,
    scalaType: ScalaType,
    beanPropertyDefinition: BeanPropertyDefinition)

  /* Holder of constructor arg to ScalaType */
  case class ConstructorParam(name: String, scalaType: ScalaType)

  private[this] def applyPropertyNamingStrategy(
    config: DeserializationConfig,
    fieldName: String
  ): String =
    config.getPropertyNamingStrategy.nameForField(config, null, fieldName)

  // Validate `Constraint` annotations for all fields.
  def executeFieldValidations(
    validatorOption: Option[ScalaValidator],
    executableDescriptorOption: Option[ExecutableDescriptor],
    config: DeserializationConfig,
    constructorValues: Array[Object],
    fields: Array[CaseClassField]
  ): Seq[CaseClassFieldMappingException] = (validatorOption, executableDescriptorOption) match {
    case (Some(validator), Some(executableDescriptor)) =>
      val violations: Set[ConstraintViolation[Any]] =
        validator.forExecutables
          .validateExecutableParameters[Any](
            executableDescriptor,
            constructorValues.asInstanceOf[Array[Any]],
            fields.map(_.name)
          )
      if (violations.nonEmpty) {
        val results = mutable.ListBuffer[CaseClassFieldMappingException]()
        val iterator = violations.iterator
        while (iterator.hasNext) {
          val violation: ConstraintViolation[Any] = iterator.next()
          results.append(
            CaseClassFieldMappingException(
              CaseClassFieldMappingException.PropertyPath.leaf(
                applyPropertyNamingStrategy(config, violation.getPropertyPath.getLeafNode.toString)
              ),
              CaseClassFieldMappingException.Reason(
                message = violation.getMessage,
                detail = ValidationError(
                  violation,
                  CaseClassFieldMappingException.ValidationError.Field,
                  violation.getDynamicPayload(classOf[Payload])
                )
              )
            )
          )
        }
        results.toSeq
      } else Seq.empty[CaseClassFieldMappingException]
    case _ =>
      Seq.empty[CaseClassFieldMappingException]
  }

  // We do not want to "leak" method names, so we only want to return the leaf node
  // if the ConstraintViolation PropertyPath size is greater than 1 element.
  private[this] def getMethodValidationViolationPropertyPath(
    config: DeserializationConfig,
    violation: ConstraintViolation[_]
  ): CaseClassFieldMappingException.PropertyPath = {
    val propertyPath = violation.getPropertyPath
    val iterator = propertyPath.iterator.asScala
    if (iterator.hasNext) {
      iterator.next() // move the iterator
      if (iterator.hasNext) { // has another element, return the leaf
        CaseClassFieldMappingException.PropertyPath.leaf(
          applyPropertyNamingStrategy(config, propertyPath.getLeafNode.toString)
        )
      } else { // does not have another element, return empty
        CaseClassFieldMappingException.PropertyPath.Empty
      }
    } else {
      CaseClassFieldMappingException.PropertyPath.Empty
    }
  }

  // Validate all methods annotated with `MethodValidation` defined in the deserialized case class.
  // This is called after the case class is created.
  def executeMethodValidations(
    validatorOption: Option[ScalaValidator],
    methodDescriptorsOption: Option[Array[MethodDescriptor]],
    config: DeserializationConfig,
    obj: Any
  ): Seq[CaseClassFieldMappingException] = (validatorOption, methodDescriptorsOption) match {
    case (Some(validator), Some(methodDescriptors)) =>
      try {
        validator.forExecutables
          .validateMethods[Any](methodDescriptors, obj).map { violation: ConstraintViolation[Any] =>
            CaseClassFieldMappingException(
              getMethodValidationViolationPropertyPath(config, violation),
              CaseClassFieldMappingException.Reason(
                message = violation.getMessage,
                detail = ValidationError(
                  violation,
                  CaseClassFieldMappingException.ValidationError.Method,
                  violation.getDynamicPayload(classOf[Payload]))
              )
            )
          }.toSeq
      } catch {
        case NonFatal(e: InvocationTargetException) if e.getCause != null =>
          // propagate the InvocationTargetException
          throw e.getCause
      }
    case _ =>
      Seq.empty[CaseClassFieldMappingException]
  }
}

/**
 * Custom case class deserializer which overcomes limitations in jackson-scala-module.
 *
 * Our improvements:
 * - Throws a [[JsonMappingException]] when non-`Option` fields are missing in the incoming JSON.
 * - Does not allow for case class constructor fields to ever be constructed with a `null` reference.
 *    Any field which is marked to not be read from the incoming JSON must either be injected with a
 *    configured InjectableValues implementation or have a default value which can be supplied when
 *    the deserializer constructs the case class. Otherwise an error will be returned.
 * - Use default values when fields are missing in the incoming JSON.
 * - Properly deserialize a `Seq[Long]` (see https://github.com/FasterXML/jackson-module-scala/issues/62)
 * - Support "wrapped values" using [[WrappedValue]].
 * - Support for field and method level validations.
 *
 * The following Jackson annotations which affect deserialization are not explicitly supported by
 * this deserializer (note this list may not be exhaustive):
 * - @JsonPOJOBuilder
 * - @JsonAlias
 * - @JsonSetter
 * - @JsonAnySetter
 * - @JsonTypeName
 * - @JsonUnwrapped
 * - @JsonManagedReference
 * - @JsonBackReference
 * - @JsonIdentityInfo
 * - @JsonTypeIdResolver
 *
 * For many, this is because the behavior of these annotations is ambiguous when it comes to application on
 * constructor arguments of a Scala case class during deserialization.
 *
 * @see [[https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations]]
 *
 * @note This class is inspired by Jerkson's CaseClassDeserializer which can be found here:
 *       [[https://github.com/codahale/jerkson/blob/master/src/main/scala/com/codahale/jerkson/deser/CaseClassDeserializer.scala]]
 */
/* exposed for testing */
private[jackson] class CaseClassDeserializer(
  javaType: JavaType,
  config: DeserializationConfig,
  beanDescription: BeanDescription,
  validator: Option[ScalaValidator])
    extends JsonDeserializer[AnyRef]
    with Logging {
  import CaseClassDeserializer._

  private[this] val clazz: Class[_] = javaType.getRawClass
  // we explicitly do not read a mix-in for a primitive type
  private[this] val mixinClazz: Option[Class[_]] =
    Option(config.findMixInClassFor(clazz))
      .flatMap(m => if (m.isPrimitive) None else Some(m))
  private[this] val clazzDescriptor: ClassDescriptor =
    Reflector.describe(clazz).asInstanceOf[ClassDescriptor]
  private[this] val clazzAnnotations: Array[Annotation] =
    mixinClazz.map(_.getAnnotations) match {
      case Some(mixinAnnotations) =>
        clazz.getAnnotations ++ mixinAnnotations
      case _ =>
        clazz.getAnnotations
    }

  private[this] val caseClazzCreator: CaseClassCreator = {
    val fromCompanion: Option[AnnotatedMethod] =
      beanDescription.getFactoryMethods.asScala.find(_.hasAnnotation(classOf[JsonCreator]))
    val fromClazz: Option[AnnotatedConstructor] =
      beanDescription.getConstructors.asScala.find(_.hasAnnotation(classOf[JsonCreator]))

    fromCompanion match {
      case Some(jsonCreatorAnnotatedMethod) =>
        CaseClassCreator(
          jsonCreatorAnnotatedMethod.getAnnotated,
          getBeanPropertyDefinitions(
            jsonCreatorAnnotatedMethod.getAnnotated.getParameters,
            jsonCreatorAnnotatedMethod,
            fromCompanion = true),
          validator.map(_.describeExecutable(jsonCreatorAnnotatedMethod.getAnnotated, mixinClazz)),
          validator.map(_.describeMethods(clazz))
        )
      case _ =>
        fromClazz match {
          case Some(jsonCreatorAnnotatedConstructor) =>
            CaseClassCreator(
              jsonCreatorAnnotatedConstructor.getAnnotated,
              getBeanPropertyDefinitions(
                jsonCreatorAnnotatedConstructor.getAnnotated.getParameters,
                jsonCreatorAnnotatedConstructor),
              validator.map(
                _.describeExecutable(jsonCreatorAnnotatedConstructor.getAnnotated, mixinClazz)),
              validator.map(_.describeMethods(clazz))
            )
          case _ =>
            // try to use what Jackson thinks is the default -- however Jackson does not
            // seem to correctly track an empty default constructor for case classes, nor
            // multiple un-annotated and we have no way to pick a proper constructor so we bail
            val constructor = Option(beanDescription.getClassInfo.getDefaultConstructor) match {
              case Some(ctor) => ctor
              case _ =>
                val constructors = beanDescription.getBeanClass.getConstructors
                assert(constructors.size == 1, "Multiple case class constructors not supported")
                annotateConstructor(constructors.head, clazzAnnotations)
            }
            CaseClassCreator(
              constructor.getAnnotated,
              getBeanPropertyDefinitions(constructor.getAnnotated.getParameters, constructor),
              validator.map(_.describeExecutable(constructor.getAnnotated, mixinClazz)),
              validator.map(_.describeMethods(clazz))
            )
        }
    }
  }
  // nested class inside another class is not supported, e.g., we do not support
  // use of creators for non-static inner classes,
  assert(!beanDescription.isNonStaticInnerClass, "Non-static inner case classes are not supported.")

  // all class annotations -- including inherited annotations for fields
  private[this] val allAnnotations: scala.collection.Map[String, Array[Annotation]] = {
    // use the carried scalaType in order to find the appropriate constructor by the
    // unresolved scalaTypes -- e.g., before we resolve generic types to their bound type
    Annotations.findAnnotations(
      clazz,
      caseClazzCreator.propertyDefinitions
        .map(_.scalaType.erasure)
    )
  }

  // support for reading annotations from Jackson Mix-ins
  // see: https://github.com/FasterXML/jackson-docs/wiki/JacksonMixInAnnotations
  private[this] val allMixinAnnotations: scala.collection.Map[String, Array[Annotation]] =
    mixinClazz match {
      case Some(clazz) =>
        clazz.getDeclaredMethods.map(m => m.getName -> m.getAnnotations).toMap
      case _ =>
        scala.collection.Map.empty[String, Array[Annotation]]
    }

  // optimized lookup of bean property definition annotations
  private[this] def getBeanPropertyDefinitionAnnotations(
    beanPropertyDefinition: BeanPropertyDefinition
  ): Array[Annotation] = {
    if (beanPropertyDefinition.getPrimaryMember.getAllAnnotations.size() > 0) {
      beanPropertyDefinition.getPrimaryMember.getAllAnnotations.annotations().asScala.toArray
    } else Array.empty[Annotation]
  }

  // Field name to list of parsed annotations. Jackson only tracks JacksonAnnotations
  // in the BeanPropertyDefinition AnnotatedMembers and we want to track all class annotations by field.
  // Annotations are keyed by parameter name because the logic collapses annotations from the
  // inheritance hierarchy where the discriminator is member name.
  // note: Prefer while loop over Scala for loop for better performance. The scala for loop
  // performance is optimized in 2.13.0 if we enable scalac: https://github.com/scala/bug/issues/1338
  private[this] val fieldAnnotations: scala.collection.Map[String, Array[Annotation]] = {
    val fieldBeanPropertyDefinitions: Array[BeanPropertyDefinition] =
      caseClazzCreator.propertyDefinitions.map(_.beanPropertyDefinition)
    val collectedFieldAnnotations = mutable.HashMap[String, Array[Annotation]]()
    var index = 0
    while (index < fieldBeanPropertyDefinitions.length) {
      val fieldBeanPropertyDefinition = fieldBeanPropertyDefinitions(index)
      val fieldName = fieldBeanPropertyDefinition.getInternalName
      // in many cases we will have the field annotations in the `allAnnotations` Map which
      // scans for annotations from the class definition being deserialized, however in some cases
      // we are dealing with an annotated field from a static or secondary constructor
      // and thus the annotations may not exist in the `allAnnotations` Map so we default to
      // any carried bean property definition annotations which includes annotation information
      // for any static or secondary constructor.
      val annotations =
        allAnnotations.getOrElse(
          fieldName,
          getBeanPropertyDefinitionAnnotations(fieldBeanPropertyDefinition)
        ) ++ allMixinAnnotations.getOrElse(fieldName, Array.empty)
      if (annotations.nonEmpty) collectedFieldAnnotations.put(fieldName, annotations)
      index += 1
    }

    collectedFieldAnnotations
  }

  /* exposed for testing */
  private[jackson] val fields: Array[CaseClassField] =
    CaseClassField.createFields(
      clazz,
      caseClazzCreator.executable,
      clazzDescriptor,
      caseClazzCreator.propertyDefinitions,
      fieldAnnotations,
      propertyNamingStrategy
    )

  private[this] lazy val numConstructorArgs = fields.length
  private[this] lazy val isWrapperClass = classOf[WrappedValue[_]].isAssignableFrom(clazz)
  private[this] lazy val firstFieldName = fields.head.name

  /* Public */

  override def isCachable: Boolean = true

  override def deserialize(jsonParser: JsonParser, context: DeserializationContext): Object = {
    if (isWrapperClass)
      deserializeWrapperClass(jsonParser, context)
    else
      deserializeNonWrapperClass(jsonParser, context)
  }

  /* Private */

  private[this] def deserializeWrapperClass(
    jsonParser: JsonParser,
    context: DeserializationContext
  ): Object = {
    if (jsonParser.getCurrentToken.isStructStart) {
      try {
        context.handleUnexpectedToken(
          clazz,
          jsonParser.currentToken(),
          jsonParser,
          "Unable to deserialize wrapped value from a json object"
        )
      } catch {
        case NonFatal(e) =>
          // wrap in a JsonMappingException
          throw JsonMappingException.from(jsonParser, e.getMessage)
      }
    }

    val jsonNode = context.getNodeFactory.objectNode()
    jsonNode.put(firstFieldName, jsonParser.getText)
    deserialize(jsonParser, context, jsonNode)
  }

  private[this] def deserializeNonWrapperClass(
    jsonParser: JsonParser,
    context: DeserializationContext
  ): Object = {
    incrementParserToFirstField(jsonParser, context)
    val jsonNode = jsonParser.readValueAsTree[JsonNode]
    deserialize(jsonParser, context, jsonNode)
  }

  private[this] def deserialize(
    jsonParser: JsonParser,
    context: DeserializationContext,
    jsonNode: JsonNode
  ): Object = {
    val jsonFieldNames: Seq[String] = jsonNode.fieldNames().asScala.toSeq
    val caseClassFieldNames: Seq[String] = fields.map(_.name)

    handleUnknownFields(jsonParser, context, jsonFieldNames, caseClassFieldNames)
    val (values, parseErrors) = parseConstructorValues(jsonParser, context, jsonNode)

    // run field validations
    val validationErrors = executeFieldValidations(
      validator,
      caseClazzCreator.executableValidationDescription,
      config,
      values,
      fields)

    val errors = parseErrors ++ validationErrors
    if (errors.nonEmpty) throw CaseClassMappingException(errors.toSet)

    newInstance(values)
  }

  /** Return all "unknown" properties sent in the incoming JSON */
  private[this] def unknownProperties(
    context: DeserializationContext,
    jsonFieldNames: Seq[String],
    caseClassFieldNames: Seq[String]
  ): Seq[String] = {
    // if there is a JsonIgnoreProperties annotation on the class, it should prevail
    val nonIgnoredFields: Seq[String] =
      Annotations.findAnnotation[JsonIgnoreProperties](clazzAnnotations) match {
        case Some(annotation) if !annotation.ignoreUnknown() =>
          // has a JsonIgnoreProperties annotation and is configured to NOT ignore unknown properties
          val annotationIgnoredFields: Seq[String] = annotation.value()
          // only non-ignored json fields should be considered
          jsonFieldNames.diff(annotationIgnoredFields)
        case None if context.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) =>
          // no annotation but feature is configured, thus all json fields should be considered
          jsonFieldNames
        case _ =>
          Seq.empty[String] // every field is ignorable
      }

    // if we have any non ignored field, return the difference
    if (nonIgnoredFields.nonEmpty) {
      nonIgnoredFields.diff(caseClassFieldNames)
    } else Seq.empty[String]
  }

  /** Throws a [[CaseClassMappingException]] with a set of [[CaseClassFieldMappingException]] per unknown field */
  private[this] def handleUnknownFields(
    jsonParser: JsonParser,
    context: DeserializationContext,
    jsonFieldNames: Seq[String],
    caseClassFieldNames: Seq[String]
  ): Unit = {
    // handles more or less incoming fields in the JSON than are defined in the case class
    val unknownFields: Seq[String] = unknownProperties(context, jsonFieldNames, caseClassFieldNames)
    if (unknownFields.nonEmpty) {
      val errors = unknownFields.map { field =>
        CaseClassFieldMappingException(
          CaseClassFieldMappingException.PropertyPath.Empty,
          CaseClassFieldMappingException.Reason(
            message = UnrecognizedPropertyException
              .from(
                jsonParser,
                clazz,
                field,
                caseClassFieldNames.map(_.asInstanceOf[Object]).asJavaCollection
              ).getMessage
          )
        )
      }
      throw CaseClassMappingException(errors.toSet)
    }
  }

  private[this] def incrementParserToFirstField(
    jsonParser: JsonParser,
    context: DeserializationContext
  ): Unit = {
    if (jsonParser.getCurrentToken == JsonToken.START_OBJECT) {
      jsonParser.nextToken()
    }
    if (jsonParser.getCurrentToken != JsonToken.FIELD_NAME &&
      jsonParser.getCurrentToken != JsonToken.END_OBJECT) {
      try {
        context.handleUnexpectedToken(clazz, jsonParser)
      } catch {
        case NonFatal(e) =>
          e match {
            case j: JsonProcessingException =>
              // don't include source info since it's often blank.
              j.clearLocation()
              throw new JsonParseException(jsonParser, j.getMessage)
            case _ =>
              throw new JsonParseException(jsonParser, e.getMessage)
          }
      }
    }
  }

  // note: Prefer while loop over Scala for loop for better performance. The scala for loop
  // performance is optimized in 2.13.0 if we enable scalac: https://github.com/scala/bug/issues/1338
  private[this] def parseConstructorValues(
    jsonParser: JsonParser,
    context: DeserializationContext,
    jsonNode: JsonNode
  ): (Array[Object], Seq[CaseClassFieldMappingException]) = {
    /* Mutable Fields */
    var constructorValuesIdx = 0
    val constructorValues = new Array[Object](numConstructorArgs)
    val errors = mutable.ListBuffer[CaseClassFieldMappingException]()

    while (constructorValuesIdx < numConstructorArgs) {
      val field = fields(constructorValuesIdx)
      try {
        val value = field.parse(context, jsonParser.getCodec, jsonNode)
        constructorValues(constructorValuesIdx) = value //mutation
      } catch {
        case e: CaseClassFieldMappingException =>
          if (e.path == null) {
            // fill in missing path detail
            addException(
              field,
              e.withPropertyPath(CaseClassFieldMappingException.PropertyPath.leaf(field.name)),
              constructorValues,
              constructorValuesIdx,
              errors
            )
          } else {
            addException(
              field,
              e,
              constructorValues,
              constructorValuesIdx,
              errors
            )
          }
        case e: InvalidFormatException =>
          addException(
            field,
            CaseClassFieldMappingException(
              CaseClassFieldMappingException.PropertyPath.leaf(field.name),
              CaseClassFieldMappingException.Reason(
                s"'${e.getValue.toString}' is not a " +
                  s"valid ${Types.wrapperType(e.getTargetType).getSimpleName}${validValuesString(e)}",
                CaseClassFieldMappingException.JsonProcessingError(e)
              )
            ),
            constructorValues,
            constructorValuesIdx,
            errors
          )
        case e: MismatchedInputException =>
          addException(
            field,
            CaseClassFieldMappingException(
              CaseClassFieldMappingException.PropertyPath.leaf(field.name),
              CaseClassFieldMappingException.Reason(
                s"'${jsonNode.asText("")}' is not a " +
                  s"valid ${Types.wrapperType(e.getTargetType).getSimpleName}${validValuesString(e)}",
                CaseClassFieldMappingException.JsonProcessingError(e)
              )
            ),
            constructorValues,
            constructorValuesIdx,
            errors
          )
        case e: CaseClassMappingException =>
          constructorValues(constructorValuesIdx) = field.missingValue //mutation
          errors ++= e.errors.map(_.scoped(field.name))
        case e: JsonProcessingException =>
          // don't include source info since it's often blank. Consider adding e.getCause.getMessage
          e.clearLocation()
          addException(
            field,
            CaseClassFieldMappingException(
              CaseClassFieldMappingException.PropertyPath.leaf(field.name),
              CaseClassFieldMappingException.Reason(
                e.errorMessage,
                CaseClassFieldMappingException.JsonProcessingError(e)
              )
            ),
            constructorValues,
            constructorValuesIdx,
            errors
          )
        case e: java.util.NoSuchElementException if isScalaEnumerationType(field) =>
          // Scala enumeration mapping issue
          addException(
            field,
            CaseClassFieldMappingException(
              CaseClassFieldMappingException.PropertyPath.leaf(field.name),
              CaseClassFieldMappingException.Reason(e.getMessage)
            ),
            constructorValues,
            constructorValuesIdx,
            errors
          )
        case e: InjectableValuesException =>
          // we rethrow, to prevent leaking internal injection detail in the "errors" array
          throw e
        case NonFatal(e) =>
          error("Unexpected exception parsing field: " + field, e)
          throw e
      }
      constructorValuesIdx += 1
    }

    (constructorValues, errors.toSeq)
  }

  private[this] def isScalaEnumerationType(field: CaseClassField): Boolean = {
    // scala.Enumerations are challenging for class type comparison and thus we simply check
    // if the class name "starts with" scala.Enumeration which should indicate if a class
    // is a scala.Enumeration type. The added benefit is that this should work even if the
    // classes come from different classloaders.
    field.javaType.getRawClass.getName.startsWith(classOf[scala.Enumeration].getName) ||
    (field.isOption && field.javaType
      .containedType(0).getRawClass.getName.startsWith(classOf[scala.Enumeration].getName))
  }

  /** Add the given exception to the given array buffer of errors while also adding a missing value field to the given array */
  private[this] def addException(
    field: CaseClassField,
    e: CaseClassFieldMappingException,
    array: Array[Object],
    idx: Int,
    errors: mutable.ListBuffer[CaseClassFieldMappingException]
  ): Unit = {
    array(idx) = field.missingValue //mutation
    errors += e //mutation
  }

  private[this] def validValuesString(e: MismatchedInputException): String = {
    if (e.getTargetType != null && e.getTargetType.isEnum)
      " with valid values: " + e.getTargetType.getEnumConstants.mkString(", ")
    else
      ""
  }

  private[this] def newInstance(constructorValues: Array[Object]): Object = {
    val obj =
      try {
        instantiate(constructorValues)
      } catch {
        case e @ (_: InvocationTargetException | _: ExceptionInInitializerError) =>
          if (e.getCause == null)
            throw e // propagate the underlying cause of the failed instantiation if available
          else throw e.getCause
      }
    val methodValidationErrors =
      executeMethodValidations(
        validator,
        caseClazzCreator.executableValidationMethodDescriptions,
        config,
        obj
      )
    if (methodValidationErrors.nonEmpty)
      throw CaseClassMappingException(methodValidationErrors.toSet)

    obj
  }

  private[this] def instantiate(
    constructorValues: Array[Object]
  ): Object = {
    caseClazzCreator.executable match {
      case method: Method =>
        // if the creator is of type Method, we assume the need to invoke the companion object
        method.invoke(clazzDescriptor.companion.get.instance, constructorValues: _*)
      case constructor: Constructor[_] =>
        // otherwise simply invoke the constructor
        constructor.newInstance(constructorValues: _*).asInstanceOf[Object]
    }
  }

  private[this] def propertyNamingStrategy: PropertyNamingStrategy = {
    Annotations.findAnnotation[JsonNaming](clazzAnnotations) match {
      case Some(jsonNaming) =>
        jsonNaming.value().getDeclaredConstructor().newInstance()
      case _ =>
        config.getPropertyNamingStrategy
    }
  }

  private[this] def annotateConstructor(
    constructor: Constructor[_],
    annotations: Seq[Annotation]
  ): AnnotatedConstructor = {
    val paramAnnotationMaps: Array[AnnotationMap] = {
      constructor.getParameterAnnotations.map { parameterAnnotations =>
        val parameterAnnotationMap = new AnnotationMap()
        parameterAnnotations.map(parameterAnnotationMap.add)
        parameterAnnotationMap
      }
    }

    val annotationMap = new AnnotationMap()
    annotations.map(annotationMap.add)
    new AnnotatedConstructor(
      new TypeResolutionContext.Basic(config.getTypeFactory, javaType.getBindings),
      constructor,
      annotationMap,
      paramAnnotationMaps
    )
  }

  /* in order to deal with parameterized types we create a JavaType here and carry it */
  private[this] def getBeanPropertyDefinitions(
    parameters: Array[Parameter],
    annotatedWithParams: AnnotatedWithParams,
    fromCompanion: Boolean = false
  ): Array[PropertyDefinition] = {
    // need to find the scala description which carries the full type information
    val constructorParamDescriptors =
      findConstructorDescriptor(parameters) match {
        case Some(constructorDescriptor) =>
          constructorDescriptor.params
        case _ =>
          throw InvalidDefinitionException.from(
            CaseClassDeserializer.EmptyJsonParser,
            s"Unable to locate suitable constructor for class: ${clazz.getName}",
            javaType)
      }

    for ((parameter, index) <- parameters.zipWithIndex) yield {
      val constructorParamDescriptor = constructorParamDescriptors(index)
      val scalaType = constructorParamDescriptor.argType

      val parameterJavaType =
        if (!javaType.getBindings.isEmpty &&
          shouldFullyDefineParameterizedType(scalaType, parameter)) {
          // what types are bound to the generic case class parameters
          val boundTypeParameters: Array[JavaType] =
            ReflectionTypes
              .parameterizedTypeNames(parameter.getParameterizedType)
              .map(javaType.getBindings.findBoundType)
          Types
            .javaType(
              config.getTypeFactory,
              scalaType,
              boundTypeParameters
            )
        } else {
          Types.javaType(config.getTypeFactory, scalaType)
        }

      val annotatedParameter =
        newAnnotatedParameter(
          typeResolutionContext = new TypeResolutionContext.Basic(
            config.getTypeFactory,
            javaType.getBindings
          ), // use the TypeBindings from the top-level JavaType, not the parameter JavaType
          owner = annotatedWithParams,
          annotations = annotatedWithParams.getParameterAnnotations(index),
          javaType = parameterJavaType,
          index = index
        )

      PropertyDefinition(
        parameterJavaType,
        scalaType,
        SimpleBeanPropertyDefinition
          .construct(
            config,
            annotatedParameter,
            new PropertyName(constructorParamDescriptor.name)
          )
      )
    }
  }

  // note: Prefer while loop over Scala for loop for better performance. The scala for loop
  // performance is optimized in 2.13.0 if we enable scalac: https://github.com/scala/bug/issues/1338
  private[this] def findConstructorDescriptor(
    parameters: Array[Parameter]
  ): Option[ConstructorDescriptor] = {
    val constructors = clazzDescriptor.constructors
    var index = 0
    while (index < constructors.length) {
      val constructorDescriptor = constructors(index)
      val params = constructorDescriptor.params
      if (params.length == parameters.length) {
        // description has the same number of parameters we're looking for, check each type in order
        val checkedParams = params.map { param =>
          Types
            .wrapperType(param.argType.erasure)
            .isAssignableFrom(Types.wrapperType(parameters(param.argIndex).getType))
        }
        if (checkedParams.forall(_ == true)) return Some(constructorDescriptor)
      }
      index += 1
    }
    None
  }

  // if we need to attempt to fully specify the JavaType because it is generically types
  private[this] def shouldFullyDefineParameterizedType(
    scalaType: ScalaType,
    parameter: Parameter
  ): Boolean = {
    // only need to fully specify if the type is parameterized and it has more than one type arg
    // or its typeArg is also parameterized.
    def isParameterized(reflectionType: Type): Boolean = reflectionType match {
      case _: ParameterizedType | _: TypeVariable[_] =>
        true
      case _ =>
        false
    }

    val parameterizedType = parameter.getParameterizedType
    !scalaType.isPrimitive &&
    (scalaType.typeArgs.isEmpty ||
    scalaType.typeArgs.head.erasure.isAssignableFrom(classOf[Object])) &&
    parameterizedType != parameter.getType &&
    isParameterized(parameterizedType)
  }
}
