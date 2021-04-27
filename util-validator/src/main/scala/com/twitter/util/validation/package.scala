package com.twitter.util

import jakarta.validation.ConstraintValidator
import java.lang.annotation.Annotation

package object validation {
  type ConstraintValidatorType = ConstraintValidator[_ <: Annotation, _]
  type ConstraintAnnotationClazz = Class[_ <: Annotation]
  type ConstraintValidatorClazz = Class[_ <: ConstraintValidatorType]
}
