package com.twitter.util.validation.internal

import org.hibernate.validator.internal.engine.path.PathImpl

/** Utility class to carry necessary context for validation */
private[validation] case class ValidationContext[T](
  fieldName: Option[String],
  rootClazz: Option[Class[T]],
  root: Option[T],
  leaf: Option[Any],
  path: PathImpl,
  isFailFast: Boolean = false)
