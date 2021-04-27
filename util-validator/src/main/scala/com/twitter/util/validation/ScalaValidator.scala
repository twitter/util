package com.twitter.util.validation

import com.twitter.util.logging.Logging
import com.twitter.util.reflect.{Classes, Annotations => ReflectAnnotations, Types => ReflectTypes}
import com.twitter.util.validation.cfg.ConstraintMapping
import com.twitter.util.validation.engine.MethodValidationResult
import com.twitter.util.validation.executable.ScalaExecutableValidator
import com.twitter.util.validation.internal.constraintvalidation.ConstraintValidatorContextFactory
import com.twitter.util.validation.internal.engine.ConstraintViolationFactory
import com.twitter.util.validation.internal.metadata.descriptor.{
  ConstraintDescriptorFactory,
  MethodValidationConstraintDescriptor
}
import com.twitter.util.validation.internal.{AnnotationFactory, Types, ValidationContext}
import com.twitter.util.validation.metadata._
import jakarta.validation._
import jakarta.validation.constraintvalidation.{SupportedValidationTarget, ValidationTarget}
import jakarta.validation.metadata.ConstraintDescriptor
import jakarta.validation.spi.ValidationProvider
import java.lang.annotation.Annotation
import java.lang.reflect.{Constructor, Executable, InvocationTargetException, Method, Parameter}
import java.util.Collections
import org.hibernate.validator.{HibernateValidator, HibernateValidatorConfiguration}
import org.hibernate.validator.internal.engine.constraintvalidation.ConstraintValidatorManager
import org.hibernate.validator.internal.engine.path.PathImpl
import org.hibernate.validator.internal.engine.{ValidatorFactoryImpl, ValidatorFactoryInspector}
import org.hibernate.validator.internal.metadata.aggregated.{
  CascadingMetaDataBuilder,
  ExecutableMetaData
}
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement.ConstrainedElementKind
import org.hibernate.validator.internal.metadata.raw.{
  ConfigurationSource,
  ConstrainedExecutable,
  ConstrainedParameter
}
import org.hibernate.validator.internal.properties.javabean.{
  ConstructorCallable,
  ExecutableCallable,
  MethodCallable
}
import org.json4s.reflect.{Reflector, ScalaType}
import scala.annotation.{tailrec, varargs}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object ScalaValidator {

  /** The size of the caffeine cache that is used to store reflection data on a validated case class. */
  private val DefaultDescriptorCacheSize: Long = 128

  case class Builder private[validation] (
    descriptorCacheSize: Long = DefaultDescriptorCacheSize,
    messageInterpolator: Option[MessageInterpolator] = None,
    constraintMappings: Set[ConstraintMapping] = Set.empty) {

    /**
     * Define the size of the cache that stores annotation data for validated case classes.
     */
    def withDescriptorCacheSize(size: Long): ScalaValidator.Builder =
      Builder(
        descriptorCacheSize = size,
        messageInterpolator = this.messageInterpolator,
        constraintMappings = this.constraintMappings
      )

    def withMessageInterpolator(
      messageInterpolator: MessageInterpolator
    ): ScalaValidator.Builder =
      Builder(
        descriptorCacheSize = this.descriptorCacheSize,
        messageInterpolator = Some(messageInterpolator),
        constraintMappings = this.constraintMappings
      )

    /**
     * Register a Set of [[ConstraintMapping]].
     *
     * @note Java users please see the version which takes a [[java.util.Set]]
     */
    def withConstraintMappings(
      constraintMappings: Set[ConstraintMapping]
    ): ScalaValidator.Builder =
      Builder(
        descriptorCacheSize = this.descriptorCacheSize,
        messageInterpolator = this.messageInterpolator,
        constraintMappings = constraintMappings
      )

    /**
     * Register a Set of [[ConstraintMapping]].
     *
     * @note Scala users please see the version which takes a [[Set]]
     */
    def withConstraintMappings(
      constraintMappings: java.util.Set[ConstraintMapping]
    ): ScalaValidator.Builder =
      Builder(
        descriptorCacheSize = this.descriptorCacheSize,
        messageInterpolator = this.messageInterpolator,
        constraintMappings = constraintMappings.asScala.toSet
      )

    /**
     * Register a [[ConstraintMapping]].
     */
    def withConstraintMapping(
      constraintMapping: ConstraintMapping
    ): ScalaValidator.Builder =
      Builder(
        descriptorCacheSize = this.descriptorCacheSize,
        messageInterpolator = this.messageInterpolator,
        constraintMappings = Set(constraintMapping)
      )

    def validator: ScalaValidator = {
      val configuration: HibernateValidatorConfiguration =
        Validation
          .byProvider[
            HibernateValidatorConfiguration,
            ValidationProvider[HibernateValidatorConfiguration]](
            classOf[HibernateValidator]
              .asInstanceOf[Class[ValidationProvider[HibernateValidatorConfiguration]]])
          .configure()

      // add user-configured message interpolator
      messageInterpolator.map { interpolator =>
        configuration.messageInterpolator(interpolator)
      }

      // add user-configured constraint mappings
      constraintMappings.foreach { constraintMapping =>
        val hibernateConstraintMapping: org.hibernate.validator.cfg.ConstraintMapping =
          configuration.createConstraintMapping()
        hibernateConstraintMapping
          .constraintDefinition(constraintMapping.annotationType.asInstanceOf[Class[Annotation]])
          .includeExistingValidators(constraintMapping.includeExistingValidators)
          .validatedBy(constraintMapping.constraintValidator
            .asInstanceOf[Class[_ <: ConstraintValidator[Annotation, _]]])
        configuration.addMapping(hibernateConstraintMapping)
      }

      val validatorFactory =
        configuration.buildValidatorFactory

      new ScalaValidator(
        this.descriptorCacheSize,
        new ValidatorFactoryInspector(validatorFactory.asInstanceOf[ValidatorFactoryImpl]),
        validatorFactory.getValidator
      )
    }
  }

  def builder: ScalaValidator.Builder = Builder()

  def apply(): ScalaValidator = ScalaValidator.builder.validator
}

class ScalaValidator private[validation] (
  cacheSize: Long,
  validatorFactory: ValidatorFactoryInspector,
  val underlying: Validator)
    extends ScalaExecutableValidator
    with Logging {

  /* Exposed for testing */
  private[validation] val descriptorFactory: DescriptorFactory =
    new DescriptorFactory(cacheSize, validatorFactory.constraintHelper)

  private[this] val constraintViolationFactory: ConstraintViolationFactory =
    new ConstraintViolationFactory(validatorFactory)

  private[this] val constraintDescriptorFactory: ConstraintDescriptorFactory =
    new ConstraintDescriptorFactory(validatorFactory)

  private[this] val constraintValidatorContextFactory: ConstraintValidatorContextFactory =
    new ConstraintValidatorContextFactory(validatorFactory)

  private[this] val constraintValidatorManager: ConstraintValidatorManager =
    validatorFactory.getConstraintCreationContext.getConstraintValidatorManager

  /** Close resources.*/
  def close(): Unit = {
    descriptorFactory.close()
    validatorFactory.close()
  }

  /**
   * Returns the descriptorFactory object describing case class constraints. Descriptors describe constraint
   * constraints on a given class and any isCascaded types.
   *
   * The returned object (and associated objects including CaseClassDescriptors) are immutable.
   *
   * @param clazz class or interface type evaluated
   * @tparam T the clazz type
   *
   * @return the [[CaseClassDescriptor]] for the specified class
   *
   * @see [[CaseClassDescriptor]]
   */
  def getConstraintsForClass[T](clazz: Class[T]): CaseClassDescriptor[T] =
    descriptorFactory.describe[T](clazz)

  /**
   * Returns the set of Constraints which validate the given [[Annotation]] class.
   *
   * For instance if the constraint type is `Class[com.twitter.util.validation.constraints.CountryCode]`,
   * the returned set would contain an instance of the `ISO3166CountryCodeConstraintValidator`.
   *
   * @param annotationType the Class[_] of the [[Annotation]] for which to find all supporting constraint validators.
   *
   * @return the set of supporting constraint validators for a given [[Annotation]].
   */
  def findConstraintValidators[A <: Annotation](
    annotationType: Class[A]
  ): Set[ConstraintValidatorType] = {
    // compute from constraint, otherwise compute from registry
    val validatedBy = findValidatedBy(annotationType)
    if (validatedBy.isEmpty) {
      validatorFactory.constraintHelper
        .getAllValidatorDescriptors(annotationType).asScala
        .map(_.newInstance(validatorFactory.constraintValidatorFactory)).toSet
    } else validatedBy
  }

  /**
   * Checks whether the specified [[Annotation]] is a valid constraint constraint. A constraint
   * constraint has to fulfill the following conditions:
   *
   *   - Must be annotated with [[jakarta.validation.Constraint]]
   *   - Define a `message` parameter
   *   - Define a `groups` parameter
   *   - Define a `payload` parameter
   *
   * @param annotation The [[Annotation]] to test.
   *
   * @return true if the constraint fulfills the above conditions, false otherwise.
   */
  def isConstraintAnnotation(annotation: Annotation): Boolean =
    isConstraintAnnotation(annotation.annotationType())

  /**
   * Checks whether the specified [[Annotation]] clazz is a valid constraint constraint. A constraint
   * constraint has to fulfill the following conditions:
   *
   *   - Must be annotated with [[jakarta.validation.Constraint]]
   *   - Define a `message` parameter
   *   - Define a `groups` parameter
   *   - Define a `payload` parameter
   *
   * @param annotationType The [[Annotation]] class to test.
   *
   * @return true if the constraint fulfills the above conditions, false otherwise.
   */
  def isConstraintAnnotation(annotationType: Class[_ <: Annotation]): Boolean =
    validatorFactory.constraintHelper.isConstraintAnnotation(annotationType)

  /**
   * Validates all constraint constraints on an object.
   *
   * @param obj the object to validate.
   *
   * @throws ValidationException      - if any constraints produce a violation.
   * @throws IllegalArgumentException - if object is null.
   */
  @throws[ValidationException]
  def verify(obj: Any, groups: Class[_]*): Unit = {
    val invalidResult = validate(obj, groups: _*)
    if (invalidResult.nonEmpty)
      throw constraintViolationFactory.newConstraintViolationException(invalidResult)
  }

  /**
   * Validates all constraint constraints on an object.
   *
   * @param obj    the object to validate.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type of the object to validate.
   *
   * @return constraint violations or an empty set if none
   *
   * @throws IllegalArgumentException - if object is null.
   */
  def validate[T](
    obj: T,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (obj == null) throw new IllegalArgumentException("value must not be null.")
    val context = ValidationContext[T](None, None, None, None, PathImpl.createRootPath())
    validate[T](
      maybeDescriptor = None,
      context = context,
      value = obj,
      groups = groups
    )
  }

  /**
   * Validates all constraint constraints on the `fieldName` field of the given class `beanType`
   * if the `fieldName` field value were `value`.
   *
   * ConstraintViolation objects return `null` for ConstraintViolation.getRootBean() and
   * ConstraintViolation.getLeafBean().
   *
   * ==Usage==
   * Validate value of "" for the "id" field in case class `MyClass` (enabling group "Checks"):
   * {{{
   *   case class MyCaseClass(@NotEmpty(groups = classOf[Checks]) id)
   *   validator.validateFieldValue(classOf[MyCaseClass], "id", "", Seq(classOf[Checks]))
   * }}}
   *
   * @param beanType     the case class type.
   * @param propertyName field to validate.
   * @param value        field value to validate.
   * @param groups       the list of groups targeted for validation (defaults to Default).
   * @tparam T the type of the object to validate
   *
   * @return constraint violations or an empty set if none
   *
   * @throws IllegalArgumentException - if beanType is null, if fieldName is null, empty or not a valid object property.
   */
  def validateValue[T](
    beanType: Class[T],
    propertyName: String,
    value: Any,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (beanType == null) throw new IllegalArgumentException("beanType must not be null.")
    val descriptor = getConstraintsForClass(beanType)
    validateValue[T](descriptor, propertyName, value, groups: _*)
  }

  /**
   * Validates all constraint constraints on the `fieldName` field of the class described
   * by the given [[CaseClassDescriptor]] if the `fieldName` field value were `value`.
   *
   * @param descriptor   the [[CaseClassDescriptor]] of the described class.
   * @param propertyName field to validate.
   * @param value        field value to validate.
   * @param groups       the list of groups targeted for validation (defaults to Default).
   * @tparam T the type of the object to validate
   *
   * @return constraint violations or an empty set if none
   *
   * @throws IllegalArgumentException - if fieldName is null, empty or not a valid object property.
   */
  def validateValue[T](
    descriptor: CaseClassDescriptor[T],
    propertyName: String,
    value: Any,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (descriptor == null)
      throw new IllegalArgumentException("descriptor must not be null.")
    if (propertyName == null || propertyName.isEmpty)
      throw new IllegalArgumentException("fieldName must not be null or empty.")

    val mangledName = DescriptorFactory.mangleName(propertyName)
    descriptor.members.get(mangledName) match {
      case Some(propertyDescriptor) =>
        val context = ValidationContext(
          Some(propertyName),
          Some(descriptor.clazz),
          None,
          None,
          PathImpl.createRootPath())
        validate(
          maybeDescriptor = Some(propertyDescriptor),
          context = context,
          value = value,
          groups = groups
        )
      case _ =>
        throw new IllegalArgumentException(s"$propertyName is not a field of ${descriptor.clazz}.")
    }
  }

  /**
   * Validates all constraint constraints on the `fieldName` field of the given object.
   *
   * ==Usage==
   * Validate the "id" field in case class `MyClass` (enabling the validation group "Checks"):
   * {{{
   *   case class MyClass(@NotEmpty(groups = classOf[Checks]) id)
   *   val i = MyClass("")
   *   validator.validateProperty(i, "id", Seq(classOf[Checks]))
   * }}}
   *
   * @param obj          object to validate.
   * @param propertyName property to validate (used in error reporting).
   * @param groups       the list of groups targeted for validation (defaults to Default).
   * @tparam T the type of the object to validate.
   *
   * @return constraint violations or an empty set if none.
   *
   * @throws IllegalArgumentException - if object is null, if fieldName is null, empty or not a valid object property.
   */
  def validateProperty[T](
    obj: T,
    propertyName: String,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (obj == null) throw new IllegalArgumentException("obj must not be null.")
    if (propertyName == null || propertyName.isEmpty)
      throw new IllegalArgumentException("fieldName must not be null or empty.")

    val caseClassDescriptor = getConstraintsForClass(obj.getClass)

    val mangledName = DescriptorFactory.mangleName(propertyName)
    caseClassDescriptor.members.get(mangledName) match {
      case Some(_) =>
        val context = ValidationContext(
          Some(propertyName),
          Some(obj.getClass.asInstanceOf[Class[T]]),
          Some(obj),
          Some(obj),
          PathImpl.createRootPath())
        validate(
          maybeDescriptor = Some(caseClassDescriptor),
          context = context,
          value = obj,
          groups = groups
        )
      case _ =>
        throw new IllegalArgumentException(
          s"$propertyName is not a field of ${caseClassDescriptor.clazz}.")
    }
  }

  /** Returns the contract for validating parameters and return values of methods and constructors. */
  def forExecutables: ScalaExecutableValidator = this

  /**
   * Returns an instance of the specified type allowing access to provider-specific APIs.
   *
   * If the Jakarta Bean Validation provider implementation does not support the specified class, ValidationException is thrown.
   *
   * @param clazz the class of the object to be returned
   * @tparam U the type of the object to be returned
   *
   * @return an instance of the specified class
   *
   * @throws ValidationException - if the provider does not support the call.
   */
  @throws[ValidationException]
  def unwrap[U](clazz: Class[U]): U = {
    // If this were an implementation that implemented the specification
    // interface, this is where users would be able to cast the validator from the
    // interface type into our specific implementation type in order to call methods
    // only available on the implementation. However, since the specification uses Java
    // collection types we do not directly implement and instead expose our feature-compatible
    // ScalaValidator directly. We try to remain true to the spirit of the specification
    // interface and thus implement unwrap but only to return this implementation.
    if (clazz.isAssignableFrom(classOf[ScalaValidator])) {
      this.asInstanceOf[U]
    } else if (clazz.isAssignableFrom(classOf[ScalaExecutableValidator])) {
      this.asInstanceOf[U]
    } else {
      throw new ValidationException(s"Type ${clazz.getName} not supported for unwrapping.")
    }
  }

  /* Executable Validator Methods */

  /** @inheritdoc */
  def validateMethods[T](
    obj: T,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (obj == null)
      throw new IllegalArgumentException("The object to be validated must not be null.")
    val caseClassDescriptor = getConstraintsForClass(obj.getClass)
    validateMethods[T](
      rootClazz = Some(obj.getClass.asInstanceOf[Class[T]]),
      root = Some(obj),
      methods = caseClassDescriptor.methods,
      obj = obj,
      groups = groups
    )
  }

  /** @inheritdoc */
  def validateMethod[T](
    obj: T,
    method: Method,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (obj == null)
      throw new IllegalArgumentException("The object to be validated must not be null.")
    if (method == null)
      throw new IllegalArgumentException("The method to be validated must not be null.")
    val caseClassDescriptor = getConstraintsForClass(obj.getClass)
    caseClassDescriptor.methods.find(_.method.getName == method.getName) match {
      case Some(methodDescriptor) =>
        validateMethods[T](
          rootClazz = Some(obj.getClass.asInstanceOf[Class[T]]),
          root = Some(obj),
          methods = Array(methodDescriptor),
          obj = obj,
          groups = groups
        )
      case _ =>
        throw new IllegalArgumentException(
          s"${method.getName} is not method of ${caseClassDescriptor.clazz}.")
    }
  }

  /** @inheritdoc */
  def validateParameters[T](
    obj: T,
    method: Method,
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (obj == null)
      throw new IllegalArgumentException("The object to be validated must not be null.")
    if (method == null)
      throw new IllegalArgumentException("The method to be validated must not be null.")

    val caseClassDescriptor = getConstraintsForClass(obj.getClass)
    caseClassDescriptor.methods.find(_.method.getName == method.getName) match {
      case Some(methodDescriptor: ExecutableDescriptor) =>
        validateExecutableParameters[T](
          executableDescriptor = methodDescriptor,
          root = Some(obj),
          leaf = Some(obj),
          parameterValues = parameterValues,
          parameterNames = None,
          groups = groups
        )
      case _ =>
        throw new IllegalArgumentException(
          s"${method.getName} is not method of ${caseClassDescriptor.clazz}.")
    }
  }

  /** @inheritdoc */
  def validateReturnValue[T](
    obj: T,
    method: Method,
    returnValue: Any,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = underlying
    .forExecutables().validateReturnValue(
      obj,
      method,
      returnValue,
      groups: _*
    ).asScala.toSet

  /** @inheritdoc */
  def validateConstructorParameters[T](
    constructor: Constructor[T],
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (constructor == null)
      throw new IllegalArgumentException("The executable to be validated must not be null.")
    if (parameterValues == null)
      throw new IllegalArgumentException("The constructor parameter array cannot not be null.")

    val parameterCount = constructor.getParameterCount
    val parameterValuesLength = parameterValues.length
    if (parameterCount != parameterValuesLength)
      throw new ValidationException(
        s"Wrong number of parameters. Method or constructor $constructor expects $parameterCount parameters, but got $parameterValuesLength.")

    validateExecutableParameters[T](
      executableDescriptor = descriptorFactory.describe[T](constructor),
      root = None,
      leaf = None,
      parameterValues = parameterValues,
      parameterNames = None,
      groups = groups
    )
  }

  /** @inheritdoc */
  def validateMethodParameters[T](
    method: Method,
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (method == null)
      throw new IllegalArgumentException("The executable to be validated must not be null.")
    if (parameterValues == null)
      throw new IllegalArgumentException("The method parameter array cannot not be null.")

    val parameterCount = method.getParameterCount
    val parameterValuesLength = parameterValues.length
    if (parameterCount != parameterValuesLength)
      throw new ValidationException(
        s"Wrong number of parameters. Method or constructor $method expects $parameterCount parameters, but got $parameterValuesLength.")

    descriptorFactory.describe(method) match {
      case Some(descriptor: ExecutableDescriptor) =>
        validateExecutableParameters[T](
          executableDescriptor = descriptor,
          root = None,
          leaf = None,
          parameterValues = parameterValues,
          parameterNames = None,
          groups = groups
        )
      case _ => Set.empty[ConstraintViolation[T]]
    }
  }

  /** @inheritdoc */
  def validateConstructorReturnValue[T](
    constructor: Constructor[T],
    createdObject: T,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] =
    underlying
      .forExecutables().validateConstructorReturnValue(
        constructor,
        createdObject,
        groups: _*).asScala.toSet

  /* Private */

  /** @inheritdoc */
  private[twitter] def validateExecutableParameters[T](
    executable: Executable,
    parameterValues: Array[Any],
    parameterNames: Array[String],
    mixinClazz: Option[Class[_]],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]] = {
    if (executable == null)
      throw new IllegalArgumentException("The executable to be validated must not be null.")
    if (parameterValues == null)
      throw new IllegalArgumentException("The constructor parameter array cannot not be null.")

    val parameterCount = executable.getParameterCount
    val parameterValuesLength = parameterValues.length
    if (parameterCount != parameterValuesLength)
      throw new ValidationException(
        s"Wrong number of parameters. Method or constructor $executable expects $parameterCount parameters, but got $parameterValuesLength.")

    executable match {
      case constructor: Constructor[_] =>
        validateExecutableParameters[T](
          executableDescriptor =
            descriptorFactory.describe[T](constructor.asInstanceOf[Constructor[T]], mixinClazz),
          root = None,
          leaf = None,
          parameterValues = parameterValues,
          parameterNames = Some(parameterNames),
          groups = groups
        )
      case _ =>
        descriptorFactory.describe(executable.asInstanceOf[Method]) match {
          case Some(descriptor: ExecutableDescriptor) =>
            validateExecutableParameters[T](
              executableDescriptor = descriptor,
              root = None,
              leaf = None,
              parameterValues = parameterValues,
              parameterNames = Some(parameterNames),
              groups = groups
            )
          case _ => Set.empty[ConstraintViolation[T]]
        }
    }
  }

  // note: Prefer while loop over Scala for loop for better performance. The scala for loop
  // performance is optimized in 2.13.0 if we enable scalac: https://github.com/scala/bug/issues/1338

  /**
   * Evaluates all known [[ConstraintValidator]] instances supporting the constraints represented by the
   * given mapping of [[Annotation]] to constraint attributes placed on a field `fieldName` if
   * the `fieldName` field value were `value`. The `fieldName` is used in error reporting (as the
   * the resulting property path for any resulting [[ConstraintViolation]]).
   *
   * E.g., the given mapping could be Map(jakarta.validation.constraint.Size -> Map("min" -> 0, "max" -> 1000))
   *
   * ConstraintViolation objects return `null` for ConstraintViolation.getRootBeanClass(),
   * ConstraintViolation.getRootBean() and ConstraintViolation.getLeafBean().
   *
   * @param constraints  the mapping of constraint [[Annotation]] to attributes to evaluate
   * @param fieldName    field to validate (used in error reporting).
   * @param value        field value to validate.
   * @param groups       the list of groups targeted for validation (defaults to Default).
   *
   * @return constraint violations or an empty set if none
   *
   * @throws IllegalArgumentException - if fieldName is null, empty or not a valid object property.
   *
   * @note Scala users should prefer the version which takes a [[Map]] of constraints and
   *       a [[Seq]] of groups.
   */
  @varargs
  private[twitter] def validateFieldValue(
    constraints: java.util.Map[Class[_ <: Annotation], java.util.Map[String, Any]],
    fieldName: String,
    value: Any,
    groups: Class[_]*
  ): java.util.Set[ConstraintViolation[Any]] = {
    if (fieldName == null || fieldName.isEmpty)
      throw new IllegalArgumentException("fieldName must not be null or empty.")
    val annotations = constraints.asScala.map {
      case (constraintAnnotationType, attributes)
          if isConstraintAnnotation(constraintAnnotationType) =>
        AnnotationFactory.newInstance(constraintAnnotationType, attributes.asScala.toMap)
    }
    validateFieldValue(fieldName, annotations.toArray, value, groups.toSeq).asJava
  }

  /**
   * Evaluates all known [[ConstraintValidator]] instances supporting the constraints represented by the
   * given mapping of [[Annotation]] to constraint attributes placed on a field `fieldName` if
   * the `fieldName` field value were `value`. The `fieldName` is used in error reporting (as the
   * the resulting property path for any resulting [[ConstraintViolation]]).
   *
   * E.g., the given mapping could be Map(jakarta.validation.constraint.Size -> Map("min" -> 0, "max" -> 1000))
   *
   * ConstraintViolation objects return `null` for ConstraintViolation.getRootBeanClass(),
   * ConstraintViolation.getRootBean() and ConstraintViolation.getLeafBean().
   *
   * @param constraints  the mapping of constraint [[Annotation]] to attributes to evaluate
   * @param fieldName    field to validate (used in error reporting).
   * @param value        field value to validate.
   * @param groups       the list of groups targeted for validation (defaults to Default).
   *
   * @return constraint violations or an empty set if none
   *
   * @throws IllegalArgumentException - if fieldName is null, empty or not a valid object property.
   *
   * @note Java users should prefer the version which takes a [[java.util.Map]] of constraints and
   *       a [[java.util.List]] of groups.
   */
  private[twitter] def validateFieldValue(
    constraints: Map[Class[_ <: Annotation], Map[String, Any]],
    fieldName: String,
    value: Any,
    groups: Class[_]*
  ): Set[ConstraintViolation[Any]] = {
    if (fieldName == null || fieldName.isEmpty)
      throw new IllegalArgumentException("fieldName must not be null or empty.")

    val annotations = constraints.map {
      case (constraintAnnotationType, attributes) =>
        AnnotationFactory.newInstance(constraintAnnotationType, attributes)
    }

    validateFieldValue(fieldName, annotations.toArray, value, groups)
  }

  /** Validate the given fieldName with the given constraint constraints, value, and groups. */
  private[this] def validateFieldValue(
    fieldName: String,
    constraints: Array[Annotation],
    value: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[Any]] =
    if (constraints.nonEmpty) {
      val results = new mutable.ListBuffer[ConstraintViolation[Any]]()
      var index = 0
      val length = constraints.length
      while (index < length) {
        val annotation = constraints(index)
        val path = PathImpl.createRootPath
        path.addPropertyNode(fieldName)
        val clazz = classOf[Any]
        val scalaType = Reflector.scalaTypeOf(value.getClass)
        val context = ValidationContext(Some(fieldName), Some(clazz), None, None, path)

        results.appendAll(
          isValid[Any](
            context = context,
            constraint = annotation,
            scalaType = scalaType,
            value = value,
            groups = groups
          )
        )
        index += 1
      }
      results.toSet
    } else Set.empty[ConstraintViolation[Any]]

  private[this] def findValidatedBy[A <: Annotation](
    annotationType: Class[A]
  ): Set[ConstraintValidatorType] = {
    if (annotationType.isAnnotationPresent(classOf[Constraint])) {
      val constraintAnnotation = annotationType.getAnnotation(classOf[Constraint])
      val clazzes: Array[Class[ConstraintValidatorType]] =
        constraintAnnotation.validatedBy().map(_.asInstanceOf[Class[ConstraintValidatorType]])

      clazzes.map { clazz =>
        val instance = validatorFactory.constraintValidatorFactory.getInstance(clazz)
        if (instance == null)
          throw new ValidationException(
            s"Constraint factory returned null when trying to create instance of $clazz.")
        instance
      }.toSet
    } else Set.empty
  }

  // https://beanvalidation.org/2.0/spec/#constraintsdefinitionimplementation-validationimplementation
  // From the documentation:
  //
  // While not mandatory, it is considered a good practice to split the core constraint
  // validation from the "not null" constraint validation (for example, an @Email constraint
  // should return "true" on a `null` object, i.e. it will not also assert the @NotNull validation).
  // A "null" can have multiple meanings but is commonly used to express that a value does
  // not make sense, is not available or is simply unknown. Those constraints on the
  // value are orthogonal in most cases to other constraints. For example a String,
  // if present, must be an email but can be null. Separating both concerns is a good
  // practice.
  //
  // Thus, we "ignore" any value that is null and not annotated with @NotNull.
  private[this] def ignorable(value: Any, annotation: Annotation): Boolean = value match {
    case null if !ReflectAnnotations.equals[jakarta.validation.constraints.NotNull](annotation) =>
      true
    case _ => false
  }

  /** Validate Executable field-level parameters (including cross-field parameters) */
  private[this] def validateExecutableParameters[T](
    executableDescriptor: ExecutableDescriptor,
    root: Option[T],
    leaf: Option[Any],
    parameterValues: Array[Any],
    parameterNames: Option[Array[String]],
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = {
    if (executableDescriptor.members.size != parameterValues.length)
      throw new IllegalArgumentException(
        s"Invalid number of arguments for method ${executableDescriptor.executable.getName}.")
    val results = new mutable.ListBuffer[ConstraintViolation[T]]()
    val parameters: Array[Parameter] = executableDescriptor.executable.getParameters
    val parameterNamesList: Array[String] =
      parameterNames match {
        case Some(names) =>
          names
        case _ =>
          val parameterNameProviderNames: java.util.List[String] =
            validatorFactory.validatorFactoryScopedContext.getParameterNameProvider
              .getParameterNames(executableDescriptor.executable)
          Array.tabulate(parameterNameProviderNames.size()) { index =>
            parameterNameProviderNames.get(index)
          }
      }

    // executable parameter constraints
    var index = 0
    val parametersLength = parameterNamesList.length
    while (index < parametersLength) {
      val parameter = parameters(index)
      val parameterValue = parameterValues(index)
      // only for property path use to affect error reporting
      val parameterName = parameterNamesList(index)

      val parameterPath =
        PathImpl.createPathForExecutable(getExecutableMetaData(executableDescriptor.executable))
      parameterPath.addParameterNode(parameterName, index)

      val propertyDescriptor = executableDescriptor.members(parameter.getName)
      val validateFieldContext =
        ValidationContext[T](
          Some(parameterName),
          Some(executableDescriptor.executable.getDeclaringClass.asInstanceOf[Class[T]]),
          root,
          leaf,
          parameterPath)
      val parameterViolations = validateField[T](
        validateFieldContext,
        propertyDescriptor,
        parameterValue,
        groups
      )
      if (parameterViolations.nonEmpty) results.appendAll(parameterViolations)

      index += 1
    }
    results.toSet

    // executable cross-parameter constraints
    val crossParameterPath = PathImpl
      .createPathForExecutable(getExecutableMetaData(executableDescriptor.executable))
    crossParameterPath.addCrossParameterNode()
    val constraints = executableDescriptor.annotations
    index = 0
    val constraintsLength = constraints.length
    while (index < constraintsLength) {
      val constraint = constraints(index)
      val scalaType = Reflector.scalaTypeOf(parameterValues.getClass)

      val validators =
        findConstraintValidators(constraint.annotationType())
          .filter(parametersValidationTargetFilter)

      val validatorsIterator = validators.iterator
      while (validatorsIterator.hasNext) {
        val validator = validatorsIterator.next().asInstanceOf[ConstraintValidator[Annotation, _]]
        val constraintDescriptor = constraintDescriptorFactory
          .newConstraintDescriptor(
            name = executableDescriptor.executable.getName,
            clazz = scalaType.erasure,
            declaringClazz = executableDescriptor.executable.getDeclaringClass,
            annotation = constraint,
            constrainedElementKind = ConstrainedElementKind.METHOD
          )
        if (groupsEnabled(constraintDescriptor, groups)) {
          // initialize validator
          validator.initialize(constraint)
          val validateCrossParameterContext =
            ValidationContext[T](
              None,
              Some(executableDescriptor.executable.getDeclaringClass.asInstanceOf[Class[T]]),
              root,
              leaf,
              crossParameterPath)
          results.appendAll(
            isValid(
              context = validateCrossParameterContext,
              constraint = constraint,
              scalaType = scalaType,
              value = parameterValues,
              constraintDescriptorOption = Some(constraintDescriptor),
              validatorOption = Some(validator.asInstanceOf[ConstraintValidator[Annotation, Any]]),
              groups = groups
            )
          )
        }
      }
      index += 1
    }
    results.toSet
  }

  /** Validate method-level constraints, i.e., @MethodValidation annotated methods */
  private[this] def validateMethods[T](
    rootClazz: Option[Class[T]],
    root: Option[T],
    methods: Array[MethodDescriptor],
    obj: T,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = {
    val results = new mutable.ListBuffer[ConstraintViolation[T]]()
    if (methods.nonEmpty) {
      var index = 0
      val length = methods.length
      while (index < length) {
        val methodDescriptor = methods(index)
        val validateMethodContext =
          ValidationContext(None, rootClazz, root, Some(obj), PathImpl.createRootPath())
        results.appendAll(
          validateMethod(validateMethodContext, methodDescriptor, obj, groups)
        )
        index += 1
      }
    }
    results.toSet
  }

  // BEGIN: Recursive validation methods -----------------------------------------------------------

  /** Validate field-level constraints */
  private[this] def validateField[T](
    context: ValidationContext[T],
    propertyDescriptor: PropertyDescriptor,
    fieldValue: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = {
    val constraints: Array[Annotation] = propertyDescriptor.annotations
    val results = new mutable.ListBuffer[ConstraintViolation[T]]()
    var index = 0
    val length = constraints.length
    while (index < length) {
      val constraint = constraints(index)
      results.appendAll(
        isValid[T](
          context,
          constraint = constraint,
          scalaType = propertyDescriptor.scalaType,
          value = fieldValue,
          groups = groups
        )
      )
      index += 1
    }

    // Cannot cascade a null or None value
    fieldValue match {
      case null | None => // do nothing
      case _ =>
        if (propertyDescriptor.isCascaded) {
          results.appendAll(
            validateCascadedProperty[T](context, propertyDescriptor, fieldValue, groups)
          )
        }
    }

    if (results.isEmpty) Set.empty[ConstraintViolation[T]]
    else results.toSet
  }

  /** Validate cascaded field-level properties */
  private[this] def validateCascadedProperty[T](
    context: ValidationContext[T],
    propertyDescriptor: PropertyDescriptor,
    clazzInstance: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = propertyDescriptor.cascadedScalaType match {
    case Some(cascadedScalaType) =>
      // Update the found value with the current landscape, for example, the retrieved case class
      // descriptor may be scala type Foo, but this property is a Seq[Foo] or an Option[Foo] and
      // we want to carry that property typing through.
      val caseClassDescriptor =
        descriptorFactory
          .describe[T](cascadedScalaType.erasure.asInstanceOf[Class[T]])
          .copy(scalaType = propertyDescriptor.scalaType)

      val caseClassPath = PathImpl.createCopy(context.path)
      caseClassDescriptor.scalaType match {
        case argType if argType.isCollection =>
          val results = mutable.ListBuffer[ConstraintViolation[T]]()
          val collectionValue: Iterable[_] = clazzInstance.asInstanceOf[Iterable[_]]
          val collectionValueIterator = collectionValue.iterator
          var index = 0
          while (collectionValueIterator.hasNext) {
            val instanceValue = collectionValueIterator.next()
            // apply the index to the parent path, then use this to recompute paths of members and methods
            val indexedPropertyPath = {
              val indexedPath = PathImpl.createCopyWithoutLeafNode(caseClassPath)
              indexedPath.addPropertyNode(
                s"${caseClassPath.getLeafNode.asString()}[${index.toString}]")
              indexedPath
            }
            val violations = validate[T](
              maybeDescriptor = Some(
                caseClassDescriptor
                  .copy(
                    scalaType = caseClassDescriptor.scalaType.typeArgs.head,
                    members = caseClassDescriptor.members,
                    methods = caseClassDescriptor.methods
                  )
              ),
              context = context.copy(path = indexedPropertyPath),
              value = instanceValue,
              groups = groups
            )
            if (violations.nonEmpty) results.appendAll(violations)
            index += 1
          }
          if (results.nonEmpty) results.toSet
          else Set.empty[ConstraintViolation[T]]
        case argType if argType.isOption =>
          val optionValue = clazzInstance.asInstanceOf[Option[_]]
          validate[T](
            maybeDescriptor = Some(
              caseClassDescriptor.copy(scalaType = caseClassDescriptor.scalaType.typeArgs.head)),
            context = context.copy(path = caseClassPath),
            value = optionValue,
            groups = groups
          )
        case _ =>
          validate[T](
            maybeDescriptor = Some(caseClassDescriptor),
            context = context.copy(path = caseClassPath),
            value = clazzInstance,
            groups = groups
          )
      }
    case _ => Set.empty[ConstraintViolation[T]]
  }

  /** Validate @MethodValidation annotated methods */
  private[this] def validateMethod[T](
    context: ValidationContext[T],
    methodDescriptor: MethodDescriptor,
    clazzInstance: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] =
    ReflectAnnotations.findAnnotation[MethodValidation](methodDescriptor.annotations) match {
      case Some(annotation) =>
        // if we're unable to invoke the method, we want this propagate
        val constraintDescriptor = new MethodValidationConstraintDescriptor(annotation)
        if (groupsEnabled(constraintDescriptor, groups)) {
          try {
            val result =
              methodDescriptor.method.invoke(clazzInstance).asInstanceOf[MethodValidationResult]
            val pathWithMethodName = PathImpl.createCopy(context.path)
            pathWithMethodName.addPropertyNode(methodDescriptor.method.getName)
            parseMethodValidationFailures[T](
              rootClazz = context.rootClazz,
              root = context.root,
              leaf = Some(clazzInstance),
              path = pathWithMethodName,
              annotation = annotation.asInstanceOf[MethodValidation],
              method = methodDescriptor.method,
              result = result,
              value = clazzInstance,
              constraintDescriptor
            )
          } catch {
            case e: InvocationTargetException if e.getCause != null =>
              throw e.getCause
          }
        } else Set.empty[ConstraintViolation[T]]
      case _ => Set.empty[ConstraintViolation[T]]
    }

  /** Validate class-level constraints */
  private[this] def validateClazz[T](
    context: ValidationContext[T],
    caseClassDescriptor: CaseClassDescriptor[T],
    clazzInstance: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = {
    val constraints = caseClassDescriptor.annotations
    if (constraints.nonEmpty) {
      val results = new mutable.ListBuffer[ConstraintViolation[T]]()
      var index = 0
      val length = constraints.length
      while (index < length) {
        val annotation = constraints(index)
        results.appendAll(
          isValid[T](
            context = context,
            constraint = annotation,
            scalaType = caseClassDescriptor.scalaType,
            value = clazzInstance,
            groups = groups
          )
        )
        index += 1
      }
      results.toSet
    } else Set.empty[ConstraintViolation[T]]
  }

  /** Recursively validate full case class */
  @tailrec
  private[this] def validate[T](
    maybeDescriptor: Option[Descriptor],
    context: ValidationContext[T],
    value: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = maybeDescriptor match {
    case Some(descriptor) =>
      descriptor match {
        case p: PropertyDescriptor =>
          val propertyPath = PathImpl.createCopy(context.path)
          context.fieldName.map(propertyPath.addPropertyNode)
          // validateField recurses back through validate for cascaded properties
          validateField[T](context.copy(path = propertyPath), p, value, groups)
        case c: CaseClassDescriptor[_] =>
          val memberViolations = {
            val results = new mutable.ListBuffer[ConstraintViolation[T]]()
            if (c.members.nonEmpty) {
              val keys: Iterable[String] = c.members.keys
              val keyIterator = keys.iterator
              while (keyIterator.hasNext) {
                val name = keyIterator.next()
                val propertyDescriptor = c.members(name)
                val propertyPath = PathImpl.createCopy(context.path)
                propertyPath.addPropertyNode(DescriptorFactory.unmangleName(name))
                // validateField recurses back through validate here for cascaded properties
                val fieldResults =
                  validateField[T](
                    context.copy(fieldName = Some(name), path = propertyPath),
                    propertyDescriptor,
                    findFieldValue(value, c.clazz, name),
                    groups)
                if (fieldResults.nonEmpty) results.appendAll(fieldResults)
              }
            }
            results.toSet
          }
          val methodViolations = {
            val results = new ListBuffer[ConstraintViolation[T]]()
            if (c.methods.nonEmpty) {
              var index = 0
              val length = c.methods.length
              while (index < length) {
                val methodResults = value match {
                  case null | None =>
                    Set.empty
                  case Some(obj) =>
                    validateMethod[T](context.copy(fieldName = None), c.methods(index), obj, groups)
                  case _ =>
                    validateMethod[T](
                      context.copy(fieldName = None),
                      c.methods(index),
                      value,
                      groups)
                }

                if (methodResults.nonEmpty) results.appendAll(methodResults)
                index += 1
              }
            }
            results.toSet
          }
          val clazzViolations =
            validateClazz[T](context, c.asInstanceOf[CaseClassDescriptor[T]], value, groups)

          // put them all together
          memberViolations ++ methodViolations ++ clazzViolations
      }
    case _ =>
      val clazz: Class[T] = value.getClass.asInstanceOf[Class[T]]
      if (ReflectTypes.notCaseClass(clazz))
        throw new ValidationException(s"$clazz is not a valid case class.")
      val caseClassDescriptor: CaseClassDescriptor[T] = descriptorFactory.describe(clazz)
      val context = ValidationContext(
        fieldName = None,
        rootClazz = Some(clazz),
        root = Some(value.asInstanceOf[T]),
        leaf = Some(value),
        path = PathImpl.createRootPath())
      validate[T](Some(caseClassDescriptor), context, value, groups)
  }

  // END: Recursive validation methods -------------------------------------------------------------

  @tailrec
  private[this] def findFieldValue[T](
    obj: Any,
    clazz: Class[T],
    name: String
  ): Any = obj match {
    case null | None =>
      obj
    case Some(v) =>
      findFieldValue(v, clazz, name)
    case _ =>
      try {
        val field = clazz.getDeclaredField(name)
        field.setAccessible(true)
        Option(field.get(obj)).orNull.asInstanceOf[T]
      } catch {
        case NonFatal(t) =>
          throw new ValidationException(t)
      }
  }

  private[this] def isValid[T](
    context: ValidationContext[T],
    constraint: Annotation,
    scalaType: ScalaType,
    value: Any,
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] =
    isValid(context, constraint, scalaType, value, None, None, groups)

  private[this] def isValid[T](
    context: ValidationContext[T],
    constraint: Annotation,
    scalaType: ScalaType,
    value: Any,
    constraintDescriptorOption: Option[ConstraintDescriptorImpl[Annotation]],
    validatorOption: Option[ConstraintValidator[Annotation, Any]],
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = value match {
    case _: Option[_] =>
      isValidOption(
        context,
        constraint,
        scalaType,
        value,
        constraintDescriptorOption,
        validatorOption,
        groups)
    case _ =>
      val constraintDescriptor = constraintDescriptorOption match {
        case Some(descriptor) =>
          descriptor
        case _ =>
          constraintDescriptorFactory.newConstraintDescriptor(
            name = context.fieldName.orNull,
            clazz = scalaType.erasure,
            declaringClazz = context.rootClazz.getOrElse(scalaType.erasure),
            annotation = constraint
          )
      }

      if (!ignorable(value, constraint) && groupsEnabled(constraintDescriptor, groups)) {
        val refinedScalaType =
          Types.refineScalaType(value, scalaType)

        val constraintValidator: ConstraintValidator[Annotation, Any] = validatorOption match {
          case Some(validator) =>
            validator // should already be initialized
          case _ =>
            constraintValidatorManager
              .getInitializedValidator[Annotation](
                Types.getJavaType(refinedScalaType),
                constraintDescriptor,
                validatorFactory.constraintValidatorFactory,
                validatorFactory.validatorFactoryScopedContext.getConstraintValidatorInitializationContext
              ).asInstanceOf[ConstraintValidator[Annotation, Any]]
        }

        if (constraintValidator == null) {
          val configuration =
            if (context.path.toString.isEmpty) Classes.simpleName(scalaType.erasure)
            else context.path.toString
          throw new UnexpectedTypeException(
            s"No validator could be found for constraint '${constraint.annotationType()}' " +
              s"validating type '${scalaType.erasure.getName}'. " +
              s"Check configuration for '$configuration'")
        }
        // create validator context
        val constraintValidatorContext: ConstraintValidatorContext =
          constraintValidatorContextFactory.newConstraintValidatorContext(
            context.path,
            constraintDescriptor)
        // compute if valid
        if (constraintValidator.isValid(value, constraintValidatorContext)) Set.empty
        else {
          constraintViolationFactory.buildConstraintViolations[T](
            rootClazz = context.rootClazz,
            root = context.root,
            leaf = context.leaf,
            path = context.path,
            invalidValue = value,
            constraintDescriptor = constraintDescriptor,
            constraintValidatorContext = constraintValidatorContext
          )
        }
      } else Set.empty
  }

  private[this] def isValidOption[T](
    context: ValidationContext[T],
    constraint: Annotation,
    scalaType: ScalaType,
    value: Any,
    constraintDescriptorOption: Option[ConstraintDescriptorImpl[Annotation]],
    validatorOption: Option[ConstraintValidator[Annotation, Any]],
    groups: Seq[Class[_]]
  ): Set[ConstraintViolation[T]] = value match {
    case Some(actualVal) =>
      isValid[T](
        context,
        constraint,
        scalaType,
        actualVal,
        constraintDescriptorOption,
        validatorOption,
        groups)
    case _ =>
      Set.empty[ConstraintViolation[T]]
  }

  private[this] def groupsEnabled(
    constraintDescriptor: ConstraintDescriptor[_],
    groups: Seq[Class[_]]
  ): Boolean = {
    val groupsFromAnnotation: java.util.Set[Class[_]] = constraintDescriptor.getGroups
    if (groups.isEmpty && groupsFromAnnotation.isEmpty) true
    else if (groups.isEmpty &&
      groupsFromAnnotation.contains(classOf[jakarta.validation.groups.Default])) true
    else if (groups.contains(classOf[jakarta.validation.groups.Default]) &&
      groupsFromAnnotation.isEmpty) true
    else groups.exists(groupsFromAnnotation.contains)
  }

  private[this] def parametersValidationTargetFilter(
    constraintValidator: ConstraintValidator[_, _]
  ): Boolean =
    ReflectAnnotations
      .findAnnotation(
        classOf[SupportedValidationTarget],
        constraintValidator.getClass.getAnnotations) match {
      case Some(annotation: SupportedValidationTarget) =>
        annotation.value().contains(ValidationTarget.PARAMETERS)
      case _ => false
    }

  private[this] def getExecutableMetaData[T](executable: Executable): ExecutableMetaData = {
    val callable: ExecutableCallable[_] = executable match {
      case constructor: Constructor[_] =>
        new ConstructorCallable(constructor)
      case method: Method =>
        new MethodCallable(method)
    }
    val builder = new ExecutableMetaData.Builder(
      executable.getDeclaringClass,
      new ConstrainedExecutable(
        ConfigurationSource.ANNOTATION,
        callable,
        callable.getParameters
          .map(p =>
            new ConstrainedParameter(
              ConfigurationSource.ANNOTATION,
              callable,
              p.getType,
              p.getIndex)).asJava,
        Collections.emptySet(),
        Collections.emptySet(),
        Collections.emptySet(),
        CascadingMetaDataBuilder.nonCascading
      ),
      validatorFactory.getConstraintCreationContext,
      validatorFactory.getExecutableHelper,
      validatorFactory.getExecutableParameterNameProvider,
      validatorFactory.getMethodValidationConfiguration
    )

    builder.build()
  }

  private[validation] def parseMethodValidationFailures[T](
    rootClazz: Option[Class[T]],
    root: Option[T],
    leaf: Option[Any],
    path: PathImpl,
    annotation: MethodValidation,
    method: Method,
    result: MethodValidationResult,
    value: Any,
    constraintDescriptor: ConstraintDescriptor[_]
  ): Set[ConstraintViolation[T]] = {
    def methodValidationConstraintViolation(
      validationPath: PathImpl,
      message: String,
      payload: Payload
    ): ConstraintViolation[T] = {
      constraintViolationFactory.newConstraintViolation[T](
        messageTemplate = annotation.message(),
        interpolatedMessage = message,
        path = validationPath,
        invalidValue = value.asInstanceOf[T],
        rootClazz = rootClazz.orNull,
        root = root.getOrElse(null.asInstanceOf[T]),
        leaf = leaf.orNull,
        constraintDescriptor = constraintDescriptor,
        payload = payload
      )
    }

    result match {
      case invalid: MethodValidationResult.Invalid =>
        val results = mutable.HashSet[ConstraintViolation[T]]()
        val annotationFields: Array[String] = annotation.fields.filter(_.nonEmpty)
        if (annotationFields.nonEmpty) {
          var index = 0
          val length = annotationFields.length
          while (index < length) {
            val fieldName = annotationFields(index)
            val parameterPath = PathImpl.createCopy(path)
            parameterPath.addParameterNode(fieldName, index)
            results.add(
              methodValidationConstraintViolation(
                parameterPath,
                invalid.message,
                invalid.payload.orNull)
            )
            index += 1
          }
          if (results.nonEmpty) results.toSet
          else Set.empty[ConstraintViolation[T]]
        } else {
          Set(methodValidationConstraintViolation(path, invalid.message, invalid.payload.orNull))
        }
      case _ => Set.empty[ConstraintViolation[T]]
    }
  }
}
