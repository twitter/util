package com.twitter.util.validation.cfg

import jakarta.validation.ConstraintValidator
import java.lang.annotation.Annotation

/**
 * Simple configuration class for defining constraint mappings for ScalaValidator configuration.
 * ==Usage==
 * {{{
 *   val customConstraintMapping: ConstraintMapping =
 *     ConstraintMapping(classOf[Annotation], classOf[ConstraintValidator])
 *
 *   val validator: ScalaValidator =
 *     ScalaValidator.builder
 *       .withConstraintMapping(customConstraintMapping)
 *       .validator
 * }}}
 *
 * or multiple mappings
 *
 * {{{
 *   val customConstraintMapping1: ConstraintMapping = ???
 *   val customConstraintMapping2: ConstraintMapping = ???
 *
 *   val validator: ScalaValidator =
 *     ScalaValidator.builder
 *       .withConstraintMappings(Set(customConstraintMapping1, customConstraintMapping2))
 *       .validator
 * }}}
 *
 * @param annotationType the `Class[Annotation]` of the constraint annotation.
 * @param constraintValidator the implementing ConstraintValidator class for the given constraint annotation.
 * @param includeExistingValidators if this is an additional validator for the given constraint annotation type
 *                                  or if this should replace all existing validators for the given constraint annotation
 *                                  type. Default is true (additional).
 * @note adding multiple constraint mappings for the same annotation type will result in a ValidationException being thrown.
 */
case class ConstraintMapping(
  annotationType: Class[_ <: Annotation],
  constraintValidator: Class[_ <: ConstraintValidator[_ <: Annotation, _]],
  includeExistingValidators: Boolean = true)
