package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.ScalaValidator
import jakarta.validation.ConstraintViolation
import java.lang.annotation.Annotation
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scala.reflect.runtime.universe._

@RunWith(classOf[JUnitRunner])
abstract class ConstraintValidatorTest
    extends AnyFunSuite
    with Matchers
    with ScalaCheckDrivenPropertyChecks {

  protected def validator: ScalaValidator

  protected def testFieldName: String

  protected def validate[A <: Annotation, T: TypeTag](
    clazz: Class[_],
    paramName: String,
    value: Any
  ): Set[ConstraintViolation[T]] =
    validator.validateValue(clazz.asInstanceOf[Class[T]], paramName, value)
}
