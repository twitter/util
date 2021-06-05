package com.twitter.util.jackson.caseclass.exceptions

import scala.util.control.NoStackTrace

/**
 * Represents an exception during deserialization due to the inability to properly inject a value via
 * Jackson [[com.fasterxml.jackson.databind.InjectableValues]]. A common cause is an incorrectly
 * configured mapper that is not instantiated with an appropriate instance of a
 * [[com.fasterxml.jackson.databind.InjectableValues]] that can locate the requested value to inject
 * during deserialization.
 *
 * @note this exception is not handled during case class deserialization and is thus expected to be
 *       handled by callers.
 */
class InjectableValuesException(message: String, cause: Throwable)
    extends Exception(message, cause)
    with NoStackTrace {

  def this(message: String) {
    this(message, null)
  }
}
