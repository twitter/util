package com.twitter.util.validation.engine

import com.twitter.util.{Return, Try}
import com.twitter.util.validation.internal.engine.ConstraintViolationFactory
import jakarta.validation.{ConstraintViolation, Payload}
import org.hibernate.validator.engine.HibernateConstraintViolation

/** Helper for [[jakarta.validation.ConstraintViolation]] */
object ConstraintViolationHelper {

  /**
   * Sorts the given [[Set]] by natural order of String created by concatenating the
   * [[ConstraintViolation#getPropertyPath]] and [[ConstraintViolation#getMessage]] separated
   * by a colon (:).
   *
   * @see [[messageWithPath]]
   */
  def sortViolations[T](violations: Set[ConstraintViolation[T]]): Seq[ConstraintViolation[T]] =
    ConstraintViolationFactory.sortSet[T](violations)

  /**
   * Returns a String created by concatenating the [[ConstraintViolation#getPropertyPath]] and
   * the [[ConstraintViolation#getMessage]] separated by a colon (:). E.g. if a given violation
   * has a `getPropertyPath.toString` value of "foo" and a `getMessage` value of "does not exist",
   * this would return "foo: does not exist".
   */
  def messageWithPath(violation: ConstraintViolation[_]): String =
    ConstraintViolationFactory.getMessage(violation)

  /** Lookup the dynamic payload of the given class type */
  def dynamicPayload[P <: Payload](
    violation: ConstraintViolation[_],
    clazz: Class[P]
  ): Option[P] = {
    Try(violation.unwrap(classOf[HibernateConstraintViolation[_]])) match {
      case Return(hibernateConstraintViolation) =>
        Option(hibernateConstraintViolation.getDynamicPayload[P](clazz))
      case _ => None
    }
  }
}
