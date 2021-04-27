package com.twitter.util.validation.internal.metadata.descriptor

import com.twitter.util.validation.MethodValidation
import jakarta.validation.metadata.{ConstraintDescriptor, ValidateUnwrappedValue}
import jakarta.validation.{ConstraintTarget, ConstraintValidator, Payload, ValidationException}
import java.util

private[validation] class MethodValidationConstraintDescriptor(annotation: MethodValidation)
    extends ConstraintDescriptor[MethodValidation] {
  override def getAnnotation: MethodValidation = annotation
  override def getMessageTemplate: String = annotation.message()
  override def getGroups: util.Set[Class[_]] = {
    val target = new util.HashSet[Class[_]]
    annotation.groups().foreach(target.add)
    target
  }
  override def getPayload: util.Set[Class[_ <: Payload]] = {
    val target = new util.HashSet[Class[_ <: Payload]]
    annotation.payload().foreach(target.add)
    target
  }
  override def getValidationAppliesTo: ConstraintTarget = ConstraintTarget.PARAMETERS
  override def getConstraintValidatorClasses: util.List[Class[
    _ <: ConstraintValidator[MethodValidation, _]
  ]] = util.Collections.emptyList()
  override def getAttributes: util.Map[String, AnyRef] = {
    val target = new util.HashMap[String, AnyRef]()
    var i = 0
    annotation.fields().foreach { key =>
      target.put(key, Integer.valueOf(i))
      i += 1
    }
    target
  }
  override def getComposingConstraints: util.Set[ConstraintDescriptor[_]] =
    util.Collections.emptySet()
  override def isReportAsSingleViolation: Boolean = true
  override def getValueUnwrapping: ValidateUnwrappedValue = ValidateUnwrappedValue.DEFAULT
  override def unwrap[U](clazz: Class[U]): U = throw new ValidationException(
    s"${clazz.getName} is unsupported")
}
