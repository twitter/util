package com.twitter.util.validation.internal

import org.json4s.reflect.Reflector
import org.json4s.reflect.ScalaType

private[validation] object Types {

  /** For certain scala types, return the scala type of the first type arg */
  def getContainedScalaType(
    scalaType: ScalaType
  ): Option[ScalaType] = scalaType match {
    case argType if argType.isMap || argType.isMutableMap =>
      // Maps not supported
      None
    case argType if argType.isCollection || argType.isOption =>
      // handles Option and Iterable collections
      if (argType.typeArgs.isEmpty) Some(Reflector.scalaTypeOf(classOf[Object]))
      else Some(argType.typeArgs.head)
    case _ =>
      Some(scalaType)
  }

  // if the case class is generically typed, the originally parsed scala type
  // may be an object thus we try to refine it based on the given value
  def refineScalaType(value: Any, scalaType: ScalaType): ScalaType = {
    if (scalaType.erasure == classOf[Object] && value != null) {
      if (classOf[Option[_]].isAssignableFrom(value.getClass))
        Reflector.scalaTypeOf(classOf[Option[_]])
      else
        Reflector.scalaTypeOf(value.getClass)
    } else if (scalaType.isOption) {
      if (scalaType.typeArgs.nonEmpty) {
        // When deserializing a case class, we interpret the field
        // type with the field constructor type. For optional
        // field, Jackson returns Option[Object] as the constructor
        // type. However, field validations would require a
        // specific field type in order to run the constraint
        // validator. So we use the field value type as the field
        // type for optional field.
        if (scalaType.typeArgs.head.erasure.isInstanceOf[Object])
          Reflector.scalaTypeOf(value.getClass)
        else scalaType.typeArgs.head
      } else {
        Reflector.scalaTypeOf(classOf[Object])
      }
    } else scalaType
  }

  /** Translate a Scala type to a corresponding Java class */
  def getJavaType(scalaType: ScalaType): Class[_] = scalaType match {
    case argType if argType.isOption =>
      if (argType.typeArgs.nonEmpty) argType.typeArgs.head.erasure
      else classOf[Object]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Byte.TYPE.getSimpleName =>
      classOf[java.lang.Byte]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Short.TYPE.getSimpleName =>
      classOf[java.lang.Short]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Character.TYPE.getSimpleName =>
      classOf[java.lang.Character]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Integer.TYPE.getSimpleName =>
      classOf[java.lang.Integer]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Long.TYPE.getSimpleName =>
      classOf[java.lang.Long]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Float.TYPE.getSimpleName =>
      classOf[java.lang.Float]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Double.TYPE.getSimpleName =>
      classOf[java.lang.Double]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Boolean.TYPE.getSimpleName =>
      classOf[java.lang.Boolean]
    case argType
        if argType.isPrimitive && argType.simpleName == java.lang.Void.TYPE.getSimpleName =>
      classOf[java.lang.Void]
    case argType =>
      argType.erasure
  }
}
