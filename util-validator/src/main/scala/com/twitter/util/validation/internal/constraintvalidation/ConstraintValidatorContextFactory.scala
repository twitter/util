package com.twitter.util.validation.internal.constraintvalidation

import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.metadata.ConstraintDescriptor
import org.hibernate.validator.internal.engine.ValidatorFactoryInspector
import org.hibernate.validator.internal.engine.constraintvalidation.{
  ConstraintValidatorContextImpl => HibernateConstraintValidatorContextImpl
}
import org.hibernate.validator.internal.engine.path.PathImpl

private[validation] class ConstraintValidatorContextFactory(
  validatorFactory: ValidatorFactoryInspector) {

  def newConstraintValidatorContext(
    path: PathImpl,
    constraintDescriptor: ConstraintDescriptor[_]
  ): ConstraintValidatorContext =
    new HibernateConstraintValidatorContextImpl(
      validatorFactory.validatorFactoryScopedContext.getClockProvider,
      path,
      constraintDescriptor,
      validatorFactory.validatorFactoryScopedContext.getConstraintValidatorPayload,
      validatorFactory.validatorFactoryScopedContext.getConstraintExpressionLanguageFeatureLevel,
      validatorFactory.validatorFactoryScopedContext.getCustomViolationExpressionLanguageFeatureLevel
    )
}
