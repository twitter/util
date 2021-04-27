package com.twitter.util.validation.metadata

import java.lang.annotation.Annotation
import org.json4s.reflect.ScalaType

case class PropertyDescriptor private[validation] (
  scalaType: ScalaType,
  cascadedScalaType: Option[ScalaType],
  annotations: Array[Annotation],
  isCascaded: Boolean = false)
    extends Descriptor
