package com.twitter.util.registry

import java.util.logging.Logger
import scala.util.control.NoStackTrace

object Formatter {
  private[this] val log = Logger.getLogger(getClass.getName)

  /**
   * The top-level key in the `Map` returned by [[asMap]].
   */
  val RegistryKey = "registry"

  private[registry] val Eponymous = "__eponymous"

  /**
   * assumes that you do not create an exact collision
   */
  private[registry] def add(
    old: Map[String, Object],
    keys: Seq[String],
    value: String
  ): Map[String, Object] =
    old + (keys match {
      case Nil => (Eponymous -> value)
      case head +: tail => {
        head -> (old.get(head) match {
          case None =>
            if (tail.isEmpty) value
            else makeMap(tail, value)

          // we can't prove that this is anything better than a Map[_, _], but that's OK
          case Some(map: Map[_, _]) => add(map.asInstanceOf[Map[String, Object]], tail, value)
          case Some(string: String) =>
            if (tail.isEmpty) throw Collision
            else makeMap(tail, value) + (Eponymous -> string)
          case Some(_) => throw InvalidType
        })
      }
    })

  /**
   * @param seq is not permitted to be empty
   */
  private[registry] def makeMap(seq: Seq[String], value: String): Map[String, Object] =
    seq.foldRight[Either[Map[String, Object], String]](Right(value)) {
      case (key, Right(string)) => Left(Map(key -> string))
      case (key, Left(map)) => Left(Map(key -> map))
    } match {
      case Right(string) => throw Empty
      case Left(map) => map
    }

  def asMap(registry: Registry): Map[String, Object] = {
    var map: Map[String, Object] = Map.empty[String, Object]
    registry.foreach {
      case Entry(keys, value) =>
        try {
          map = add(map, keys, value)
        } catch {
          case Collision =>
            log.severe(s"collided on (${keys.mkString(",")}) -> $value")
          case InvalidType =>
            log.severe(s"found an invalid type on (${keys.mkString(",")}) -> $value")
          case Empty =>
            val returnString = s"(${keys.mkString(",")}) -> $value"
            log.severe(s"incorrectly found an empty seq on $returnString")
        }
    }
    Map(RegistryKey -> map)
  }

  private[registry] val Collision = new Exception() with NoStackTrace
  private[registry] val InvalidType = new Exception() with NoStackTrace
  private[registry] val Empty = new Exception() with NoStackTrace
}
