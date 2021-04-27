package com.twitter.util.validation.internal.validators

import com.twitter.util.validation.constraints.{StateConstraint, StateConstraintPayload}
import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext

class StateConstraintValidator extends ConstraintValidator[StateConstraint, String] {

  override def isValid(
    obj: String,
    constraintValidatorContext: ConstraintValidatorContext
  ): Boolean = {
    val valid = obj.equalsIgnoreCase("CA")

    if (!valid) {
      val hibernateContext: HibernateConstraintValidatorContext =
        constraintValidatorContext.unwrap(classOf[HibernateConstraintValidatorContext])
      hibernateContext.withDynamicPayload(StateConstraintPayload(obj, "CA"))
    }

    valid
  }
}
