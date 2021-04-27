package com.twitter.util.validation.metadata

import java.lang.annotation.Annotation
import java.lang.reflect.Method

case class MethodDescriptor private[validation] (
  method: Method,
  annotations: Array[Annotation],
  members: Map[String, PropertyDescriptor])
    extends ExecutableDescriptor(method)
