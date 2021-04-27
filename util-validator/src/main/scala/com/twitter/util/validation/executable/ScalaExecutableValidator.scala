package com.twitter.util.validation.executable

import jakarta.validation.{ConstraintViolation, ValidationException}
import java.lang.reflect.{Constructor, Executable, Method}

/**
 * Scala version of the Bean Specification [[jakarta.validation.executable.ExecutableValidator]].
 *
 * Validates parameters and return values of methods and constructors. Implementations of this interface must be thread-safe.
 */
trait ScalaExecutableValidator {

  /**
   * Validates all methods annotated with `@MethodValidation` of the given object.
   *
   * @param obj the object on which the methods to validate are invoked.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the methods to validate.
   *
   * @return a set with the constraint violations caused by this validation; will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for the object to validate.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateMethods[T](
    obj: T,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates the given method annotated with `@MethodValidation` of the given object.
   *
   * @param obj the object on which the method to validate is invoked.
   * @param method the `@MethodValidation` annotated method to invoke for validation.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the method to validate.
   *
   * @return a set with the constraint violations caused by this validation; will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for the object to validate or if the given method is not a method of the object class type.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateMethod[T](
    obj: T,
    method: Method,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all constraints placed on the parameters of the given method.
   *
   * @param obj the object on which the method to validate is invoked.
   * @param method the method for which the parameter constraints is validated.
   * @param parameterValues the values provided by the caller for the given method's parameters.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the method to validate.
   *
   * @return a set with the constraint violations caused by this validation; will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateParameters[T](
    obj: T,
    method: Method,
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all return value constraints of the given method.
   *
   * @param obj the object on which the method to validate is invoked.
   * @param method the method for which the return value constraints is validated.
   * @param returnValue the value returned by the given method.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the method to validate.
   *
   * @return a set with the constraint violations caused by this validation; will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateReturnValue[T](
    obj: T,
    method: Method,
    returnValue: Any,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all constraints placed on the parameters of the given constructor.
   *
   * @param constructor the constructor for which the parameter constraints is validated.
   * @param parameterValues the values provided by the caller for the given constructor's parameters.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the constructor to validate.
   *
   * @return a set with the constraint violations caused by this validation; Will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateConstructorParameters[T](
    constructor: Constructor[T],
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all constraints placed on the parameters of the given method.
   *
   * @param method the method for which the parameter constraints is validated.
   * @param parameterValues the values provided by the caller for the given method's parameters.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type defining the method to validate.
   *
   * @return a set with the constraint violations caused by this validation; Will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateMethodParameters[T](
    method: Method,
    parameterValues: Array[Any],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all return value constraints of the given constructor.
   *
   * @param constructor the constructor for which the return value constraints is validated.
   * @param createdObject the object instantiated by the given method.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type hosting the constructor to validate.
   *
   * @return a set with the constraint violations caused by this validation; will be empty, if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  def validateConstructorReturnValue[T](
    constructor: Constructor[T],
    createdObject: T,
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]

  /**
   * Validates all constraints placed on the parameters of the given executable.
   *
   * @param executable the executable for which the parameter constraints is validated.
   * @param parameterValues the values provided by the caller for the given executable's parameters.
   * @param parameterNames the parameter names to use for error reporting.
   * @param mixinClazz the optional mix-in class to also consider for annotation processing.
   * @param groups the list of groups targeted for validation (defaults to Default).
   * @tparam T the type defining the executable to validate.
   *
   * @return a set with the constraint violations caused by this validation; Will be empty if no error occurs, but never null.
   *
   * @throws IllegalArgumentException - if null is passed for any of the parameters or if parameters don't match with each other.
   * @throws ValidationException      - if a non recoverable error happens during the validation process.
   */
  @throws[ValidationException]
  private[twitter] def validateExecutableParameters[T](
    executable: Executable,
    parameterValues: Array[Any],
    parameterNames: Array[String],
    mixinClazz: Option[Class[_]],
    groups: Class[_]*
  ): Set[ConstraintViolation[T]]
}
