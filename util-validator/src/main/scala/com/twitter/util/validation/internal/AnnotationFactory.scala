package com.twitter.util.validation.internal

import org.hibernate.validator.internal.util.annotation.AnnotationDescriptor

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.util

private[validation] object AnnotationFactory {

  /**
   * Creates a new Annotation (AnnotationProxy) instance of
   * the given annotation type with the custom values applied.
   *
   */
  def newInstance[A <: Annotation](
    annotationType: Class[A],
    customValues: Map[String, Any] = Map.empty
  ): A = {
    val attributes: util.Map[String, Object] = new util.HashMap[String, Object]()
    // Extract default values from annotation
    val methods: Array[Method] = annotationType.getDeclaredMethods
    var index = 0
    val length = methods.length
    while (index < length) {
      val method = methods(index)
      attributes.put(method.getName, method.getDefaultValue)
      index += 1
    }
    // Populate custom values
    val keysIterator = customValues.keysIterator
    while (keysIterator.hasNext) {
      val key = keysIterator.next()
      attributes.put(key, customValues(key).asInstanceOf[AnyRef])
    }
    annotationForMap(annotationType, attributes).asInstanceOf[A]
  }

  private def annotationForMap[A <: Annotation](
    annotationClass: Class[A],
    memberValues: util.Map[String, AnyRef]
  ): Annotation = {
    val ad: AnnotationDescriptor[A] =
      new AnnotationDescriptor.Builder[A](annotationClass, memberValues).build()
    org.hibernate.validator.internal.util.annotation.AnnotationFactory
      .create(ad)
  }
}
