package com.twitter.util.validation.internal.engine

import jakarta.validation.metadata.ConstraintDescriptor
import jakarta.validation.{
  ConstraintValidatorContext,
  ConstraintViolation,
  ConstraintViolationException,
  Payload
}
import java.util.Collections
import org.hibernate.validator.internal.engine.constraintvalidation.{
  ConstraintValidatorContextImpl,
  ConstraintViolationCreationContext
}
import org.hibernate.validator.internal.engine.path.PathImpl
import org.hibernate.validator.internal.engine.{
  MessageInterpolatorContext,
  ValidatorFactoryInspector,
  ConstraintViolationImpl => HibernateConstraintViolationImpl
}
import scala.collection.{SortedSet, mutable}
import scala.jdk.CollectionConverters._

private[validation] object ConstraintViolationFactory {
  private[this] val MessageWithPathTemplate = "%s: %s"

  def sortSet[T](unsortedSet: Set[ConstraintViolation[T]]): Seq[ConstraintViolation[T]] =
    (SortedSet.empty[ConstraintViolation[T]](new ConstraintViolationOrdering) ++ unsortedSet).toSeq

  /* Private */

  def getMessage(violation: ConstraintViolation[_]): String =
    MessageWithPathTemplate.format(violation.getPropertyPath.toString, violation.getMessage)

  private[this] class ConstraintViolationOrdering[T] extends Ordering[ConstraintViolation[T]] {
    override def compare(x: ConstraintViolation[T], y: ConstraintViolation[T]): Int =
      getMessage(x).compare(getMessage(y))
  }
}

/** A wrapper/helper for [[ConstraintViolation]] instances */
private[validation] class ConstraintViolationFactory(validatorFactory: ValidatorFactoryInspector) {
  import ConstraintViolationFactory._

  /** Instantiate a new [[ConstraintViolationException]] from the given Set of violations */
  def newConstraintViolationException(
    violations: Set[ConstraintViolation[Any]]
  ): ConstraintViolationException = {
    val errors: Seq[ConstraintViolation[Any]] = sortSet(violations)

    val message: String =
      "\nValidation Errors:\t\t" + errors
        .map(getMessage).mkString(", ") + "\n\n"
    new ConstraintViolationException(message, errors.toSet.asJava)
  }

  /**
   * Performs message interpolation given the constraint descriptor and constraint validator context
   * to create a set of [[ConstraintViolation]] from the given context and parameters.
   */
  def buildConstraintViolations[T](
    rootClazz: Option[Class[T]],
    root: Option[T],
    leaf: Option[Any],
    path: PathImpl,
    invalidValue: Any,
    constraintDescriptor: ConstraintDescriptor[_],
    constraintValidatorContext: ConstraintValidatorContext
  ): Set[ConstraintViolation[T]] = {
    val results = mutable.HashSet[ConstraintViolation[T]]()
    val constraintViolationCreationContexts =
      constraintValidatorContext
        .asInstanceOf[ConstraintValidatorContextImpl]
        .getConstraintViolationCreationContexts

    var index = 0
    val size = constraintViolationCreationContexts.size()
    while (index < size) {
      val constraintViolationCreationContext = constraintViolationCreationContexts.get(index)
      val messageTemplate = constraintViolationCreationContext.getMessage
      val interpolatedMessage =
        validatorFactory.messageInterpolator
          .interpolate(
            messageTemplate,
            new MessageInterpolatorContext(
              constraintDescriptor,
              invalidValue,
              root.map(_.getClass.asInstanceOf[Class[T]]).orNull,
              constraintViolationCreationContext.getPath,
              constraintViolationCreationContext.getMessageParameters,
              constraintViolationCreationContext.getExpressionVariables,
              constraintViolationCreationContext.getExpressionLanguageFeatureLevel,
              constraintViolationCreationContext.isCustomViolation
            )
          )
      results.add(
        newConstraintViolation[T](
          messageTemplate,
          interpolatedMessage,
          path,
          invalidValue,
          rootClazz.orNull,
          root.map(_.asInstanceOf[T]).getOrElse(null.asInstanceOf[T]),
          leaf.orNull,
          constraintDescriptor,
          constraintViolationCreationContext
        )
      )
      index += 1
    }

    if (results.nonEmpty) results.toSet
    else Set.empty[ConstraintViolation[T]]
  }

  /** Creates a new [[ConstraintViolation]] from the given context and parameters. */
  def newConstraintViolation[T](
    messageTemplate: String,
    interpolatedMessage: String,
    path: PathImpl,
    invalidValue: Any,
    rootClazz: Class[T],
    root: T,
    leaf: Any,
    constraintDescriptor: ConstraintDescriptor[_],
    payload: Payload
  ): ConstraintViolation[T] =
    HibernateConstraintViolationImpl.forBeanValidation[T](
      messageTemplate,
      Collections.emptyMap[String, Object](),
      Collections.emptyMap[String, Object](),
      interpolatedMessage,
      rootClazz,
      root,
      leaf,
      invalidValue,
      path,
      constraintDescriptor,
      payload
    )

  /** Creates a new [[ConstraintViolation]] from the given context and parameters. */
  def newConstraintViolation[T](
    messageTemplate: String,
    interpolatedMessage: String,
    path: PathImpl,
    invalidValue: Any,
    rootClazz: Class[T],
    root: T,
    leaf: Any,
    constraintDescriptor: ConstraintDescriptor[_],
    constraintViolationCreationContext: ConstraintViolationCreationContext
  ): ConstraintViolation[T] =
    HibernateConstraintViolationImpl.forBeanValidation[T](
      messageTemplate,
      constraintViolationCreationContext.getMessageParameters,
      constraintViolationCreationContext.getExpressionVariables,
      interpolatedMessage,
      rootClazz,
      root,
      leaf,
      invalidValue,
      path,
      constraintDescriptor,
      constraintViolationCreationContext.getDynamicPayload
    )
}
