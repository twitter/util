package com.twitter.util.validation

import com.github.benmanes.caffeine.cache.{Cache, Caffeine}
import com.twitter.util.reflect.{Annotations, Types => ReflectTypes}
import com.twitter.util.validation.engine.MethodValidationResult
import com.twitter.util.validation.internal.Types
import com.twitter.util.validation.metadata.{
  CaseClassDescriptor,
  ConstructorDescriptor,
  ExecutableDescriptor,
  MethodDescriptor,
  PropertyDescriptor
}
import com.twitter.util.{Return, Try}
import jakarta.validation.{Constraint, ConstraintDeclarationException, Valid, ValidationException}
import java.lang.annotation.Annotation
import java.lang.reflect.{Constructor, Executable, Method, Parameter}
import org.hibernate.validator.internal.metadata.core.ConstraintHelper
import org.json4s.reflect.{ClassDescriptor, ConstructorParamDescriptor, Reflector, ScalaType}
import org.json4s.{reflect => json4s}
import scala.collection.mutable
import scala.util.control.NonFatal

private[validation] object DescriptorFactory {

  def unmangleName(name: String): String = scala.reflect.NameTransformer.decode(name)
  def mangleName(name: String): String = scala.reflect.NameTransformer.encode(name)

  /** If the given class is marked for cascaded validation or not. True if the class is a case class and has the `@Valid` annotation */
  private def isCascadedValidation(erasure: Class[_], annotations: Array[Annotation]): Boolean =
    Annotations.findAnnotation[Valid](annotations).isDefined && ReflectTypes.isCaseClass(erasure)

  private case class ConstructorParams(
    param: ConstructorParamDescriptor,
    annotations: Array[Annotation])

  private def getCascadedScalaType(
    scalaType: ScalaType,
    annotations: Array[Annotation]
  ): Option[ScalaType] =
    Types.getContainedScalaType(scalaType) match {
      case Some(containedScalaType)
          if isCascadedValidation(containedScalaType.erasure, annotations) =>
        Some(containedScalaType)
      case _ => None
    }
}

/**
 * Used to describe a given Class[T] as a CaseClassDescriptor[T]
 */
private[validation] class DescriptorFactory(
  descriptorCacheSize: Long,
  constraintHelper: ConstraintHelper) {

  import DescriptorFactory._

  // A caffeine cache to store the expensive reflection calls on the same object. Caffeine cache
  // uses the `Window TinyLfu` policy to remove evicted keys.
  // For more information, check out: https://github.com/ben-manes/caffeine/wiki/Efficiency
  private[validation] val caseClassDescriptors: Cache[Class[_], CaseClassDescriptor[_]] =
    Caffeine
      .newBuilder()
      .maximumSize(descriptorCacheSize)
      .build[Class[_], CaseClassDescriptor[_]]()

  /** Close and attempt to clean up resources */
  def close(): Unit = {
    caseClassDescriptors.invalidateAll()
    caseClassDescriptors.cleanUp()
  }

  /**
   * Describe a [[Class]].
   *
   * @note the returned [[CaseClassDescriptor]] is cached for repeated lookup attempts keyed by
   *       the given Class[T] type.
   */
  def describe[T](
    clazz: Class[T]
  ): CaseClassDescriptor[T] = {
    caseClassDescriptors
      .get(
        clazz,
        (key: Class[_]) => buildDescriptor[T](clazz)
      ).asInstanceOf[CaseClassDescriptor[T]]
  }

  /**
   * Describe a [[Constructor]].
   *
   * @note the returned [[ConstructorDescriptor]] is NOT cached. It is up to the caller of this
   *       method to optimize any calls to this method.
   */
  def describe[T](
    constructor: Constructor[T]
  ): ConstructorDescriptor = {
    buildConstructorDescriptor(
      constructor.getDeclaringClass,
      getJson4sConstructorDescriptor(constructor),
      None
    )
  }

  /**
   * Describe a `@MethodValidation`-annotated or otherwise constrained [[Method]].
   *
   * As we do not want to describe every possible case class method, this potentially
   * returns a None in the case where the method has no constraint annotation and no
   * constrained parameters.
   *
   * @note the returned [[MethodDescriptor]] is NOT cached. It is up to the caller of this
   *       method to optimize any calls to this method.
   */
  def describe(method: Method): Option[MethodDescriptor] = buildMethodDescriptor(method)

  /**
   * Describe an [[Executable]] given an optional "mix-in" Class.
   *
   * @note the returned [[ExecutableDescriptor]] is NOT cached. It is up to the caller of this
   *       method to optimize any calls to this method.
   */
  def describeExecutable[T](
    executable: Executable,
    mixinClazz: Option[Class[_]]
  ): ExecutableDescriptor = {
    executable match {
      case constructor: Constructor[_] =>
        val desc = getJson4sConstructorDescriptor(constructor)
        val clazz = constructor.getDeclaringClass.asInstanceOf[Class[T]]
        val parameterAnnotations = mixinClazz match {
          case Some(mixin) =>
            val constructorAnnotationMap = getConstructorParams(clazz, desc)
            // augment with mixin class field annotations
            constructorAnnotationMap.map {
              case (name, params) =>
                try {
                  val method = mixin.getDeclaredMethod(name)
                  (
                    name,
                    params.copy(annotations =
                      params.annotations ++
                        method.getAnnotations.filter(isConstraintAnnotation))
                  )
                } catch {
                  case NonFatal(_) => // do nothing
                    (name, params)
                }
            }
          case _ =>
            getConstructorParams(clazz, desc)
        }
        buildConstructorDescriptor(
          constructor.getDeclaringClass,
          desc,
          Some(parameterAnnotations)
        )
      case method: Method =>
        MethodDescriptor(
          method = method,
          annotations = method.getDeclaredAnnotations.filter(isConstraintAnnotation),
          members = method.getParameters.map { parameter =>
            val name = parameter.getName
            // augment with mixin class field annotations
            val parameterAnnotations =
              mixinClazz match {
                case Some(mixin) =>
                  try {
                    val method = mixin.getDeclaredMethod(name)
                    parameter.getAnnotations ++
                      method.getAnnotations.filter(isConstraintAnnotation)
                  } catch {
                    case NonFatal(_) => // do nothing
                      parameter.getAnnotations
                  }
                case _ => // do nothing
                  parameter.getAnnotations
              }
            parameter.getName -> buildPropertyDescriptor(
              Reflector.scalaTypeOf(parameter.getParameterizedType),
              parameterAnnotations
            )
          }.toMap
        )
      case _ => // should not get here
        throw new IllegalArgumentException
    }
  }

  /**
   * Describe `@MethodValidation`-annotated or otherwise constrained methods of a given `Class[_]`.
   *
   * @note the returned [[MethodDescriptor]] instances are NOT cached. It is up to the caller of this
   *       method to optimize any calls to this method.
   */
  def describeMethods(clazz: Class[_]): Array[MethodDescriptor] = {
    val clazzMethods = clazz.getMethods
    if (clazzMethods.nonEmpty) {
      val methods = new mutable.ArrayBuffer[MethodDescriptor](clazzMethods.length)
      var index = 0
      val length = clazzMethods.length
      while (index < length) {
        val method = clazzMethods(index)
        buildMethodDescriptor(method).foreach(methods.append(_))
        index += 1
      }
      methods.toArray
    } else Array.empty[MethodDescriptor]
  }

  /* Private */

  private[this] def buildDescriptor[T](clazz: Class[T]): CaseClassDescriptor[T] = {
    // for case classes, annotations for params only appear on the executable
    val clazzDescriptor: ClassDescriptor = Reflector.describe(clazz).asInstanceOf[ClassDescriptor]
    // map of parameter name to a class with all annotations for the parameter (including inherited)
    val constructorParams: scala.collection.Map[String, ConstructorParams] =
      getConstructorParams(clazz, clazzDescriptor)

    val members = new mutable.HashMap[String, PropertyDescriptor]()
    // we validate only declared properties of the class
    val properties: Seq[json4s.PropertyDescriptor] = clazzDescriptor.properties
    var index = 0
    val size = properties.size
    while (index < size) {
      val property = properties(index)
      val (fromConstructorScalaType, annotations) = {
        constructorParams.get(property.mangledName) match {
          case Some(constructorParam) =>
            // we already have information for the field as it is an annotated executable parameter
            (constructorParam.param.argType, constructorParam.annotations)
          case _ =>
            // we don't have information, use the property information
            (property.returnType, property.field.getAnnotations)
        }
      }

      // create property descriptorFactory
      members.put(
        property.mangledName,
        buildPropertyDescriptor(fromConstructorScalaType, annotations)
      )
      index += 1
    }

    val constructors = new mutable.ArrayBuffer[ConstructorDescriptor]()
    // create executable descriptors
    var jindex = 0
    val jsize = clazzDescriptor.constructors.size
    while (jindex < jsize) {
      constructors.append(
        buildConstructorDescriptor(clazz, clazzDescriptor.constructors(jindex), None)
      )
      jindex += 1
    }

    val methods = new mutable.ArrayBuffer[MethodDescriptor]()
    val clazzMethods = clazz.getMethods
    if (clazzMethods.nonEmpty) {
      var index = 0
      val length = clazzMethods.length
      while (index < length) {
        val method = clazzMethods(index)
        buildMethodDescriptor(method).foreach(methods.append(_))
        index += 1
      }
    }

    CaseClassDescriptor(
      clazz = clazz,
      scalaType = clazzDescriptor.erasure,
      annotations = clazzDescriptor.erasure.erasure.getAnnotations.filter(isConstraintAnnotation),
      constructors = constructors.toArray,
      members = members.toMap,
      methods = methods.toArray
    )
  }

  /* Exposed for testing */
  // find the executable with parameters that are all declared class fields
  private[validation] def findConstructor(
    clazzDescriptor: ClassDescriptor
  ): org.json4s.reflect.ConstructorDescriptor = {
    def isDefaultConstructorDescriptor(
      constructorDescriptor: org.json4s.reflect.ConstructorDescriptor
    ): Boolean = {
      constructorDescriptor.isPrimary || constructorDescriptor.params.forall { param =>
        Try(clazzDescriptor.erasure.erasure.getDeclaredField(param.mangledName)).isReturn
      }
    }

    // locate the executable where all params are also declared fields else error
    clazzDescriptor.constructors
      .find(isDefaultConstructorDescriptor)
      .getOrElse(throw new ValidationException(
        s"Unable to parse case class for validation: ${clazzDescriptor.erasure.fullName}"))
  }

  private[this] def getConstructorParams(
    clazz: Class[_],
    clazzDescriptor: ClassDescriptor
  ): scala.collection.Map[String, ConstructorParams] = {
    // find the default executable descriptorFactory (the underlying executable
    // may be a executable or a method)
    val constructorDescriptor: org.json4s.reflect.ConstructorDescriptor =
      findConstructor(clazzDescriptor)
    getConstructorParams(
      clazz,
      constructorDescriptor
    )
  }

  private[this] def getConstructorParams(
    clazz: Class[_],
    constructorDescriptor: org.json4s.reflect.ConstructorDescriptor
  ): scala.collection.Map[String, ConstructorParams] = {
    val constructorParameters: Array[Parameter] = {
      Option(constructorDescriptor.constructor.constructor) match {
        case Some(cons) =>
          cons.getParameters
        case _ =>
          // factory method "executable"
          constructorDescriptor.constructor.method.getParameters
      }
    }

    // find all inherited annotations for every executable param
    val allFieldAnnotations =
      findAnnotations(
        clazz,
        clazz.getDeclaredFields.map(_.getName).toSet,
        constructorDescriptor.params.map { param =>
          // annotations can be on the executable param OR they were
          // copied to the generated field with a meta annotation. we
          // rely on the fact that a given annotation should not show up in
          // both arrays the way the Scala compiler currently works, however
          // the executable we're iterating through may not represent
          // actual declared fields in the class, so we have to apply logic
          param.mangledName -> getAnnotations(
            param.mangledName,
            constructorParameters(param.argIndex),
            clazz)
        }.toMap
      )

    val result: mutable.HashMap[String, ConstructorParams] =
      new mutable.HashMap[String, ConstructorParams]()
    var index = 0
    val length = constructorParameters.length
    while (index < length) {
      val descriptor = constructorDescriptor.params(index)
      val filteredAnnotations = allFieldAnnotations(descriptor.mangledName).filter { ann =>
        Annotations.isAnnotationPresent[Constraint](ann) ||
        Annotations.equals[Valid](ann)
      }

      result.put(
        descriptor.mangledName,
        ConstructorParams(
          descriptor,
          filteredAnnotations
        )
      )
      index += 1
    }
    result
  }

  private[this] def getAnnotations(
    name: String,
    parameter: Parameter,
    clazz: Class[_]
  ): Array[Annotation] = {
    val fromClazzAnnotations: Array[Annotation] =
      Try(clazz.getDeclaredField(name)) match {
        case Return(declaredField) =>
          declaredField.getAnnotations
        case _ =>
          Array.empty[Annotation]
      }
    parameter.getAnnotations ++ fromClazzAnnotations
  }

  private[this] def findAnnotations(
    clazz: Class[_],
    declaredFields: Set[String],
    fieldAnnotations: scala.collection.Map[String, Array[Annotation]]
  ): scala.collection.Map[String, Array[Annotation]] = {
    val collectorMap = new scala.collection.mutable.HashMap[String, Array[Annotation]]()
    collectorMap ++= fieldAnnotations
    // find inherited annotations
    Annotations.findDeclaredAnnotations(
      clazz,
      declaredFields,
      collectorMap
    )
  }

  private[this] def getJson4sConstructorDescriptor(
    constructor: Constructor[_]
  ): org.json4s.reflect.ConstructorDescriptor = {
    val parameters: Array[Parameter] = constructor.getParameters
    org.json4s.reflect.ConstructorDescriptor(
      params = Seq.tabulate(parameters.length) { index =>
        val parameter = parameters(index)
        org.json4s.reflect.ConstructorParamDescriptor(
          name = unmangleName(parameter.getName),
          mangledName = parameter.getName,
          argIndex = index,
          argType = Reflector.scalaTypeOf(parameter.getParameterizedType),
          defaultValue = None
        )
      },
      constructor = new org.json4s.reflect.Executable(constructor, true),
      isPrimary = true
    )
  }

  private[this] def buildConstructorDescriptor[T](
    clazz: Class[T],
    constructorDescriptor: org.json4s.reflect.ConstructorDescriptor,
    parameterAnnotationsMap: Option[scala.collection.Map[String, ConstructorParams]]
  ): ConstructorDescriptor = {
    val parameterAnnotations = parameterAnnotationsMap match {
      case Some(m) => m
      case _ => getConstructorParams(clazz, constructorDescriptor)
    }
    val (executable, annotations) = Option(constructorDescriptor.constructor.constructor) match {
      case Some(constructor) =>
        (constructor, constructor.getAnnotations)
      case _ =>
        (
          constructorDescriptor.constructor.method,
          constructorDescriptor.constructor.method.getAnnotations)
    }

    ConstructorDescriptor(
      executable,
      annotations = annotations.filter(isConstraintAnnotation),
      members = constructorDescriptor.params.map { parameter =>
        parameter.mangledName -> buildPropertyDescriptor(
          parameter.argType,
          parameterAnnotations(parameter.mangledName).annotations
        )
      }.toMap
    )
  }

  /** Create a PropertyDescriptor from the given ScalaType and annotations */
  private[this] def buildPropertyDescriptor[T](
    scalaType: ScalaType,
    annotations: Array[Annotation]
  ): PropertyDescriptor = {
    val cascadedScalaType = getCascadedScalaType(scalaType, annotations)
    val isCascaded = // annotated with @Valid and is a case class type
      Annotations.findAnnotation(classOf[Valid], annotations).isDefined &&
        cascadedScalaType.exists(p => ReflectTypes.isCaseClass(p.erasure))

    PropertyDescriptor(
      scalaType = scalaType,
      cascadedScalaType = cascadedScalaType,
      annotations = annotations.filter(isConstraintAnnotation),
      isCascaded = isCascaded
    )
  }

  /** Create a MethodDescriptor for a given clazz Method */
  private[this] def buildMethodDescriptor(method: Method): Option[MethodDescriptor] = {
    if (isMethodValidation(method) && checkMethodValidationMethod(method)) {
      Some(
        MethodDescriptor(
          method = method,
          annotations = method.getDeclaredAnnotations.filter(isConstraintAnnotation),
          members = Map.empty
        )
      )
    } else if (isConstrainedMethod(method) || hasConstrainedParameters(method)) {
      Some(
        MethodDescriptor(
          method = method,
          annotations = method.getDeclaredAnnotations.filter(isConstraintAnnotation),
          members = method.getParameters.map { parameter =>
            parameter.getName -> buildPropertyDescriptor(
              Reflector.scalaTypeOf(parameter.getParameterizedType),
              parameter.getAnnotations
            )
          }.toMap
        )
      )
    } else None
  }

  private[this] def checkMethodValidationMethod(method: Method): Boolean = {
    if (method.getParameterCount != 0)
      throw new ConstraintDeclarationException(
        s"Methods annotated with @${classOf[MethodValidation].getSimpleName} must not declare any arguments")
    if (method.getReturnType != classOf[MethodValidationResult])
      throw new ConstraintDeclarationException(s"Methods annotated with @${classOf[
        MethodValidation].getSimpleName} must return a ${classOf[MethodValidationResult].getName}")
    true
  }

  private[this] def isConstraintAnnotation(annotation: Annotation): Boolean =
    constraintHelper.isConstraintAnnotation(annotation.annotationType())

  // Array of annotations contains a constraint annotation
  private[this] def isConstrainedMethod(method: Method): Boolean =
    method.getDeclaredAnnotations.exists(isConstraintAnnotation)

  private[this] def hasConstrainedParameters(method: Method): Boolean = {
    method.getParameters.exists(_.getAnnotations.exists(isConstraintAnnotation))
  }

  // Array of annotation contains @MethodValidation
  private[this] def isMethodValidation(method: Method): Boolean =
    Annotations.findAnnotation[MethodValidation](method.getDeclaredAnnotations).isDefined
}
