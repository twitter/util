package com.twitter.inject

import com.google.inject.{Key, Injector => UnderlyingInjector}
import java.lang.annotation.Annotation
import net.codingwell.scalaguice.KeyExtensions._
import net.codingwell.scalaguice.typeLiteral
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

final case class Injector private[inject] (underlying: UnderlyingInjector) {

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed type [[T]].
   *
   * When feasible, avoid using this method in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @tparam T type of the bound instance to return from the object graph.
   *
   * @return bound instance of type [[T]].
   */
  def instance[T: TypeTag]: T =
    underlying.getInstance(typeLiteral[T].toKey)

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed type [[T]] and given [[java.lang.annotation.Annotation]] type [[Ann]].
   *
   * When feasible, avoid using this method in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @tparam T type of the bound instance to return from the object graph.
   * @tparam Ann type of the annotation used to disambiguate the bound type [[T]].
   *
   * @return bound instance of type [[T]] annotated with annotation type [[Ann]].
   */
  def instance[T: TypeTag, Ann <: Annotation: ClassTag]: T =
    underlying.getInstance(typeLiteral[T].annotatedWith[Ann])

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed type [[T]] and given [[java.lang.annotation.Annotation]] annotation.
   *
   * When feasible, avoid using this method in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @param annotation [[java.lang.annotation.Annotation]] instance used to
   *                    disambiguate the bound type [[T]].
   * @tparam T type of the bound instance to return from the object graph.
   *
   * @return bound instance of type [[T]] annotated with annotation.
   */
  def instance[T: TypeTag](annotation: Annotation): T =
    underlying.getInstance(typeLiteral[T].annotatedWith(annotation))

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed type [[T]] and given [[java.lang.annotation.Annotation]] class.
   *
   * When feasible, avoid using this method in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @param annotationClazz class of [[java.lang.annotation.Annotation]] used
   *                        to disambiguate the bound type [[T]].
   * @tparam T type of the bound instance to return from the object graph.
   * @return bound instance of type [[T]] annotated with annotation class.
   */
  def instance[T: TypeTag](annotationClazz: Class[_ <: Annotation]): T =
    underlying.getInstance(typeLiteral[T].annotatedWith(annotationClazz))

  /**
   * Returns the appropriate instance for the given injection type.
   *
   * When feasible, avoid using this method, in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @param clazz the class of type [[T]] of the bound instance to return
   *              from the object graph.
   * @tparam T type of the bound instance to return from the object graph.
   *
   * @return bound instance of type [[T]].
   */
  def instance[T](clazz: Class[T]): T = underlying.getInstance(clazz)

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed class and given [[java.lang.annotation.Annotation]] annotation.
   *
   * When feasible, avoid using this method, in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @param clazz the class of type [[T]] of the bound instance to return from
   *              the object graph.
   * @param annotation [[java.lang.annotation.Annotation]] instance used to
   *                  disambiguate the bound type [[T]].
   * @tparam T type of the bound instance to return from the object graph.
   *
   * @return bound instance of type [[T]].
   */
  def instance[T](clazz: Class[T], annotation: Annotation): T =
    underlying.getInstance(Key.get(clazz, annotation))

  /**
   * Returns the appropriate instance for the given key constructed from the
   * passed class and given [[java.lang.annotation.Annotation]] class.
   *
   * @param clazz the class of type [[T]] of the bound instance to return from
   *              the object graph.
   * @param annotationClazz [[java.lang.annotation.Annotation]] class used to
   *                        disambiguate the bound type [[T]].
   * @tparam T type of the bound instance to return from the object graph.
   * @tparam Ann type of the annotation class used to disambiguate the bound type [[T]].
   *
   * @return bound instance of type [[T]].
   */
  def instance[T, Ann <: Annotation](clazz: Class[T], annotationClazz: Class[Ann]): T =
    underlying.getInstance(Key.get(clazz, annotationClazz))

  /**
   * Returns the appropriate instance for the given injection key.
   *
   * When feasible, avoid using this method in favor of having the Injector
   * inject your dependencies ahead of time via annotating your constructor
   * with `@Inject`.
   *
   * @param key [[com.google.inject.Key]] binding key of the bound instance to return
   *              from the object graph.
   * @tparam T type of the bound instance to return from the object graph.
   *
   * @return bound instance of type [[T]] represented by [[com.google.inject.Key]] key.
   *
   * @see [[https://google.github.io/guice/api-docs/latest/javadoc/com/google/inject/Key.html com.google.inject.Key]]
   */
  def instance[T](key: Key[T]): T = underlying.getInstance(key)
}
