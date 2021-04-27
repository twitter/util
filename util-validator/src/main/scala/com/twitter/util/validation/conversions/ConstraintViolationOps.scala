package com.twitter.util.validation.conversions

import com.twitter.util.validation.engine.ConstraintViolationHelper
import jakarta.validation.{ConstraintViolation, Payload}

object ConstraintViolationOps {

  implicit class RichConstraintViolation[T](val self: ConstraintViolation[T]) extends AnyVal {

    /**
     * Returns a String created by concatenating the [[ConstraintViolation#getPropertyPath]] and
     * the [[ConstraintViolation#getMessage]] separated by a colon (:). E.g. if a given violation
     * has a `getPropertyPath.toString` value of "foo" and a `getMessage` value of "does not exist",
     * this would return "foo: does not exist".
     */
    def getMessageWithPath: String = ConstraintViolationHelper.messageWithPath(self)

    /** Lookup the dynamic payload of the given class type */
    def getDynamicPayload[P <: Payload](clazz: Class[P]): Option[P] =
      ConstraintViolationHelper.dynamicPayload[P](self, clazz)
  }
}
