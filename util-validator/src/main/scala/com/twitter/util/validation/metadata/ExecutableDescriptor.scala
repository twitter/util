package com.twitter.util.validation.metadata

import java.lang.annotation.Annotation
import java.lang.reflect.Executable

abstract class ExecutableDescriptor private[validation] (val executable: Executable)
    extends Descriptor {
  def annotations: Array[Annotation]
  def members: Map[String, PropertyDescriptor]
}
