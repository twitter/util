package com.twitter.util.validation.metadata

import java.lang.annotation.Annotation
import java.lang.reflect.Executable

case class ConstructorDescriptor private[validation] (
  override val executable: Executable,
  annotations: Array[Annotation],
  members: Map[String, PropertyDescriptor])
    extends ExecutableDescriptor(executable)
