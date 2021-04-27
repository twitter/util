package com.twitter.util.validation.constraintvalidation

import jakarta.validation.{ConstraintValidatorContext, Payload, ValidationException}
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext

object TwitterConstraintValidatorContext {

  def builder: TwitterConstraintValidatorContext.Builder =
    new Builder(None, None, Map.empty[String, AnyRef], Map.empty[String, AnyRef])

  /**
   * Simple utility to build a non-default [[jakarta.validation.ConstraintViolation]]
   * from a [[jakarta.validation.ConstraintValidatorContext]].
   */
  class Builder private[validation] (
    messageTemplate: Option[String],
    payload: Option[Payload],
    messageParameters: Map[String, AnyRef],
    expressionVariables: Map[String, AnyRef]) {

    /**
     * Specify a new un-interpolated constraint message.
     *
     * @param messageTemplate an un-interpolated constraint message.
     *
     * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
     */
    def withMessageTemplate(messageTemplate: String): Builder =
      new Builder(
        messageTemplate = Some(messageTemplate),
        payload = this.payload,
        messageParameters = this.messageParameters,
        expressionVariables = this.expressionVariables)

    /**
     * Allows to set an object that may further describe the violation. The user is responsible to
     * ensure that this payload is serializable in case the [[jakarta.validation.ConstraintViolation]]
     * has to be serialized.
     *
     * @param payload payload an object representing additional information about the violation
     *
     * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
     */
    def withDynamicPayload(payload: Payload): Builder =
      new Builder(
        messageTemplate = this.messageTemplate,
        payload = Some(payload),
        messageParameters = this.messageParameters,
        expressionVariables = this.expressionVariables)

    /**
     * Allows setting of an additional named parameter which can be interpolated in the constraint violation message. The
     * variable will be available for interpolation for all constraint violations generated for this constraint.
     * This includes the default one as well as all violations created by this TwitterConstraintValidatorContext.
     * To create multiple constraint violations with different variable values, this method can be called
     * between successive calls to [[TwitterConstraintValidatorContext#addConstraintViolation]].
     *
     * @param name the name under which to bind the parameter, cannot be `null`.
     * @param value the value to be bound to the specified name
     *
     * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
     */
    def addMessageParameter(name: String, value: AnyRef): Builder =
      new Builder(
        messageTemplate = this.messageTemplate,
        payload = this.payload,
        messageParameters = this.messageParameters + (name -> value),
        expressionVariables = this.expressionVariables
      )

    /**
     * Allows setting of an additional expression variable which will be available as an EL variable during interpolation. The
     * variable will be available for interpolation for all constraint violations generated for this constraint.
     * This includes the default one as well as all violations created by this TwitterConstraintValidatorContext.
     * To create multiple constraint violations with different variable values, this method can be called
     * between successive calls to [[TwitterConstraintValidatorContext#addConstraintViolation]].
     *
     * @param name the name under which to bind the expression variable, cannot be `null`.
     * @param value the value to be bound to the specified name
     *
     * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
     */
    def addExpressionVariable(name: String, value: AnyRef): Builder =
      new Builder(
        messageTemplate = this.messageTemplate,
        payload = this.payload,
        messageParameters = this.messageParameters,
        expressionVariables = this.expressionVariables + (name -> value)
      )

    /**
     * Replaces the default constraint violation report with one built via this method using the
     * given [[TwitterConstraintValidatorContext]].
     *
     * @param context the [[TwitterConstraintValidatorContext]] to use in building a [[jakarta.validation.ConstraintViolation]]
     *
     * @throws ValidationException - if the given [[TwitterConstraintValidatorContext]] cannot be unwrapped as expected.
     */
    @throws[ValidationException]
    def addConstraintViolation(context: ConstraintValidatorContext): Unit = {
      if (context != null) {
        val hibernateContext: HibernateConstraintValidatorContext =
          context.unwrap(classOf[HibernateConstraintValidatorContext])
        // custom payload
        payload.foreach(hibernateContext.withDynamicPayload)
        // set message parameters
        messageParameters.foreach {
          case (name, value) =>
            hibernateContext.addMessageParameter(name, value)
        }
        // set expression variables
        expressionVariables.foreach {
          case (name, value) =>
            hibernateContext.addExpressionVariable(name, value)
        }
        // custom message
        messageTemplate.foreach { template =>
          hibernateContext.disableDefaultConstraintViolation()

          if (expressionVariables.nonEmpty) {
            hibernateContext
              .buildConstraintViolationWithTemplate(template)
              .enableExpressionLanguage
              .addConstraintViolation()
          } else {
            hibernateContext
              .buildConstraintViolationWithTemplate(template)
              .addConstraintViolation()
          }
        }
      }
    }
  }

  /**
   * Specify a new un-interpolated constraint message.
   *
   * @param messageTemplate an un-interpolated constraint message.
   *
   * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
   */
  def withMessageTemplate(messageTemplate: String): TwitterConstraintValidatorContext.Builder =
    builder.withMessageTemplate(messageTemplate)

  /**
   * Allows to set an object that may further describe the violation. The user is responsible to
   * ensure that this payload is serializable in case the [[jakarta.validation.ConstraintViolation]]
   * has to be serialized.
   *
   * @param payload payload an object representing additional information about the violation
   *
   * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
   */
  def withDynamicPayload(payload: Payload): TwitterConstraintValidatorContext.Builder =
    builder.withDynamicPayload(payload)

  /**
   * Allows setting of an additional named parameter which can be interpolated in the constraint violation message. The
   * variable will be available for interpolation for all constraint violations generated for this constraint.
   * This includes the default one as well as all violations created by this TwitterConstraintValidatorContext.
   * To create multiple constraint violations with different variable values, this method can be called
   * between successive calls to [[TwitterConstraintValidatorContext#addConstraintViolation]]. E.g.,
   * within a ConstraintValidator instance:
   * {{{
   *   def isValid(value: String, constraintValidatorContext: TwitterConstraintValidatorContext): Boolean = {
   *
   *     TwitterConstraintValidatorContext
   *       .addMessageParameter("foo", "bar")
   *       .withMessageTemplate("{foo}")
   *       .addConstraintViolation(context)
   *
   *     TwitterConstraintValidatorContext
   *       .addMessageParameter("foo", "snafu")
   *       .withMessageTemplate("{foo}")
   *       .addConstraintViolation(context)
   *
   *    false
   * }}}
   *
   * @param name the name under which to bind the parameter, cannot be `null`.
   * @param value the value to be bound to the specified name
   *
   * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
   */
  def addMessageParameter(name: String, value: AnyRef): TwitterConstraintValidatorContext.Builder =
    builder.addMessageParameter(name, value)

  /**
   * Allows setting of an additional expression variable which will be available as an EL variable during interpolation. The
   * variable will be available for interpolation for all constraint violations generated for this constraint.
   * This includes the default one as well as all violations created by this TwitterConstraintValidatorContext.
   * To create multiple constraint violations with different variable values, this method can be called
   * between successive calls to [[TwitterConstraintValidatorContext#addConstraintViolation]]. E.g.,
   * within a ConstraintValidator instance:
   * {{{
   *   def isValid(value: String, constraintValidatorContext: TwitterConstraintValidatorContext): Boolean = {
   *
   *     TwitterConstraintValidatorContext
   *       .addExpressionVariable("foo", "bar")
   *       .withMessageTemplate("${foo}")
   *       .addConstraintViolation(context)
   *
   *     TwitterConstraintValidatorContext
   *       .addExpressionVariable("foo", "snafu")
   *       .withMessageTemplate("${foo}")
   *       .addConstraintViolation(context)
   *
   *    false
   * }}}
   *
   * @param name the name under which to bind the expression variable, cannot be `null`.
   * @param value the value to be bound to the specified name
   *
   * @return a [[TwitterConstraintValidatorContext.Builder]] to allow for method chaining.
   */
  def addExpressionVariable(
    name: String,
    value: AnyRef
  ): TwitterConstraintValidatorContext.Builder =
    builder.addExpressionVariable(name, value)
}
