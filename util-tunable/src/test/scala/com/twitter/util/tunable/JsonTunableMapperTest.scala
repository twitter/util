package com.twitter.util.tunable

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.twitter.conversions.StorageUnitOps._
import com.twitter.conversions.DurationOps._
import com.twitter.util.{Duration, Return, StorageUnit, Throw}
import scala.jdk.CollectionConverters._
import org.scalatest.funsuite.AnyFunSuite

// Used for veryifying custom deserialization
case class Foo(number: Double)

class JsonTunableMapperTest extends AnyFunSuite {

  test("returns a Throw if json is empty") {
    JsonTunableMapper().parse("") match {
      case Return(_) => fail()
      case Throw(_) =>
    }
  }

  test("parses valid json of no tunables into NullTunableMap") {
    val json = """{ "tunables": [ ] }"""
    JsonTunableMapper().parse("""{ "tunables": [ ] }""") match {
      case Return(map) =>
        assert(map eq NullTunableMap)
      case Throw(_) => fail()
    }
  }

  def assertInvalid(json: String) = JsonTunableMapper().parse(json) match {
    case Return(_) => fail()
    case Throw(_) =>
  }

  test("returns a Throw if tunables are not valid Tunables") {
    val json = """
      |{ "tunables": [
      |   { "foo" : "bar" }
      | ]
      |}""".stripMargin
    assertInvalid(json)
  }

  test("returns a Throw if tunables do not contain an id") {
    val json = """
      |{ "tunables": [
      |   { "type" : "com.twitter.util.Duration",
      |     "value" : 5.seconds
      |   }
      | ]
      |}""".stripMargin
    assertInvalid(json)
  }

  test("returns a Throw if tunables do not contain a type") {
    val json = """
      |{ "tunables": [
      |    { "id" : "timeoutId",
      |      "value" : 5.seconds
      |    }
      | ]
      |}""".stripMargin
    assertInvalid(json)
  }

  test("returns a Throw if tunables do not contain a value") {
    val json = """
      |{ "tunables": [
      |    { "id" : "timeoutId",
      |      "type" : "com.twitter.util.Duration"
      |    }
      | ]
      |}""".stripMargin
    assertInvalid(json)
  }

  test("parses valid json of tunables") {
    val json = """
     |{ "tunables": [
     |   { "id" : "timeoutId1",
     |     "value" : "5.seconds",
     |     "type" : "com.twitter.util.Duration",
     |     "comment": "a very important timeout"
     |   },
     |   { "id" : "timeoutId2",
     |     "value" : "Duration.Top",
     |     "type" : "com.twitter.util.Duration"
     |   },
     |   { "id" : "timeoutId3",
     |     "value" : "Duration.Bottom",
     |     "type" : "com.twitter.util.Duration"
     |   },
     |   { "id" : "timeoutId4",
     |     "value" : "Duration.Undefined",
     |     "type" : "com.twitter.util.Duration",
     |     "comment": "You'll never believe it: *another* timeout."
     |   }
     | ]
     |}""".stripMargin

    JsonTunableMapper().parse(json) match {
      case Return(map) =>
        assert(map.entries.size == 4)
        assert(map(TunableMap.Key[Duration]("timeoutId1"))() == Some(5.seconds))
        assert(map(TunableMap.Key[Duration]("timeoutId2"))() == Some(Duration.Top))
        assert(map(TunableMap.Key[Duration]("timeoutId3"))() == Some(Duration.Bottom))
        assert(map(TunableMap.Key[Duration]("timeoutId4"))() == Some(Duration.Undefined))
      case Throw(_) => fail()
    }
  }

  test("parses json with StorageUnit value") {
    val json = """
      |{ "tunables": [
      |   { "id" : "maxRequestSize",
      |     "value" : "100.kilobytes",
      |     "type" : "com.twitter.util.StorageUnit"
      |   }
      | ]
      |}""".stripMargin

    JsonTunableMapper().parse(json) match {
      case Return(map) =>
        assert(map.entries.size == 1)
        assert(map(TunableMap.Key[StorageUnit]("maxRequestSize"))() == Some(100.kilobytes))
      case Throw(_) => fail()
    }
  }

  test("Throws an IllegalArugmentException if multiple Tunables have the same id") {
    val json = """
      |{ "tunables": [
      |   { "id" : "timeoutId",
      |     "value" : "5.seconds",
      |     "type" : "com.twitter.util.Duration"
      |   },
      |   { "id" : "timeoutId",
      |     "value" : "10.seconds",
      |     "type" : "com.twitter.util.Duration"
      |   }
      | ]
      |}""".stripMargin

    JsonTunableMapper().parse(json) match {
      case Throw(_: IllegalArgumentException) =>
      case _ => fail()
    }
  }

  test("Can configure custom deserializer") {
    val fooDeserializer = new StdDeserializer[Foo](classOf[Foo]) {
      override def deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
      ): Foo = new Foo(jsonParser.getDoubleValue)
    }

    val json = """
      |{ "tunables": [
      |   { "id" : "fooId",
      |     "value" : 1.23,
      |     "type" : "com.twitter.util.tunable.Foo"
      |   }
      | ]
      |}""".stripMargin

    // make sure we can't decode a Foo without the fooDeserializer
    JsonTunableMapper().parse(json) match {
      case Throw(_) =>
      case _ => fail()
    }

    // now configure the fooDeserializer and see that we can now decode a Foo
    JsonTunableMapper(Seq(fooDeserializer)).parse(json) match {
      case Return(map) =>
        assert(map(TunableMap.Key[Foo]("fooId"))() == Some(Foo(1.23)))
      case Throw(_) => fail()
    }
  }

  test("Can configure multiple deserializers") {
    val fooDeserializer = new StdDeserializer[Foo](classOf[Foo]) {
      override def deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
      ): Foo = new Foo(jsonParser.getDoubleValue)
    }

    val json = """
      |{ "tunables": [
      |   { "id" : "fooId",
      |     "value" : 1.23,
      |     "type" : "com.twitter.util.tunable.Foo"
      |   },
      |   { "id" : "timeoutId",
      |     "value" : "5.seconds",
      |     "type" : "com.twitter.util.Duration"
      |   }
      | ]
      |}""".stripMargin

    JsonTunableMapper(JsonTunableMapper.DefaultDeserializers :+ fooDeserializer).parse(json) match {
      case Return(map) =>
        assert(map(TunableMap.Key[Foo]("fooId"))() == Some(Foo(1.23)))
        assert(map(TunableMap.Key[Duration]("timeoutId"))() == Some(5.seconds))
      case Throw(_) => fail()
    }
  }

  test("loadJsonTunables returns a NullTunableMap when the file does not exist") {

    assert(
      JsonTunableMapper().loadJsonTunables("IdForNonexistantFile", "mumbojumo")
        == NullTunableMap
    )
  }

  test("loadJsonTunables returns an IllegalArgumentException when the file exists but is empty") {
    val path = s"com/twitter/tunables/IdForEmptyFile/instances.json"
    val ex = intercept[IllegalArgumentException] {
      JsonTunableMapper().loadJsonTunables("IdForEmptyFile", path)
    }
    assert(ex.getMessage.contains("Failed to parse Tunable configuration file for IdForEmptyFile"))
  }

  test("loadJsonTunables throws an IllegalArgumentException if the file cannot be parsed") {
    val path = s"com/twitter/tunables/IdForInvalidJson/instances.json"
    val ex = intercept[IllegalArgumentException] {
      JsonTunableMapper().loadJsonTunables("IdForInvalidJson", path)
    }
    assert(
      ex.getMessage.contains("Failed to parse Tunable configuration file for IdForInvalidJson")
    )
  }

  test("loadJsonTunables loads JSON tunables for a given client id when the JSON is valid") {
    val path = s"com/twitter/tunables/IdForValidJson/instances.json"
    val map = JsonTunableMapper().loadJsonTunables("IdForValidJson", path)
    assert(map.entries.size == 4)
    assert(map(TunableMap.Key[Duration]("timeoutId1"))() == Some(5.seconds))
    assert(map(TunableMap.Key[Duration]("timeoutId2"))() == Some(Duration.Top))
    assert(map(TunableMap.Key[Duration]("timeoutId3"))() == Some(Duration.Bottom))
    assert(map(TunableMap.Key[Duration]("timeoutId4"))() == Some(Duration.Undefined))
  }

  test(
    "tunableMapForResources throws an Illegal argument exception when there are multiple paths") {
    val rsc = getClass.getClassLoader
      .getResources("com/twitter/tunables/IdForValidJson/instances.json")
      .asScala
      .toSeq
      .head

    val ex = intercept[IllegalArgumentException] {
      JsonTunableMapper().tunableMapForResources("IdWithDuplicateResourceFiles", List(rsc, rsc))
    }
    assert(
      ex.getMessage
        .contains("Found multiple Tunable configuration files for IdWithDuplicateResourceFiles")
    )
  }
}
