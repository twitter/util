package com.twitter.util.validation

import com.twitter.util.validation.engine.ConstraintViolationHelper
import jakarta.validation.ConstraintViolation
import java.lang.annotation.Annotation
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import scala.reflect.runtime.universe._

object AssertViolationTest {
  case class WithViolation[T](
    path: String,
    message: String,
    invalidValue: Any,
    rootBeanClazz: Class[T],
    root: T,
    annotation: Option[Annotation] = None)
}

@RunWith(classOf[JUnitRunner])
abstract class AssertViolationTest extends AnyFunSuite with Matchers {
  import AssertViolationTest._

  protected def validator: ScalaValidator

  protected def assertViolations[T: TypeTag](
    obj: T,
    groups: Seq[Class[_]] = Seq.empty,
    withViolations: Seq[WithViolation[T]] = Seq.empty
  ): Unit = {
    val violations: Set[ConstraintViolation[T]] = validator.validate(obj, groups: _*)
    assertViolations(violations, withViolations)
  }

  protected def assertViolations[T: TypeTag](
    violations: Set[ConstraintViolation[T]],
    withViolations: Seq[WithViolation[T]]
  ): Unit = {
    violations.size should equal(withViolations.size)
    val sortedViolations: Seq[ConstraintViolation[T]] =
      ConstraintViolationHelper.sortViolations(violations)
    for ((constraintViolation, index) <- sortedViolations.zipWithIndex) {
      val withViolation = withViolations(index)
      constraintViolation.getMessage should equal(withViolation.message)
      if (constraintViolation.getPropertyPath == null) withViolation.path should be(null)
      else constraintViolation.getPropertyPath.toString should equal(withViolation.path)
      constraintViolation.getInvalidValue should equal(withViolation.invalidValue)
      constraintViolation.getRootBeanClass should equal(withViolation.rootBeanClazz)
      withViolation.root.getClass.getName == constraintViolation.getRootBean
        .asInstanceOf[T].getClass.getName should be(true)
      constraintViolation.getLeafBean should not be null
      withViolation.annotation.foreach { ann =>
        constraintViolation.getConstraintDescriptor.getAnnotation should equal(ann)
      }
    }
  }
}
