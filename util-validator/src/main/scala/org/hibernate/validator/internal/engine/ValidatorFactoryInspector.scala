package org.hibernate.validator.internal.engine

import jakarta.validation.{ConstraintValidatorFactory, MessageInterpolator}
import java.lang.reflect.Field
import org.hibernate.validator.internal.metadata.core.ConstraintHelper
import org.hibernate.validator.internal.util.{ExecutableHelper, ExecutableParameterNameProvider}

/** Allows access to the configured [[ConstraintHelper]] and [[ConstraintValidatorFactory]] */
class ValidatorFactoryInspector(underlying: ValidatorFactoryImpl) {

  def constraintHelper: ConstraintHelper =
    underlying.getConstraintCreationContext.getConstraintHelper

  def constraintValidatorFactory: ConstraintValidatorFactory =
    underlying.getConstraintValidatorFactory

  def validatorFactoryScopedContext: ValidatorFactoryScopedContext =
    underlying.getValidatorFactoryScopedContext

  def messageInterpolator: MessageInterpolator =
    underlying.getMessageInterpolator

  def getConstraintCreationContext: ConstraintCreationContext =
    underlying.getConstraintCreationContext

  def getExecutableHelper: ExecutableHelper = {
    val field: Field = classOf[ValidatorFactoryImpl].getDeclaredField("executableHelper")
    field.setAccessible(true)
    field.get(underlying).asInstanceOf[ExecutableHelper]
  }

  def getExecutableParameterNameProvider: ExecutableParameterNameProvider =
    underlying.getExecutableParameterNameProvider

  def getMethodValidationConfiguration: MethodValidationConfiguration =
    underlying.getMethodValidationConfiguration

  /** Close underlying ValidatorFactoryImpl */
  def close(): Unit =
    underlying.close()
}
