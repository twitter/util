package com.twitter.util.yaml

import com.twitter.util.TimeConversions._
import java.io.File
import java.net.InetSocketAddress
import org.specs.Specification
import scala.collection.JavaConversions._

object YamlConfigSpec extends Specification {
  val YAML = """|int: 100
                |string: hello
                |float: 0.02
                |strInt: "123"
                |strFloat: "0.02"
                |btrue: true
                |bfalse: false
                |blank:
                |list:
                |  - hello
                |  - world
                |addrs:
                |  - 1.2.3.4
                |  - 5.6.7.8:9999
                |hash:
                |  int: 23
                |  string: hola
                |  float: 0.01
                |  btrue: true
                |  bfalse: false
                |  blank:
                |  morehash:
                |    string: hello
                |    int: 42
""".trim.stripMargin
  val yamlConfig = new YamlConfig(YAML)

  "YamlConfig" should {
    "iterable" in {
      yamlConfig.isEmpty must beFalse
    }

    "contains" in {
      yamlConfig.contains("int")     must beTrue
      yamlConfig.contains("unknown") must beFalse
    }

    "get" in {
      yamlConfig.get("")                     must beSomething

      yamlConfig.get("int")                  must_== Some(100)
      yamlConfig.get("string")               must_== Some("hello")
      yamlConfig.get("float")                must_== Some(0.02)
      yamlConfig.get("btrue")                must_== Some(true)
      yamlConfig.get("bfalse")               must_== Some(false)
      yamlConfig.get("blank")                must_== Some("")
      yamlConfig.get("list")                 must_== Some(asJavaList(List("hello", "world")))

      yamlConfig.get("hash")                 must beSomething
      yamlConfig.get("hash.int")             must_== Some(23)
      yamlConfig.get("hash.string")          must_== Some("hola")
      yamlConfig.get("hash.float")           must_== Some(0.01)
      yamlConfig.get("hash.btrue")           must_== Some(true)
      yamlConfig.get("hash.bfalse")          must_== Some(false)
      yamlConfig.get("hash.blank")           must_== Some("")

      yamlConfig.get("hash.morehash")        must beSomething
      yamlConfig.get("hash.morehash.int")    must_== Some(42)
      yamlConfig.get("hash.morehash.string") must_== Some("hello")

      yamlConfig.get("unknown")              must_== None
      yamlConfig.get("hash.unknown")         must_== None
      yamlConfig.get("unknown.unknown")      must_== None
      yamlConfig.get("int.unknown")          must_== None

      yamlConfig("int") must_== Some(100) // apply works too
    }

    "getOrElse" in {
      yamlConfig.getOrElse("int", -1)                  must_== 100
      yamlConfig.getOrElse("hash.int", -1)             must_== 23
      yamlConfig.getOrElse("hash.float", 1.0)          must_== 0.01
      yamlConfig.getOrElse("hash.morehash.int", -1)    must_== 42
      yamlConfig.getOrElse("hash.morehash.string", "") must_== "hello"

      yamlConfig.getOrElse("unknown", "x")             must_== "x"
      yamlConfig.getOrElse("hash.unknown", 23)         must_== 23
      yamlConfig.getOrElse("unknown.unknown", 23)      must_== 23
      yamlConfig.getOrElse("int.unknown", 23)          must_== 23
    }

    "getBoolean" in {
      yamlConfig.getBoolean("unknown") must_== None
      yamlConfig.getBoolean("btrue")   must_== Some(true)
      yamlConfig.getBoolean("float")   must throwA[ClassCastException]

      yamlConfig.getBooleanOrElse("unknown", true) must_== true
      yamlConfig.getBooleanOrElse("btrue", false)  must_== true
    }

    "getInt" in {
      yamlConfig.getInt("unknown") must_== None
      yamlConfig.getInt("int")     must_== Some(100)
      yamlConfig.getInt("strInt")  must_== Some(123)
      yamlConfig.getInt("float")   must_== Some(0)
      yamlConfig.getInt("btrue")   must throwA[ClassCastException]

      yamlConfig.getIntOrElse("unknown", 1) must_== 1
      yamlConfig.getIntOrElse("int", 1)     must_== 100
    }

    "getLong" in {
      yamlConfig.getLong("unknown") must_== None
      yamlConfig.getLong("int")     must_== Some(100L)
      yamlConfig.getLong("strInt")  must_== Some(123L)
      yamlConfig.getLong("float")   must_== Some(0L)
      yamlConfig.getLong("btrue")   must throwA[ClassCastException]

      yamlConfig.getLongOrElse("unknown", 1L) must_== 1L
      yamlConfig.getLongOrElse("int", 1L)     must_== 100L
    }

    "getFloat" in {
      yamlConfig.getFloat("unknown")  must_== None
      yamlConfig.getFloat("int")      must_== Some(100.0f)
      yamlConfig.getFloat("strFloat") must_== Some(0.02f)
      yamlConfig.getFloat("float")    must_== Some(0.02f)
      yamlConfig.getFloat("btrue")    must throwA[ClassCastException]

      yamlConfig.getFloatOrElse("unknown", 1.0f) must_== 1.0f
      yamlConfig.getFloatOrElse("float", 1.0f)   must_== 0.02f
    }

    "getDouble" in {
      yamlConfig.getDouble("unknown")  must_== None
      yamlConfig.getDouble("int")      must_== Some(100.0)
      yamlConfig.getDouble("strFloat") must_== Some(0.02)
      yamlConfig.getDouble("float")    must_== Some(0.02)
      yamlConfig.getDouble("btrue")    must throwA[ClassCastException]

      yamlConfig.getDoubleOrElse("unknown", 1.0) must_== 1.0
      yamlConfig.getDoubleOrElse("float", 1.0)   must_== 0.02
    }

    "getString" in {
      yamlConfig.getString("unknown")  must_== None
      yamlConfig.getString("int")      must_== Some("100")
      yamlConfig.getString("string")   must_== Some("hello")
      yamlConfig.getString("float")    must_== Some("0.02")
      yamlConfig.getString("btrue")    must_== Some("true")

      yamlConfig.getStringOrElse("unknown", "x") must_== "x"
      yamlConfig.getStringOrElse("string", "x")  must_== "hello"
    }

    "getDuration" in {
      yamlConfig.getDuration("hash.float")                   must_== Some(10.milliseconds)
      yamlConfig.getDurationOrElse("hash.float", 1.second)   must_== 10.milliseconds
      yamlConfig.getDurationOrElse("hash.unknown", 1.second) must_== 1.second
    }

    "getList" in {
      yamlConfig.getList("list")                     must_== Some(List("hello", "world"))
      yamlConfig.getListOrElse("list",    List("x")) must_== List("hello", "world")
      yamlConfig.getListOrElse("unknown", List("x")) must_== List("x")
    }

    "getInetAddrs" in {
      val addrs = List(new InetSocketAddress("1.2.3.4", 0),
                       new InetSocketAddress("5.6.7.8", 9999))
      val addrs7 = List(new InetSocketAddress("1.2.3.4", 7),
                        new InetSocketAddress("5.6.7.8", 9999))

      yamlConfig.getInetAddrs("addrs")              must_== Some(addrs)
      yamlConfig.getInetAddrs("addrs", 7)           must_== Some(addrs7)
      yamlConfig.getInetAddrsOrElse("addrs", Nil)   must_== addrs
      yamlConfig.getInetAddrsOrElse("unknown", Nil) must_== Nil
    }
  }

  val ENVIRONMENT_YAML = """|defaults:
                            |  host: localhost
                            |  port: 8888
                            |production:
                            |  host: example.com
                            |  post: B
                            |  lost: true
""".trim.stripMargin
  val environmentYamlConfig = new EnvironmentYamlConfig(ENVIRONMENT_YAML, _:String)

  "EnvironmentYamlConfig" should {
    "get" in {
      val developmentConfig = environmentYamlConfig("development")
      developmentConfig("host")    must_== Some("localhost")
      developmentConfig("port")    must_== Some(8888)
      developmentConfig("unknown") must_== None

      val productionConfig = environmentYamlConfig("production")
      productionConfig("host")    must_== Some("example.com")
      productionConfig("port")    must_== Some(8888)
      productionConfig("unknown") must_== None

    }
    "keySet" in {
      val productionConfig = environmentYamlConfig("production")
      productionConfig.keySet must haveTheSameElementsAs(Set("host", "post", "lost"))
    }
  }
}
