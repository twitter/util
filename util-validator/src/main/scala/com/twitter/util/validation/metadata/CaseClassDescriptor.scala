package com.twitter.util.validation.metadata

import java.lang.annotation.Annotation
import org.json4s.reflect.ScalaType

case class CaseClassDescriptor[T] private[validation] (
  clazz: Class[T],
  scalaType: ScalaType,
  annotations: Array[Annotation],
  constructors: Array[ConstructorDescriptor],
  members: Map[String, PropertyDescriptor],
  methods: Array[MethodDescriptor])
    extends Descriptor
