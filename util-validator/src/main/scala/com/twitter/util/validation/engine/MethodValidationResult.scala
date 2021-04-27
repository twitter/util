package com.twitter.util.validation.engine

import com.twitter.util.logging.Logger
import com.twitter.util.{Return, Throw, Try}
import jakarta.validation.Payload

sealed trait MethodValidationResult {
  def payload: Option[Payload]
  def isValid: Boolean
}

object MethodValidationResult {
  private[this] val logger: Logger = Logger(MethodValidationResult.getClass)

  case object EmptyPayload extends Payload

  case object Valid extends MethodValidationResult {
    override final val payload: Option[Payload] = None
    override final val isValid: Boolean = true
  }

  case class Invalid(
    message: String,
    payload: Option[Payload] = None)
      extends MethodValidationResult {
    override final val isValid: Boolean = false
  }

  /**
   * Utility for evaluating a condition in order to return a [[MethodValidationResult]]. Returns
   * [[MethodValidationResult.Valid]] when the condition is `true`, otherwise if the condition
   * evaluates to `false` or throws an exception a [[MethodValidationResult.Invalid]] will be returned.
   * In the case of an exception, the `exception.getMessage` is used in place of the given message.
   *
   * @note This will not allow a non-fatal exception to escape. Instead a [[MethodValidationResult.Invalid]]
   *       will be returned when a non-fatal exception is encountered when evaluating `condition`. As
   *       this equates failure to execute the condition function to a return of `false`.
   * @param condition function to evaluate for validation.
   * @param message function to evaluate for a message when the given condition is `false`.
   * @param payload [[Payload]] to use for when the given condition is `false`.
   *
   * @return a [[MethodValidationResult.Valid]] when the condition is `true` otherwise a [[MethodValidationResult.Invalid]].
   */
  def validIfTrue(
    condition: => Boolean,
    message: => String,
    payload: Option[Payload] = None,
  ): MethodValidationResult = Try(condition) match {
    case Return(result) if result =>
      Valid
    case Return(result) if !result =>
      Invalid(message, payload)
    case Throw(e) =>
      logger.warn(e.getMessage, e)
      Invalid(e.getMessage, payload)
  }

  /**
   * Utility for evaluating the negation of a condition in order to return a [[MethodValidationResult]].
   * Returns [[MethodValidationResult.Valid]] when the condition is `false`, otherwise if the condition
   * evaluates to `true` or throws an exception a [[MethodValidationResult.Invalid]] will be returned.
   * In the case of an exception, the `exception.getMessage` is used in place of the given message.
   *
   * @note This will not allow a non-fatal exception to escape. Instead a [[MethodValidationResult.Valid]]
   *       will be returned when a non-fatal exception is encountered when evaluating `condition`. As
   *       this equates failure to execute the condition to a return of `false`.
   * @param condition function to evaluate for validation.
   * @param message function to evaluate for a message when the given condition is `true`.
   * @param payload [[Payload]] to use for when the given condition is `true`.
   *
   * @return a [[MethodValidationResult.Valid]] when the condition is `false` or when the condition evaluation
   *         throws a NonFatal exception otherwise a [[MethodValidationResult.Invalid]].
   */
  def validIfFalse(
    condition: => Boolean,
    message: => String,
    payload: Option[Payload] = None
  ): MethodValidationResult =
    Try(condition) match {
      case Return(result) if !result =>
        Valid
      case Return(result) if result =>
        Invalid(message, payload)
      case Throw(e) =>
        logger.warn(e.getMessage, e)
        Valid
    }
}
