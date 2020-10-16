package com.twitter.app

import com.twitter.util.{Duration, StorageUnit, Time, TimeFormat}
import java.io.File
import java.lang.reflect.Type
import java.lang.{
  Boolean => JBoolean,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong
}
import java.net.InetSocketAddress
import java.time.LocalTime
import java.util.{List => JList, Map => JMap, Set => JSet}
import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

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
   * Just like [[Flaggable]] but provides the runtime with the information about the type `T`. This
   * can be useful for frameworks leveraging DI for Twitter Flags.
   *
   * @note You should prefer this interface for new Flaggables to allow them participate in DI (if
   *       needed). Eventually this interface may or may not submerge into the parent [[Flaggable]].
   */
  private[twitter] abstract class Typed[T] extends Flaggable[T] {

    /**
     * A raw type for this Flaggable.
     */
    def rawType: Type
  }

  /**
   * Just like [[Flaggable.Typed]] but signals the runtime that this Flaggable type is generic so it
   * takes types as arguments. For example, `List[T]` is such type and it take one type-parameter
   * `T` to construct `List[T]`.
   */
  private[twitter] abstract class Generic[T] extends Typed[T] {

    /**
     * An [[Array]] of Flaggables that map to type-parameters for this generic Flaggable.
     */
    def parameters: Array[Flaggable[_]]
  }

  /**
   * Create a `Flaggable[T]` according to a `String => T` conversion function.
   *
   * @param f Function that parses a string into an object of type `T`
   */
  def mandatory[T](f: String => T)(implicit ct: ClassTag[T]): Flaggable[T] = new Typed[T] {
    def parse(s: String): T = f(s)
    def rawType: Type = ct.runtimeClass
  }

  implicit val ofString: Flaggable[String] = mandatory(identity)

  implicit val ofBoolean: Flaggable[Boolean] = new Typed[Boolean] {
    override def default: Option[Boolean] = Some(true)
    def parse(s: String): Boolean = s.toBoolean
    def rawType: Type = classOf[Boolean]
  }

  implicit val ofJavaBoolean: Flaggable[JBoolean] = new Typed[JBoolean] {
    override def default: Option[JBoolean] = Some(JBoolean.TRUE)
    def parse(s: String): JBoolean = JBoolean.valueOf(s.toBoolean)
    def rawType: Type = classOf[JBoolean]
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

  implicit val ofJavaLocalTime: Flaggable[LocalTime] = mandatory(LocalTime.parse)

  implicit val ofInetSocketAddress: Flaggable[InetSocketAddress] =
    new Typed[InetSocketAddress] {
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

      def rawType: Type = classOf[InetSocketAddress]
    }

  implicit val ofFile: Flaggable[File] = mandatory(s => new File(s))

  implicit def ofTuple[T: Flaggable, U: Flaggable]: Flaggable[(T, U)] = new Generic[(T, U)] {
    private val tflag = implicitly[Flaggable[T]]
    private val uflag = implicitly[Flaggable[U]]

    def parse(v: String): (T, U) = v.split(",") match {
      case Array(t, u) => (tflag.parse(t), uflag.parse(u))
      case _ => throw new IllegalArgumentException("not a 't,u'")
    }

    override def show(tup: (T, U)): String = {
      val (t, u) = tup
      tflag.show(t) + "," + uflag.show(u)
    }

    def parameters: Array[Flaggable[_]] = Array(tflag, uflag)
    def rawType: Type = classOf[Tuple2[_, _]]
  }

  /**
   * Create a Flaggable of Java Enum.
   * @param clazz The `java.lang.Enum` class.
   * @note `java.lang.Enum` enumeration constant value look up is case insensitive.
   */
  def ofJavaEnum[T <: Enum[T]](clazz: Class[T]): Flaggable[T] = new Typed[T] {
    def parse(s: String): T =
      clazz.getEnumConstants.find(_.name.equalsIgnoreCase(s)) match {
        case Some(prop) => prop
        case _ =>
          throw new IllegalArgumentException(
            s"The property $s does not belong to Java Enum ${clazz.getName}, the constants defined " +
              s"in the class are: ${clazz.getEnumConstants.mkString(",")}.")
      }

    def rawType: Type = clazz
  }

  private[app] class SetFlaggable[T: Flaggable] extends Generic[Set[T]] {
    private val flag = implicitly[Flaggable[T]]
    def parse(v: String): Set[T] =
      if (v.isEmpty) Set.empty[T]
      else v.split(",").iterator.map(flag.parse(_)).toSet
    override def show(set: Set[T]): String = set.map(flag.show).mkString(",")

    def parameters: Array[Flaggable[_]] = Array(flag)
    def rawType: Type = classOf[Set[_]]
  }

  private[app] class SeqFlaggable[T: Flaggable] extends Generic[Seq[T]] {
    private val flag = implicitly[Flaggable[T]]
    def parse(v: String): Seq[T] =
      if (v.isEmpty) Seq.empty[T]
      else v.split(",").toSeq.map(flag.parse)
    override def show(seq: Seq[T]): String = seq.map(flag.show).mkString(",")

    override def parameters: Array[Flaggable[_]] = Array(flag)
    override def rawType: Type = classOf[Seq[_]]
  }

  private[app] class MapFlaggable[K: Flaggable, V: Flaggable] extends Generic[Map[K, V]] {
    private val kflag = implicitly[Flaggable[K]]
    private val vflag = implicitly[Flaggable[V]]

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

    def parameters: Array[Flaggable[_]] = Array(kflag, vflag)
    def rawType: Type = classOf[Map[_, _]]
  }

  implicit def ofSet[T: Flaggable]: Flaggable[Set[T]] = new SetFlaggable[T]
  implicit def ofSeq[T: Flaggable]: Flaggable[Seq[T]] = new SeqFlaggable[T]
  implicit def ofMap[K: Flaggable, V: Flaggable]: Flaggable[Map[K, V]] = new MapFlaggable[K, V]

  implicit def ofJavaSet[T: Flaggable]: Flaggable[JSet[T]] = new Generic[JSet[T]] {
    val setFlaggable = new SetFlaggable[T]
    override def parse(v: String): JSet[T] = setFlaggable.parse(v).asJava
    override def show(set: JSet[T]): String = setFlaggable.show(set.asScala.toSet)

    def parameters: Array[Flaggable[_]] = setFlaggable.parameters
    def rawType: Type = classOf[JSet[_]]
  }

  implicit def ofJavaList[T: Flaggable]: Flaggable[JList[T]] = new Generic[JList[T]] {
    val seqFlaggable = new SeqFlaggable[T]
    override def parse(v: String): JList[T] = seqFlaggable.parse(v).asJava
    override def show(list: JList[T]): String = seqFlaggable.show(list.asScala.toSeq)

    def parameters: Array[Flaggable[_]] = seqFlaggable.parameters
    def rawType: Type = classOf[JList[_]]
  }

  implicit def ofJavaMap[K: Flaggable, V: Flaggable]: Flaggable[JMap[K, V]] =
    new Generic[JMap[K, V]] {
      val mapFlaggable = new MapFlaggable[K, V]
      def parse(in: String): JMap[K, V] = mapFlaggable.parse(in).asJava
      override def show(out: JMap[K, V]): String = mapFlaggable.show(out.asScala.toMap)

      def parameters: Array[Flaggable[_]] = mapFlaggable.parameters
      def rawType: Type = classOf[JMap[_, _]]
    }
}
