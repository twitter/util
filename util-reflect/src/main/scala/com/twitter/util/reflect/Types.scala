package com.twitter.util.reflect

import com.twitter.util.Memoize
import java.lang.reflect.{ParameterizedType, TypeVariable, Type => JavaType}
import scala.reflect.api.TypeCreator
import scala.reflect.runtime.universe._

object Types {
  private[this] val PRODUCT: Type = typeOf[Product]
  private[this] val OPTION: Type = typeOf[Option[_]]
  private[this] val LIST: Type = typeOf[List[_]]

  /**
   * Returns  `true` if the given class type is considered a case class.
   * True if:
   *  - is assignable from PRODUCT and
   *  - not assignable from OPTION nor LIST and
   *  - is not a Tuple and
   *  - class symbol is case class.
   */
  val isCaseClass: Class[_] => Boolean = Memoize { clazz: Class[_] =>
    val tpe = asTypeTag(clazz).tpe
    val classSymbol = tpe.typeSymbol.asClass
    tpe <:< PRODUCT &&
    !(tpe <:< OPTION || tpe <:< LIST) &&
    !clazz.getName.startsWith("scala.Tuple") &&
    classSymbol.isCaseClass
  }

  /**
   * This is the negation of [[Types.isCaseClass]]
   * Determine if a given class type is not a case class.
   * Returns `true` if it is NOT considered a case class.
   */
  def notCaseClass[T](clazz: Class[T]): Boolean = !isCaseClass(clazz)

  /**
   * Convert from the given `Class[T]` to a `TypeTag[T]` in the runtime universe.
   * =Usage=
   * {{{
   *   val clazz: Class[T] = ???
   *   val tag: TypeTag[T] = Types.asTypeTag(clazz)
   * }}}
   *
   * @param clazz the class for which to build the resultant [[TypeTag]]
   * @return a `TypeTag[T]` representing the given `Class[T]`.
   */
  def asTypeTag[T](clazz: Class[_ <: T]): TypeTag[T] = {
    val clazzMirror = runtimeMirror(clazz.getClassLoader)
    val tpe = clazzMirror.classSymbol(clazz).toType
    val typeCreator = new TypeCreator() {
      def apply[U <: scala.reflect.api.Universe with scala.Singleton](
        m: scala.reflect.api.Mirror[U]
      ): U#Type = {
        if (clazzMirror != m) throw new RuntimeException("wrong mirror")
        else tpe.asInstanceOf[U#Type]
      }
    }
    TypeTag[T](clazzMirror, typeCreator)
  }

  /**
   * Return the runtime class from a given [[TypeTag]].
   * =Usage=
   * {{{
   *   val clazz: Class[T] = Types.runtimeClass[T]
   * }}}
   *
   * or pass in the implict TypeTag explicitly:
   *
   * {{{
   *   val tag: TypeTag[T] = ???
   *   val clazz: Class[T] = Types.runtimeClass(tag)
   * }}}
   *
   *
   * @note the given [[TypeTag]] must be from the runtime universe otherwise an
   *       [[IllegalArgumentException]] will be thrown.
   *
   * @tparam T the [[TypeTag]] and expected class type of the returned class.
   * @return the runtime class of the given [[TypeTag]].
   */
  def runtimeClass[T](implicit tag: TypeTag[T]): Class[T] = {
    val clazzMirror = runtimeMirror(getClass.getClassLoader)
    if (clazzMirror != tag.mirror) {
      throw new IllegalArgumentException("TypeTag is not from runtime universe.")
    }
    tag.mirror.runtimeClass(tag.tpe).asInstanceOf[Class[T]]
  }

  /**
   * If `thisTypeTag` [[TypeTag]] has the same TypeSymbol as `thatTypeTag` [[TypeTag]].
   *
   * @param thatTypeTag [[TypeTag]] to compare
   * @param thisTypeTag [[TypeTag]] to compare
   * @tparam T type of thisTypeTag
   * @return true if the TypeSymbols are equivalent.
   * @see [[scala.reflect.api.Symbols]]
   */
  def equals[T](thatTypeTag: TypeTag[_])(implicit thisTypeTag: TypeTag[T]): Boolean =
    thisTypeTag.tpe.typeSymbol == thatTypeTag.tpe.typeSymbol

  /**
   * If the given [[java.lang.reflect.Type]] is parameterized, return an Array of the
   * type parameter names. E.g., `Map[T, U]` returns, `Array("T", "U")`.
   *
   * The use case is when we are trying to match the given type's type parameters to
   * a set of bindings that are stored keyed by the type name, e.g. if the given type
   * is a `Map[K, V]` we want to be able to look up the binding for the key `K` at runtime
   * during reflection operations e.g., if the K type is bound to a String we want to
   * be able use that when further processing this type. This type of operation most typically
   * happens with Jackson reflection handling of case classes, which is a specialized case
   * and thus the utility of this method may not be broadly applicable and therefore is limited
   * in visibility.
   */
  private[twitter] def parameterizedTypeNames(javaType: JavaType): Array[String] =
    javaType match {
      case parameterizedType: ParameterizedType =>
        parameterizedType.getActualTypeArguments.map(_.getTypeName)
      case typeVariable: TypeVariable[_] =>
        Array(typeVariable.getTypeName)
      case clazz: Class[_] =>
        clazz.getTypeParameters.map(_.getName)
      case _ => Array.empty
    }
}
