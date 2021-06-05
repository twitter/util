package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.{ArrayType, TypeBindings, TypeFactory}
import org.json4s.reflect.ScalaType

private[twitter] object Types {

  /**
   * Primitive class to Java wrapper (boxed) primitive class.
   * Similar to [[com.fasterxml.jackson.databind.util.ClassUtil.wrapperType(Class<?> primitiveType)]]
   * but accepts non primitives as a pass-thru.
   */
  def wrapperType(clazz: Class[_]): Class[_] = {
    if (clazz == null)
      classOf[Null]
    else
      clazz match {
        case java.lang.Byte.TYPE => classOf[java.lang.Byte]
        case java.lang.Short.TYPE => classOf[java.lang.Short]
        case java.lang.Character.TYPE => classOf[java.lang.Character]
        case java.lang.Integer.TYPE => classOf[java.lang.Integer]
        case java.lang.Long.TYPE => classOf[java.lang.Long]
        case java.lang.Float.TYPE => classOf[java.lang.Float]
        case java.lang.Double.TYPE => classOf[java.lang.Double]
        case java.lang.Boolean.TYPE => classOf[java.lang.Boolean]
        case java.lang.Void.TYPE => classOf[java.lang.Void]
        case _ => clazz
      }
  }

  /**
   * Convert a [[org.json4s.reflect.ScalaType]] to a [[com.fasterxml.jackson.databind.JavaType]].
   * This is used when defining the structure of a case class used in parsing JSON.
   *
   * @note order matters since Maps can be treated as collections of tuples
   */
  def javaType(
    typeFactory: TypeFactory,
    scalaType: ScalaType
  ): JavaType = {
    // for backwards compatibility we box primitive types
    val erasedClazzType = toScalaBoxedType(scalaType.erasure)

    if (scalaType.typeArgs.isEmpty || scalaType.erasure.isEnum) {
      typeFactory.constructType(erasedClazzType)
    } else if (scalaType.isCollection) {
      if (scalaType.isMap) {
        typeFactory.constructMapLikeType(
          erasedClazzType,
          javaType(typeFactory, scalaType.typeArgs.head),
          javaType(typeFactory, scalaType.typeArgs(1))
        )
      } else if (scalaType.isArray) {
        // need to special-case array creation
        ArrayType.construct(
          javaType(typeFactory, scalaType.typeArgs.head),
          TypeBindings
            .create(
              classOf[
                java.util.ArrayList[_]
              ], // we hardcode the type to `java.util.ArrayList` to properly support Array creation
              javaType(typeFactory, scalaType.typeArgs.head)
            )
        )
      } else {
        typeFactory.constructCollectionLikeType(
          erasedClazzType,
          javaType(typeFactory, scalaType.typeArgs.head)
        )
      }
    } else {
      typeFactory.constructParametricType(
        erasedClazzType,
        javaTypes(typeFactory, scalaType.typeArgs): _*
      )
    }
  }

  /**
   * Convert a [[org.json4s.reflect.ScalaType]] to a [[com.fasterxml.jackson.databind.JavaType]]
   * taking into account any parameterized types and type bindings. This is used when parsing
   * JSON into a type.
   *
   * @note Match order matters since Maps can be treated as collections of tuples.
   */
  def javaType(
    typeFactory: TypeFactory,
    scalaType: ScalaType,
    typeParameters: Array[JavaType]
  ): JavaType = {

    if (scalaType.typeArgs.isEmpty || scalaType.erasure.isEnum) {
      typeParameters.head
    } else if (scalaType.isCollection) {
      if (scalaType.isMap) {
        typeFactory.constructMapLikeType(
          scalaType.erasure,
          typeParameters.head,
          typeParameters.last
        )
      } else if (scalaType.isArray) {
        // need to special-case array creation
        // we hardcode the type to `java.util.ArrayList` to properly support Array creation
        ArrayType.construct(
          javaType(typeFactory, scalaType.typeArgs.head, typeParameters),
          TypeBindings.create(classOf[java.util.ArrayList[_]], typeParameters.head)
        )
      } else {
        typeFactory.constructCollectionLikeType(
          scalaType.erasure,
          typeParameters.head
        )
      }
    } else {
      typeFactory.constructParametricType(scalaType.erasure, typeParameters: _*)
    }
  }

  /* Private */

  /* Primitive class to Scala wrapper (boxed) primitive class */
  private[this] def toScalaBoxedType(clazz: Class[_]): Class[_] = clazz match {
    case java.lang.Byte.TYPE => classOf[Byte]
    case java.lang.Short.TYPE => classOf[Short]
    case java.lang.Character.TYPE => classOf[Char]
    case java.lang.Integer.TYPE => classOf[Int]
    case java.lang.Long.TYPE => classOf[Long]
    case java.lang.Float.TYPE => classOf[Float]
    case java.lang.Double.TYPE => classOf[Double]
    case java.lang.Boolean.TYPE => classOf[Boolean]
    case _ => clazz
  }

  /* Create a Seq of JavaTypes from the given ScalaTypes */
  private[this] def javaTypes(
    typeFactory: TypeFactory,
    scalaTypes: Seq[ScalaType]
  ): Seq[JavaType] = {
    for (scalaType <- scalaTypes) yield {
      javaType(typeFactory, scalaType)
    }
  }
}
