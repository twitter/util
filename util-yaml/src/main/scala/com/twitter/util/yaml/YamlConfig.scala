package com.twitter.util.yaml

import com.twitter.conversions.time._
import com.twitter.logging.Logger
import com.twitter.util.Duration
import java.io.{BufferedReader, File, FileInputStream, InputStream, InputStreamReader, Reader}
import java.net.{InetSocketAddress, SocketAddress}
import java.util.{Map => JMap, List => JList, Collections}
import scala.collection.JavaConversions._
import scala.collection.Set

// XXX move to config?

/** Yaml configuration file (e.g. decider.yml).  For Yaml file containing a map.
  *
  * Example:
  *   config = new YamlConfig(yaml)
  *   assert(config("user.david.id") == 23)
  */
class YamlConfig(yamlMap: JMap[String, Any]) extends Iterable[(String, Any)] {
  def this(io: InputStream) =
    this(YamlConfig.loadYaml(io))
  def this(reader: Reader) =
    this(YamlConfig.loadYaml(reader))
  def this(yamlOrPath: String) =
    this(YamlConfig.loadYaml(yamlOrPath))

  def apply[T](key: String): Option[T] = get(key)

  def iterator: Iterator[(String, Any)] = yamlMap.iterator

  def keys: Iterator[String] = yamlMap.keys.iterator

  def keySet: Set[String] = asScalaSet(yamlMap.keySet)

  def contains(key: String): Boolean =
    getAny(key) match {
      case None    => false
      case Some(_) => true
    }

  def getAny(key: String): Option[Any] = {
    key.split("\\.").toList match {
      case parts if parts.length > 1 =>
        val map = parts.take(parts.length-1).foldLeft(yamlMap) { (map, part) =>
          if (map.containsKey(part)) {
            val nextMap = map.get(part)
            if (nextMap.isInstanceOf[JMap[_,_]])
              nextMap.asInstanceOf[JMap[String,Any]]
            else
              return None
          }
          else {
            return None
          }
        }
        if (map.containsKey(parts.last)) {
          val value = map.get(parts.last)
          if (value != null)
            Some(value)
          else
            Some("") // treat empty value as empty string
        } else {
          None
        }
      case key :: Nil =>
        if (key.isEmpty) {
          Some(yamlMap)
        } else if (yamlMap.containsKey(key)) {
          val value = yamlMap.get(key)
          if (value != null)
            Some(value)
          else
            Some("")
        } else {
          None
        }
      case _ =>
        None
    }
  }

  def getAnyOrElse(key: String, default: => Any): Any =
    getAny(key).getOrElse(default)

  def get[T](key: String): Option[T] =
    getAny(key).map { x => x.asInstanceOf[T] }

  def getOrElse[T](key: String, default: => T): T =
    get[T](key).getOrElse(default)

  def getBoolean(key: String): Option[Boolean] =
    getAny(key).map(_.asInstanceOf[Boolean]) // might cause cast exception

  def getBooleanOrElse(key: String, default: => Boolean): Boolean =
    getBoolean(key).getOrElse(default)

  def getInt(key: String): Option[Int] =
    getAny(key).map(_ match {
      case value:Int    => value
      case value:Long   => value.toInt
      case value:Float  => value.toInt
      case value:Double => value.toInt
      case value:String => value.toInt
      case value:Any    => value.asInstanceOf[Int].toInt // force cast exception
    })

  def getIntOrElse(key: String, default: => Int): Int =
    getInt(key).getOrElse(default)

  def getLong(key: String): Option[Long] =
    getAny(key).map(_ match {
      case value:Int    => value.toLong
      case value:Long   => value
      case value:Float  => value.toLong
      case value:Double => value.toLong
      case value:String => value.toLong
      case value:Any    => value.asInstanceOf[Long].toLong // force cast exception
    })

  def getLongOrElse(key: String, default: => Long): Long =
    getLong(key).getOrElse(default)

  def getFloat(key: String): Option[Float] =
    getAny(key).map(_ match {
      case value:Int    => value.toFloat
      case value:Long   => value.toFloat
      case value:Float  => value
      case value:Double => value.toFloat
      case value:String => value.toFloat
      case value:Any    => value.asInstanceOf[Float].toFloat // force cast exception
    })

  def getFloatOrElse(key: String, default: => Float): Float =
    getFloat(key).getOrElse(default)

  def getDouble(key: String): Option[Double] =
    getAny(key).map(_ match {
      case value:Int    => value.toDouble
      case value:Long   => value.toDouble
      case value:Float  => value.toDouble
      case value:Double => value
      case value:String => value.toDouble
      case value:Any    => value.asInstanceOf[Double].toDouble // force cast exception
    })

  def getDoubleOrElse(key: String, default: => Double): Double =
    getDouble(key).getOrElse(default)

  def getString(key: String): Option[String] =
    getAny(key).map(_ match {
      case value:Boolean => value.toString
      case value:Int     => value.toString
      case value:Long    => value.toString
      case value:Float   => value.toString
      case value:Double  => value.toString
      case value:String  => value
      case value:Any     => value.asInstanceOf[String].toString // force cast exception
    })

  def getStringOrElse(key: String, default: => String): String =
    getString(key).getOrElse(default)

  def getDuration(key: String): Option[Duration] =
    try {
      getDouble(key).map { value => (value * 1000000L).toLong.microseconds }
    } catch {
      // Try interpreting value as erb
      case _: NumberFormatException => getString(key) map { erb => RubyUtil.erbToDuration(erb) }
    }


  // XXX remove this - use getDurationOrElse
  def getDurationOrThrow(key: String): Duration =
    getDurationOrElse(key, throw new NoSuchFieldException(key))

  def getDurationOrElse(key: String, default: => Duration): Duration =
    getDuration(key).getOrElse(default)

  def getList[T](key: String): Option[List[T]] =
    get[JList[T]](key).map { jlist => jlist.toList }

  def getListOrElse[T](key: String, default: => List[T]): List[T] =
    getList(key).getOrElse(default)

  def getInetAddrs(key: String, defaultPort: Int=0): Option[List[SocketAddress]] =
    getList[String](key).map { strings =>
      strings.map { string =>
        string.split(":", 2) match {
          case Array(host, port) => new InetSocketAddress(host, port.toInt)
          case Array(host)       => new InetSocketAddress(host, defaultPort)
          case _                 => throw new IllegalArgumentException
        }
      }
    }

  def getInetAddrsOrElse(key: String, default: => List[SocketAddress], defaultPort: Int=0):
      List[SocketAddress] =
    getInetAddrs(key, defaultPort).getOrElse(default)

  def getMap[T](key: String): Option[JMap[String, T]] = get(key)

  // Assumes underlying yaml file only has fields that are strings or lists.
  // Get ip blocks of form (ip, mask) specified in CIDR notation (e.g. 10.0.0.0/8)
  def getIpBlocks(key: String): Iterable[(Int, Int)] = {
    val cidrs: Iterable[Any] = if (key.isEmpty) yamlMap.values else Seq(getAny(key).get)
    cidrs flatMap {
      case cidr: String => List(NetUtil.cidrToIpBlock(cidr))
      case x => x.asInstanceOf[JList[String]].toList map { NetUtil.cidrToIpBlock }
    }
  }
}

object YamlConfig extends YamlConfigLoader[JMap[String, Any]](
  Collections.EMPTY_MAP.asInstanceOf[JMap[String, Any]]
)

/**
 * Yaml configuration file in "environment" format:
 *   environment is the name of a top-level hash of values
 *   "default" is a top-level hash of default values
 * For example:
 *   With YAML:
 *     default:
 *       host: localhost
 *       port: 8888
 *     production:
 *       host: example.com
 *   config = new EnvironmentYamlConfig(yaml, "production")
 *   assert(config("host") == "example.com")
 *   assert(config("port") == 8888)
 */
class EnvironmentYamlConfig(yamlMap: JMap[String, Any], environment: String)
extends YamlConfig(yamlMap) {
  def this(io: InputStream, environment: String) =
    this(YamlConfig.loadYaml(io), environment)
  def this(reader: Reader, environment: String) =
    this(YamlConfig.loadYaml(reader), environment)
  def this(yamlOrPath: String, environment: String) =
    this(YamlConfig.loadYaml(yamlOrPath), environment)

  override def getAny(key: String): Option[Any] =
    super.getAny(environment + "." + key) match {
      case None => super.getAny("defaults" + "." + key)
      case some => some
    }

  override def keySet: Set[String] = super.getAny(environment) match {
    case Some(v) => v.asInstanceOf[JMap[String, Any]].keySet
    case _ => Set()
  }

  override def keys: Iterator[String] = keySet.iterator
}
