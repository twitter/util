package com.twitter.util.jackson.caseclass.exceptions

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import jakarta.validation.{ConstraintViolation, Payload}

object CaseClassFieldMappingException {

  /** Marker trait for CaseClassFieldMappingException detail */
  sealed trait Detail

  /** No specified detail */
  case object Unspecified extends Detail

  /** A case class field specified as required is missing from JSON to deserialize */
  case object RequiredFieldMissing extends Detail

  object ValidationError {
    sealed trait Location
    case object Field extends Location
    case object Method extends Location
  }

  /** A violation was raised when performing a field or method validation */
  case class ValidationError(
    violation: ConstraintViolation[_],
    location: ValidationError.Location,
    payload: Option[Payload])
      extends Detail

  /** A JsonProcessingException occurred during deserialization */
  case class JsonProcessingError(cause: JsonProcessingException) extends Detail

  /** Throwable detail which specifies a message */
  case class ThrowableError(message: String, cause: Throwable) extends Detail

  /**
   * Represents the reason with message and more details for the field mapping exception.
   */
  case class Reason(
    message: String,
    detail: Detail = Unspecified)

  object PropertyPath {
    val Empty: PropertyPath = PropertyPath(Seq.empty)
    private val FieldSeparator = "."
    def leaf(name: String): PropertyPath = Empty.withParent(name)
  }

  /** Represents a path to the case class field/property germane to the exception */
  case class PropertyPath(names: Seq[String]) {
    private[jackson] def withParent(name: String): PropertyPath = copy(name +: names)

    def isEmpty: Boolean = names.isEmpty
    def prettyString: String = names.mkString(PropertyPath.FieldSeparator)
  }
}

/**
 * A subclass of [[JsonMappingException]] which bundles together a failed field location as a
 * `CaseClassFieldMappingException.PropertyPath` with the the failure reason represented by a
 * `CaseClassFieldMappingException.Reason` to carry the failure reason.
 *
 * @note this exception is a case class in order to have a useful equals() and hashcode()
 *       methods since this exception is generally carried in a collection inside of a
 *       [[CaseClassMappingException]].
 * @param path   - a `CaseClassFieldMappingException.PropertyPath` instance to the case class field
 *               that caused the failure.
 * @param reason - an instance of a `CaseClassFieldMappingException.Reason` which carries detail of the
 *               failure reason.
 *
 * @see [[com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.PropertyPath]]
 * @see [[com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.Reason]]
 * @see [[com.fasterxml.jackson.databind.JsonMappingException]]
 * @see [[CaseClassMappingException]]
 */
case class CaseClassFieldMappingException(
  path: CaseClassFieldMappingException.PropertyPath,
  reason: CaseClassFieldMappingException.Reason)
    extends JsonMappingException(null, reason.message) {

  /* Public */

  /**
   * Render a human readable message. If the error message pertains to
   * a specific field it is prefixed with the field's name.
   */
  override def getMessage: String = {
    if (path == null || path.isEmpty) reason.message
    else s"${path.prettyString}: ${reason.message}"
  }

  /* Private */

  /** fill in any missing PropertyPath information */
  private[jackson] def withPropertyPath(
    path: CaseClassFieldMappingException.PropertyPath
  ): CaseClassFieldMappingException = this.copy(path)

  private[jackson] def scoped(fieldName: String): CaseClassFieldMappingException = {
    copy(path = path.withParent(fieldName))
  }
}
