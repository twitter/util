/*
 * Copyright 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import java.io.Serializable
import scala.collection.mutable
import scala.language.implicitConversions

/**
 * You can import Config._ if you want the auto-conversions in a class
 * that does not inherit from trait Config.
 */
@deprecated("use a Plain Old Scala Object", "")
object Config {
  sealed trait Required[+A] extends Serializable {
    def value: A
    def isSpecified: Boolean
    def isEmpty: Boolean = !isSpecified
  }

  object Specified {
    def unapply[A](specified: Specified[A]): Some[A] = Some(specified.value)
  }

  class Specified[+A](f: => A) extends Required[A] {
    lazy val value: A = f
    def isSpecified: Boolean = true
  }

  case object Unspecified extends Required[scala.Nothing] {
    def value: scala.Nothing = throw new NoSuchElementException
    def isSpecified: Boolean = false
  }

  class RequiredValuesMissing(names: Seq[String]) extends Exception(names.mkString(","))

  /**
   * Config classes that don't produce anything via an apply() method
   * can extends Config.Nothing, and still get access to the Required
   * classes and methods.
   */
  class Nothing extends Config[scala.Nothing] {
    def apply(): scala.Nothing = throw new UnsupportedOperationException
  }

  implicit def toSpecified[A](value: => A): Specified[A] = new Specified(value)
  implicit def toSpecifiedOption[A](value: => A): Specified[Some[A]] =
    new Specified(Some(value))
  implicit def fromRequired[A](req: Required[A]): A = req.value
  implicit def intoOption[A](item: A): Option[A] = Some(item)
  implicit def intoList[A](item: A): List[A] = List(item)
}

/**
 * A config object contains vars for configuring an object of type T, and an apply() method
 * which turns this config into an object of type T.
 *
 * The trait also contains a few useful implicits.
 *
 * You can define fields that are required but that don't have a default value with:
 *     class BotConfig extends Config[Bot] {
 *       var botName = required[String]
 *       var botPort = required[Int]
 *       ....
 *     }
 *
 * Optional fields can be defined with:
 *    var something = optional[Duration]
 *
 * Fields that are dependent on other fields and have a default value computed
 * from an expression should be marked as computed:
 *
 *    var level = required[Int]
 *    var nextLevel = computed { level + 1 }
 */
@deprecated("use a Plain Old Scala Object", "")
trait Config[T] extends (() => T) {
  import Config.{Required, Specified, Unspecified, RequiredValuesMissing}

  /**
   * a lazy singleton of the configured class. Allows one to pass
   * the Config around to multiple classes without producing multiple
   * configured instances
   */
  lazy val memoized: T = apply()

  def required[A]: Required[A] = Unspecified
  def required[A](default: => A): Required[A] = new Specified(default)
  def optional[A]: Required[Option[A]] = new Specified(None)
  def optional[A](default: => A): Required[Option[A]] = new Specified(Some(default))

  /**
   * The same as specifying required[A] with a default value, but the intent to the
   * use is slightly different.
   */
  def computed[A](f: => A): Required[A] = new Specified(f)

  /** a non-lazy way to create a Specified that can be called from java */
  def specified[A](value: A): Specified[A] = new Specified(value)

  implicit def toSpecified[A](value: => A): Specified[A] = new Specified(value)
  implicit def toSpecifiedOption[A](value: => A): Specified[Some[A]] =
    new Specified(Some(value))
  implicit def fromRequired[A](req: Required[A]): A = req.value
  implicit def intoOption[A](item: A): Option[A] = Some(item)
  implicit def intoList[A](item: A): List[A] = List(item)

  /**
   * Looks for any fields that are Required but currently Unspecified, in
   * this object and in any child Config objects, and returns the names
   * of those missing values.  For missing values in child Config objects,
   * the name is the dot-separated path down to that value.
   *
   * @note as of Scala 2.12 the returned values are not always correct.
   *       See `ConfigTest` for an example.
   */
  def missingValues: Seq[String] = {
    val alreadyVisited = mutable.Set[AnyRef]()
    val interestingReturnTypes = Seq(classOf[Required[_]], classOf[Config[_]], classOf[Option[_]])
    val buf = new mutable.ListBuffer[String]
    val mirror = scala.reflect.runtime.universe.runtimeMirror(getClass.getClassLoader)
    def collect(prefix: String, config: Config[_]): Unit = {
      if (!alreadyVisited.contains(config)) {
        alreadyVisited += config
        val nullaryMethods = config.getClass.getMethods.toSeq filter { _.getParameterTypes.isEmpty }
        val syntheticTermNames: Set[String] = {
          val configSymbol = mirror.reflect(config).symbol
          val configMembers = configSymbol.toType.members
          configMembers.collect {
            case symbol if symbol.isTerm && symbol.isSynthetic =>
              symbol.name.decodedName.toString
          }(scala.collection.breakOut)
        }
        for (method <- nullaryMethods) {
          val name = method.getName
          val rt = method.getReturnType
          if (name != "required" &&
            name != "optional" &&
            !syntheticTermNames.contains(name) && // no loops, no compiler generated methods!
            interestingReturnTypes.exists(_.isAssignableFrom(rt))) {
            method.invoke(config) match {
              case Unspecified =>
                buf += (prefix + name)
              case specified: Specified[_] =>
                specified match {
                  case Specified(sub: Config[_]) =>
                    collect(prefix + name + ".", sub)
                  case Specified(Some(sub: Config[_])) =>
                    collect(prefix + name + ".", sub)
                  case _ =>
                }
              case sub: Config[_] =>
                collect(prefix + name + ".", sub)
              case Some(sub: Config[_]) =>
                collect(prefix + name + ".", sub)
              case _ =>
            }
          }
        }
      }
    }
    collect("", this)
    buf.toList
  }

  /**
   * @throws RequiredValuesMissing if there are any missing values.
   */
  def validate(): Unit = {
    val missing = missingValues
    if (missing.nonEmpty) throw new RequiredValuesMissing(missing)
  }
}
