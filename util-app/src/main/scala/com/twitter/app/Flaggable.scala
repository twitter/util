package com.twitter.app

import com.twitter.util.{Duration, StorageUnit, Time, TimeFormat}
import java.io.File
import java.lang.{
  Boolean => JBoolean,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong
}
import java.net.InetSocketAddress
import java.util.{List => JList, Map => JMap, Set => JSet}
import scala.jdk.CollectionConverters._

/**
 * A type class providing evidence for parsing type `T` as a flag value.
 *
 * Any class that is to be provided as a flaggable value must have an
 * accompanying implicit `Flaggable` (contained within a companion object of the
 * class in question) for converting a string to an object of that type. For
 * instance, to make a hypothetical type called `Foo` flaggable:
 *
 * {{{
 * class Foo {
 *   ...
 * }
 *
 * object Foo {
 *   implicit val flagOfFoo = new Flaggable[Foo] {
 *     def parse(v: String): Foo = {
 *       ...
 *     }
 *   }
 * }
 * }}}
 *
 * For simple implicit definitions based on existing `String => T` functions,
 * use the `Flaggable.mandatory` function:
 *
 * {{{
 * object Foo {
 *   def parse(v: String): Foo = {
 *      ...
 *   }
 *
 *   implicit val ofFoo = Flaggable.mandatory(Foo.parse(_))
 * }
 * }}}
 *
 * [1] https://en.wikipedia.org/wiki/Type_class
 */
abstract class Flaggable[T] {

  /**
   * Parse a string (i.e. a value set on the command line) into an object of
   * type `T`.
   */
  def parse(s: String): T

  /**
   * Create a string-representation of an object of type `T`. Used in
   * `Flag.toString`.
   */
  def show(t: T): String = t.toString

  /**
   * An optional default value for the Flaggable.
   */
  def default: Option[T] = None
}

/**
 * Default `Flaggable` implementations.
 */
object Flaggable {

  /**
   * Create a `Flaggable[T]` according to a `String => T` conversion function.
   *
   * @param f Function that parses a string into an object of type `T`
   */
  def mandatory[T](f: String => T): Flaggable[T] = new Flaggable[T] {
    def parse(s: String): T = f(s)
  }

  implicit val ofString: Flaggable[String] = mandatory(identity)

  // Pairs of Flaggable conversions for types with corresponding Java boxed types.
  implicit val ofBoolean: Flaggable[Boolean] = new Flaggable[Boolean] {
    override def default: Option[Boolean] = Some(true)
    def parse(s: String): Boolean = s.toBoolean
  }

  implicit val ofJavaBoolean: Flaggable[JBoolean] = new Flaggable[JBoolean] {
    override def default: Option[JBoolean] = Some(JBoolean.TRUE)
    def parse(s: String): JBoolean = JBoolean.valueOf(s.toBoolean)
  }

  implicit val ofInt: Flaggable[Int] = mandatory(_.toInt)
  implicit val ofJavaInteger: Flaggable[JInteger] =
    mandatory { s: String => JInteger.valueOf(s.toInt) }

  implicit val ofLong: Flaggable[Long] = mandatory(_.toLong)
  implicit val ofJavaLong: Flaggable[JLong] =
    mandatory { s: String => JLong.valueOf(s.toLong) }

  implicit val ofFloat: Flaggable[Float] = mandatory(_.toFloat)
  implicit val ofJavaFloat: Flaggable[JFloat] =
    mandatory { s: String => JFloat.valueOf(s.toFloat) }

  implicit val ofDouble: Flaggable[Double] = mandatory(_.toDouble)
  implicit val ofJavaDouble: Flaggable[JDouble] =
    mandatory { s: String => JDouble.valueOf(s.toDouble) }

  // Conversions for common non-primitive types and collections.
  implicit val ofDuration: Flaggable[Duration] = mandatory(Duration.parse(_))
  implicit val ofStorageUnit: Flaggable[StorageUnit] = mandatory(StorageUnit.parse(_))

  private val defaultTimeFormat = new TimeFormat("yyyy-MM-dd HH:mm:ss Z")
  implicit val ofTime: Flaggable[Time] = mandatory(defaultTimeFormat.parse(_))

  implicit val ofInetSocketAddress: Flaggable[InetSocketAddress] =
    new Flaggable[InetSocketAddress] {
      def parse(v: String): InetSocketAddress = v.split(":") match {
        case Array("", p) =>
          new InetSocketAddress(p.toInt)
        case Array(h, p) =>
          new InetSocketAddress(h, p.toInt)
        case _ =>
          throw new IllegalArgumentException
      }

      override def show(addr: InetSocketAddress): String =
        "%s:%d".format(
          Option(addr.getAddress) match {
            case Some(a) if a.isAnyLocalAddress => ""
            case _ => addr.getHostName
          },
          addr.getPort)
    }

  implicit val ofFile: Flaggable[File] = new Flaggable[File] {
    override def parse(v: String): File = new File(v)
    override def show(file: File): String = file.toString
  }

  implicit def ofTuple[T: Flaggable, U: Flaggable]: Flaggable[(T, U)] = new Flaggable[(T, U)] {
    private val tflag = implicitly[Flaggable[T]]
    private val uflag = implicitly[Flaggable[U]]

    assert(tflag.default.isEmpty)
    assert(uflag.default.isEmpty)

    def parse(v: String): (T, U) = v.split(",") match {
      case Array(t, u) => (tflag.parse(t), uflag.parse(u))
      case _ => throw new IllegalArgumentException("not a 't,u'")
    }

    override def show(tup: (T, U)): String = {
      val (t, u) = tup
      tflag.show(t) + "," + uflag.show(u)
    }
  }

  private[app] class SetFlaggable[T: Flaggable] extends Flaggable[Set[T]] {
    private val flag = implicitly[Flaggable[T]]
    assert(flag.default.isEmpty)
    override def parse(v: String): Set[T] = v.split(",").iterator.map(flag.parse(_)).toSet
    override def show(set: Set[T]): String = set.map(flag.show).mkString(",")
  }

  private[app] class SeqFlaggable[T: Flaggable] extends Flaggable[Seq[T]] {
    private val flag = implicitly[Flaggable[T]]
    assert(flag.default.isEmpty)
    def parse(v: String): Seq[T] = v.split(",").toSeq.map(flag.parse)
    override def show(seq: Seq[T]): String = seq.map(flag.show).mkString(",")
  }

  private[app] class MapFlaggable[K: Flaggable, V: Flaggable] extends Flaggable[Map[K, V]] {
    private val kflag = implicitly[Flaggable[K]]
    private val vflag = implicitly[Flaggable[V]]

    assert(kflag.default.isEmpty)
    assert(vflag.default.isEmpty)

    def parse(in: String): Map[K, V] = {
      val tuples = in.split(',').foldLeft(Seq.empty[String]) {
        case (acc, s) if !s.contains('=') =>
          // In order to support comma-separated values, we concatenate
          // consecutive tokens that don't contain equals signs.
          acc.init :+ (acc.last + ',' + s)
        case (acc, s) => acc :+ s
      }

      tuples.map { tup =>
        tup.split("=") match {
          case Array(k, v) => (kflag.parse(k), vflag.parse(v))
          case _ => throw new IllegalArgumentException("not a 'k=v'")
        }
      }.toMap
    }

    override def show(out: Map[K, V]): String = {
      out.toSeq.map { case (k, v) => k.toString + "=" + v.toString }.mkString(",")
    }
  }

  implicit def ofSet[T: Flaggable]: Flaggable[Set[T]] = new SetFlaggable[T]
  implicit def ofSeq[T: Flaggable]: Flaggable[Seq[T]] = new SeqFlaggable[T]
  implicit def ofMap[K: Flaggable, V: Flaggable]: Flaggable[Map[K, V]] = new MapFlaggable[K, V]

  implicit def ofJavaSet[T: Flaggable]: Flaggable[JSet[T]] = new Flaggable[JSet[T]] {
    val setFlaggable = new SetFlaggable[T]
    override def parse(v: String): JSet[T] = setFlaggable.parse(v).asJava
    override def show(set: JSet[T]): String = setFlaggable.show(set.asScala.toSet)
  }

  implicit def ofJavaList[T: Flaggable]: Flaggable[JList[T]] = new Flaggable[JList[T]] {
    val seqFlaggable = new SeqFlaggable[T]
    override def parse(v: String): JList[T] = seqFlaggable.parse(v).asJava
    override def show(list: JList[T]): String = seqFlaggable.show(list.asScala.toSeq)
  }

  implicit def ofJavaMap[K: Flaggable, V: Flaggable]: Flaggable[JMap[K, V]] = {
    val mapFlaggable = new MapFlaggable[K, V]

    new Flaggable[JMap[K, V]] {
      def parse(in: String): JMap[K, V] = mapFlaggable.parse(in).asJava
      override def show(out: JMap[K, V]): String = mapFlaggable.show(out.asScala.toMap)
    }
  }
}
