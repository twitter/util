package com.twitter.util.jackson

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.TreeTraversingParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.{ObjectMapper => JacksonObjectMapper}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}
import com.twitter.io.Buf
import com.twitter.util.Duration
import com.twitter.util.jackson.Obj.NestedCaseClassInObject
import com.twitter.util.jackson.Obj.NestedCaseClassInObjectWithNestedCaseClassInObjectParam
import com.twitter.util.jackson.TypeAndCompanion.NestedCaseClassInCompanion
import com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.Unspecified
import com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.ValidationError
import com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException
import com.twitter.util.jackson.internal.SimplePersonInPackageObject
import com.twitter.util.jackson.internal.SimplePersonInPackageObjectWithoutConstructorParams
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.TimeZone
import java.util.UUID
import org.junit.Assert.assertNotNull
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import scala.util.Random

private object ScalaObjectMapperTest {
  val UTCZoneId: ZoneId = ZoneId.of("UTC")
  val ISO8601DateTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ISO_OFFSET_DATE_TIME // "2014-05-30T03:57:59.302Z"

  class ZeroOrOneDeserializer extends JsonDeserializer[ZeroOrOne] {
    override def deserialize(
      jsonParser: JsonParser,
      deserializationContext: DeserializationContext
    ): ZeroOrOne = {
      jsonParser.getValueAsString match {
        case "zero" => Zero
        case "one" => One
        case _ =>
          throw new JsonMappingException(null, "Invalid value")
      }
    }
  }

  object MixInAnnotationsModule extends SimpleModule {
    setMixInAnnotation(classOf[Point], classOf[PointMixin])
    setMixInAnnotation(classOf[CaseClassShouldUseKebabCaseFromMixin], classOf[KebabCaseMixin])
  }
}

@RunWith(classOf[JUnitRunner])
class ScalaObjectMapperTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with ScalaObjectMapperFunctions {

  import ScalaObjectMapperTest._

  /* Class under test */
  protected val mapper: ScalaObjectMapper = ScalaObjectMapper()

  private final val steve: Person =
    Person(id = 1, name = "Steve", age = Some(20), age_with_default = Some(20), nickname = "ace")
  private final val steveJson: String =
    """{
       "id" : 1,
       "name" : "Steve",
       "age" : 20,
       "age_with_default" : 20,
       "nickname" : "ace"
     }
    """

  override def beforeAll(): Unit = {
    super.beforeAll()
    mapper.registerModule(ScalaObjectMapperTest.MixInAnnotationsModule)
  }

  test("constructors") {
    // new mapper with defaults
    assertNotNull(ScalaObjectMapper())
    assertNotNull(ScalaObjectMapper.apply())

    // augments the given mapper with the defaults
    assertNotNull(ScalaObjectMapper(mapper.underlying))
    assertNotNull(ScalaObjectMapper.apply(mapper.underlying))

    // copies the underlying Jackson object mapper
    assertNotNull(ScalaObjectMapper.objectMapper(mapper.underlying))
    // underlying mapper needs to have a JsonFactory of type YAMLFactory
    assertThrows[IllegalArgumentException](ScalaObjectMapper.yamlObjectMapper(mapper.underlying))
    assertNotNull(
      ScalaObjectMapper.yamlObjectMapper(ScalaObjectMapper.builder.yamlObjectMapper.underlying))
    assertNotNull(ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying))
    assertNotNull(ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying))

    assertThrows[AssertionError](new ScalaObjectMapper(null))
  }

  test("builder constructors") {
    assertNotNull(ScalaObjectMapper.builder.objectMapper)
    assertNotNull(ScalaObjectMapper.builder.objectMapper(new JsonFactory))
    assertNotNull(ScalaObjectMapper.builder.objectMapper(new YAMLFactory))

    assertNotNull(ScalaObjectMapper.builder.yamlObjectMapper)
    assertNotNull(ScalaObjectMapper.builder.camelCaseObjectMapper)
    assertNotNull(ScalaObjectMapper.builder.snakeCaseObjectMapper)
  }

  test("mapper register module") {
    val testMapper = ScalaObjectMapper()

    val simpleJacksonModule = new SimpleModule()
    simpleJacksonModule.addDeserializer(classOf[ZeroOrOne], new ZeroOrOneDeserializer)
    testMapper.registerModule(simpleJacksonModule)

    // regular mapper (without the custom deserializer) -- doesn't parse
    intercept[JsonMappingException] {
      mapper.parse[CaseClassWithZeroOrOne]("{\"id\" :\"zero\"}")
    }

    // mapper with custom deserializer -- parses correctly
    testMapper.parse[CaseClassWithZeroOrOne]("{\"id\" :\"zero\"}") should be(
      CaseClassWithZeroOrOne(Zero))
    testMapper.parse[CaseClassWithZeroOrOne]("{\"id\" :\"one\"}") should be(
      CaseClassWithZeroOrOne(One))
    intercept[JsonMappingException] {
      testMapper.parse[CaseClassWithZeroOrOne]("{\"id\" :\"two\"}")
    }
  }

  test("inject request field fails with a mapping exception") {
    // with no InjectableValues we get a CaseClassMappingException since we can't map
    // the JSON into the case class.
    intercept[CaseClassMappingException] {
      parse[ClassWithQueryParamDateTimeInject]("""{}""")
    }
  }

  test(
    "class with an injectable field fails with a mapping exception when it cannot be parsed from JSON") {
    // if there is no injector, the default injectable values is not configured, thus the code
    // tries to construct the asked for type from the given JSON, which is empty and fails with
    // a mapping exception.
    intercept[CaseClassMappingException] {
      parse[ClassWithFooClassInject]("""{}""")
    }
  }

  test("class with an injectable field is not constructed from JSON") {
    // no field injection configured and parsing proceeds as normal
    val result = parse[ClassWithFooClassInject](
      """
        |{
        |  "foo_class": {
        |    "id" : "11"
        |  }
        |}""".stripMargin
    )
    result.fooClass should equal(FooClass("11"))
  }

  test("class with a defaulted injectable field is constructed from the default") {
    // no field injection configured and default value is not used since a value is passed in the JSON.
    val result = parse[ClassWithFooClassInjectAndDefault](
      """
        |{
        |  "foo_class": {
        |    "id" : "1"
        |  }
        |}""".stripMargin
    )

    result.fooClass should equal(FooClass("1"))
  }

  test("injectable field should use default when field not sent in json") {
    parse[CaseClassInjectStringWithDefault]("""{}""") should equal(
      CaseClassInjectStringWithDefault("DefaultHello"))
  }

  test("injectable field should not use default when field sent in json") {
    parse[CaseClassInjectStringWithDefault]("""{"string": "123"}""") should equal(
      CaseClassInjectStringWithDefault("123"))
  }

  test("injectable field should use None assumed default when field not sent in json") {
    parse[CaseClassInjectOptionString]("""{}""") should equal(CaseClassInjectOptionString(None))
  }

  test("injectable field should throw exception when no value is passed in the json") {
    intercept[CaseClassMappingException] {
      parse[CaseClassInjectString]("""{}""")
    }
  }

  test("regular mapper handles unknown properties when json provides MORE fields than case class") {
    // regular mapper -- doesn't fail
    mapper.parse[CaseClass](
      """
        |{
        |  "id": 12345,
        |  "name": "gadget",
        |  "extra": "fail"
        |}
        |""".stripMargin
    )

    // mapper = loose, case class = annotated strict --> Fail
    intercept[JsonMappingException] {
      mapper.parse[StrictCaseClass](
        """
          |{
          |  "id": 12345,
          |  "name": "gadget",
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }
  }

  test("regular mapper handles unknown properties when json provides LESS fields than case class") {
    // regular mapper -- doesn't fail
    mapper.parse[CaseClassWithOption](
      """
        |{
        |  "value": 12345,
        |  "extra": "fail"
        |}
        |""".stripMargin
    )

    // mapper = loose, case class = annotated strict --> Fail
    intercept[JsonMappingException] {
      mapper.parse[StrictCaseClassWithOption](
        """
          |{
          |  "value": 12345,
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }
  }

  test("regular mapper handles unknown properties") {
    // regular mapper -- doesn't fail (no field named 'flame')
    mapper.parse[CaseClassIdAndOption](
      """
        |{
        |  "id": 12345,
        |  "flame": "gadget"
        |}
        |""".stripMargin
    )

    // mapper = loose, case class = annotated strict --> Fail (no field named 'flame')
    intercept[JsonMappingException] {
      mapper.parse[StrictCaseClassIdAndOption](
        """
          |{
          |  "id": 12345,
          |  "flame": "gadget"
          |}
          |""".stripMargin
      )
    }
  }

  test("mapper with deserialization config fails on unknown properties") {
    val testMapper =
      ScalaObjectMapper.builder
        .withDeserializationConfig(Map(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES -> true))
        .objectMapper

    // mapper = strict, case class = unannotated --> Fail
    intercept[JsonMappingException] {
      testMapper.parse[CaseClass](
        """
          |{
          |  "id": 12345,
          |  "name": "gadget",
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }

    // mapper = strict, case class = annotated strict --> Fail
    intercept[JsonMappingException] {
      testMapper.parse[StrictCaseClass](
        """
          |{
          |  "id": 12345,
          |  "name": "gadget",
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }

    // mapper = strict, case class = annotated loose --> Parse
    testMapper.parse[LooseCaseClass](
      """
        |{
        |  "id": 12345,
        |  "name": "gadget",
        |  "extra": "pass"
        |}
        |""".stripMargin
    )
  }

  test("mapper with additional configuration handles unknown properties") {
    // test with additional configuration set on mapper
    val testMapper =
      ScalaObjectMapper.builder
        .withAdditionalMapperConfigurationFn(
          _.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true))
        .objectMapper

    // mapper = strict, case class = unannotated --> Fail
    intercept[JsonMappingException] {
      testMapper.parse[CaseClass](
        """
          |{
          |  "id": 12345,
          |  "name": "gadget",
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }

    // mapper = strict, case class = annotated strict --> Fail
    intercept[JsonMappingException] {
      testMapper.parse[StrictCaseClass](
        """
          |{
          |  "id": 12345,
          |  "name": "gadget",
          |  "extra": "fail"
          |}
          |""".stripMargin
      )
    }

    // mapper = strict, case class = annotated loose --> Parse
    testMapper.parse[LooseCaseClass](
      """
        |{
        |  "id": 12345,
        |  "name": "gadget",
        |  "extra": "pass"
        |}
        |""".stripMargin
    )
  }

  test("support wrapped object mapper") {
    val person = CamelCaseSimplePersonNoAnnotation(myName = "Bob")

    val jacksonScalaObjectMapper: JacksonScalaObjectMapperType = new JacksonObjectMapper()
      with JacksonScalaObjectMapper
    jacksonScalaObjectMapper.registerModule(DefaultScalaModule)
    jacksonScalaObjectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

    // default for ScalaObjectMapper#Builder is snake_case which should not get applied here
    // since this method only wraps and does not mutate the underlying mapper
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.objectMapper(jacksonScalaObjectMapper)

    val serialized = """{"myName":"Bob"}"""
    // serialization -- they should each serialize the same way
    jacksonScalaObjectMapper.writeValueAsString(person) should equal(serialized)
    objectMapper.writeValueAsString(person) should equal(serialized)
    jacksonScalaObjectMapper.writeValueAsString(person) should equal(
      objectMapper.writeValueAsString(person))
    // deserialization -- each should be able to deserialize the other's written representation
    objectMapper.parse[CamelCaseSimplePersonNoAnnotation](
      jacksonScalaObjectMapper.writeValueAsString(person)) should equal(person)
    jacksonScalaObjectMapper.readValue[CamelCaseSimplePersonNoAnnotation](
      objectMapper.writeValueAsString(person)) should equal(person)
    objectMapper.parse[CamelCaseSimplePersonNoAnnotation](serialized) should equal(
      jacksonScalaObjectMapper.readValue[CamelCaseSimplePersonNoAnnotation](serialized))
  }

  test("support camel case mapper") {
    val camelCaseObjectMapper = ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying)

    camelCaseObjectMapper.parse[Map[String, String]]("""{"firstName": "Bob"}""") should equal(
      Map("firstName" -> "Bob")
    )
  }

  test("support snake case mapper") {
    val snakeCaseObjectMapper = ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying)

    val person = CamelCaseSimplePersonNoAnnotation(myName = "Bob")

    val serialized = snakeCaseObjectMapper.writeValueAsString(person)
    serialized should equal("""{"my_name":"Bob"}""")
    snakeCaseObjectMapper.parse[CamelCaseSimplePersonNoAnnotation](serialized) should equal(person)
  }

  test("support yaml mapper") {
    val yamlObjectMapper = ScalaObjectMapper.builder.yamlObjectMapper

    val person = CamelCaseSimplePersonNoAnnotation(myName = "Bob")

    val serialized = yamlObjectMapper.writeValueAsString(person)
    // default PropertyNamingStrategy for the generate YAML object mapper is snake_case
    serialized should equal("""---
                              |my_name: "Bob"
                              |""".stripMargin)
    yamlObjectMapper.parse[CamelCaseSimplePersonNoAnnotation](serialized) should equal(person)

    // but we can also update to a camelCase PropertyNamingStrategy:
    val camelCaseObjectMapper = ScalaObjectMapper.camelCaseObjectMapper(yamlObjectMapper.underlying)
    val serializedCamelCase = camelCaseObjectMapper.writeValueAsString(person)
    serializedCamelCase should equal("""---
                                       |myName: "Bob"
                                       |""".stripMargin)
    camelCaseObjectMapper.parse[CamelCaseSimplePersonNoAnnotation](
      serializedCamelCase) should equal(person)
  }

  test("Scala enums") {
    val expectedInstance = BasicDate(Month.Feb, 29, 2020, Weekday.Sat)
    val expectedStr = """{"month":"Feb","day":29,"year":2020,"weekday":"Sat"}"""
    parse[BasicDate](expectedStr) should equal(expectedInstance) // deser
    generate(expectedInstance) should equal(expectedStr) // ser

    // test with default scala module
    val defaultScalaObjectMapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    defaultScalaObjectMapper.registerModule(DefaultScalaModule)
    defaultScalaObjectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    deserialize[BasicDate](defaultScalaObjectMapper, expectedStr) should equal(expectedInstance)

    // case insensitive mapper feature does not pertain to scala enumerations (only Java enums)
    val caseInsensitiveEnumMapper =
      ScalaObjectMapper.builder
        .withAdditionalMapperConfigurationFn(
          _.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true))
        .objectMapper

    val withErrors212 = Seq("month: key not found: feb", "weekday: key not found: sat")
    val withErrors213 = Seq("month: null", "weekday: null")

    val expectedCaseInsensitiveStr = """{"month":"feb","day":29,"year":2020,"weekday":"sat"}"""
    val e = intercept[CaseClassMappingException] {
      caseInsensitiveEnumMapper.parse[BasicDate](expectedCaseInsensitiveStr)
    }
    val actualMessages = e.errors.map(_.getMessage)
    assert(
      actualMessages.mkString(",") == withErrors212.mkString(",") ||
        actualMessages.mkString(",") == withErrors213.mkString(","))

    // non-existent values
    val withErrors2212 = Seq("month: key not found: Nnn", "weekday: key not found: Flub")
    val withErrors2213 = Seq("month: null", "weekday: null")

    val expectedStr2 = """{"month":"Nnn","day":29,"year":2020,"weekday":"Flub"}"""
    val e2 = intercept[CaseClassMappingException] {
      parse[BasicDate](expectedStr2)
    }
    val actualMessages2 = e2.errors.map(_.getMessage)
    assert(
      actualMessages2.mkString(",") == withErrors2212.mkString(",") ||
        actualMessages2.mkString(",") == withErrors2213.mkString(","))

    val invalidWithOptionalScalaEnumerationValue = """{"month":"Nnn"}"""
    val e3 = intercept[CaseClassMappingException] {
      parse[WithOptionalScalaEnumeration](invalidWithOptionalScalaEnumerationValue)
    }
    e3.errors.size shouldEqual 1
    e3.errors.head.getMessage match {
      case "month: key not found: Nnn" => // Scala 2.12.x (https://github.com/scala/scala/blob/2.12.x/src/library/scala/collection/MapLike.scala#L236)
      case "month: null" => // Scala 2.13.1 (https://github.com/scala/scala/blob/2.13.x/src/library/scala/collection/immutable/HashMap.scala#L635)
      case _ => fail()
    }
    e3.errors.head.reason.message match {
      case "key not found: Nnn" => // Scala 2.12.x (https://github.com/scala/scala/blob/2.12.x/src/library/scala/collection/MapLike.scala#L236_
      case null => // Scala 2.13.1 (https://github.com/scala/scala/blob/2.13.x/src/library/scala/collection/immutable/HashMap.scala#L635)
      case _ => fail()
    }
    e3.errors.head.reason.detail shouldEqual Unspecified

    val validWithOptionalScalaEnumerationValue = """{"month":"Apr"}"""
    val validWithOptionalScalaEnumeration =
      parse[WithOptionalScalaEnumeration](validWithOptionalScalaEnumerationValue)
    validWithOptionalScalaEnumeration.month.isDefined shouldBe true
    validWithOptionalScalaEnumeration.month.get shouldEqual Month.Apr
  }

  // based on Jackson test to ensure compatibility:
  // https://github.com/FasterXML/jackson-module-scala/blob/fa7cf702e0f61467d726384af88de9ea1f798b97/src/test/scala/com/fasterxml/jackson/module/scala/deser/CaseClassDeserializerTest.scala#L78-L81
  test("deserialization#generic types") {
    parse[GenericTestCaseClass[Int]]("""{"data" : 3}""") should equal(GenericTestCaseClass(3))
    parse[GenericTestCaseClass[CaseClass]]("""{"data" : {"id" : 123, "name" : "foo"}}""") should
      equal(GenericTestCaseClass(CaseClass(123, "foo")))
    parse[CaseClassWithGeneric[CaseClass]](
      """{"inside" : { "data" : {"id" : 123, "name" : "foo"}}}""") should
      equal(CaseClassWithGeneric(GenericTestCaseClass(CaseClass(123, "foo"))))

    parse[GenericTestCaseClass[String]]("""{"data" : "Hello, World"}""") should equal(
      GenericTestCaseClass("Hello, World"))
    parse[GenericTestCaseClass[Double]]("""{"data" : 3.14}""") should equal(
      GenericTestCaseClass(3.14d))

    parse[CaseClassWithOptionalGeneric[Int]]("""{"inside": {"data": 3}}""") should equal(
      CaseClassWithOptionalGeneric[Int](Some(GenericTestCaseClass[Int](3))))

    parse[CaseClassWithTypes[String, Int]]("""{"first": "Bob", "second" : 42}""") should equal(
      CaseClassWithTypes("Bob", 42))
    parse[CaseClassWithTypes[Int, Float]]("""{"first": 127, "second" : 39.0}""") should equal(
      CaseClassWithTypes(127, 39.0f))

    parse[CaseClassWithMapTypes[String, Float]](
      """{"data": {"pi": 3.14, "inverse fine structure constant": 137.035}}""") should equal(
      CaseClassWithMapTypes(
        Map[String, Float]("pi" -> 3.14f, "inverse fine structure constant" -> 137.035f))
    )
    parse[CaseClassWithManyTypes[Int, Float, String]](
      """{"one": 1, "two": 3.1, "three": "Hello, World!"}""") should equal(
      CaseClassWithManyTypes(1, 3.1f, "Hello, World!"))

    val result = Page(
      List(
        Person(1, "Bob Marley", None, Some(32), Some(32), "Music Master"),
        Person(2, "Jimi Hendrix", None, Some(27), None, "Melody Man")),
      5,
      None,
      None)
    val input =
      """
        |{
        |  "data": [
        |    {"id": 1, "name": "Bob Marley", "age": 32, "age_with_default": 32, "nickname": "Music Master"},
        |    {"id": 2, "name": "Jimi Hendrix", "age": 27, "nickname": "Melody Man"}
        |  ],
        |  "page_size": 5
        |}
            """.stripMargin

    // test with default scala module
    val defaultScalaObjectMapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    defaultScalaObjectMapper.registerModule(DefaultScalaModule)
    defaultScalaObjectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    deserialize[Page[Person]](defaultScalaObjectMapper, input) should equal(result)

    // test with ScalaObjectMapper
    deserialize[Page[Person]](mapper.underlying, input) should equal(result)
  }

  // tests for GH #547
  test("deserialization#generic types 2") {
    val aGeneric = mapper.parse[AGeneric[B]]("""{"b":{"value":"string"}}""")
    aGeneric.b.map(_.value) should equal(Some("string"))

    val c = mapper.parse[C]("""{"a":{"b":{"value":"string"}}}""")
    c.a.b.map(_.value) should equal(Some("string"))

    val aaGeneric = mapper.parse[AAGeneric[B, Int]]("""{"b":{"value":"string"},"c":42}""")
    aaGeneric.b.map(_.value) should equal(Some("string"))
    aaGeneric.c.get should equal(42)

    val aaGeneric2 = mapper.parse[AAGeneric[B, D]]("""{"b":{"value":"string"},"c":{"value":42}}""")
    aaGeneric2.b.map(_.value) should equal(Some("string"))
    aaGeneric2.c.map(_.value) should equal(Some(42))

    val eGeneric =
      mapper.parse[E[B, D, Int]]("""{"a": {"b":{"value":"string"},"c":{"value":42}}, "b":42}""")
    eGeneric.a.b.map(_.value) should equal(Some("string"))
    eGeneric.a.c.map(_.value) should equal(Some(42))
    eGeneric.b should equal(Some(42))

    val fGeneric =
      mapper.parse[F[B, Int, D, AGeneric[B], Int, String]]("""
          |{
          |  "a":{"value":"string"},
          |  "b":[1,2,3],
          |  "c":{"value":42},
          |  "d":{"b":{"value":"string"}},
          |  "e":{"r":"forty-two"}
          |}
          |""".stripMargin)
    fGeneric.a.map(_.value) should equal(Some("string"))
    fGeneric.b should equal(Seq(1, 2, 3))
    fGeneric.c.map(_.value) should equal(Some(42))
    fGeneric.d.b.map(_.value) should equal(Some("string"))
    fGeneric.e should equal(Right("forty-two"))

    val fGeneric2 =
      mapper.parse[F[B, Int, D, AGeneric[B], Int, String]]("""
         |{
         |  "a":{"value":"string"},
         |  "b":[1,2,3],
         |  "c":{"value":42},
         |  "d":{"b":{"value":"string"}},
         |  "e":{"l":42}
         |}
         |""".stripMargin)
    fGeneric2.a.map(_.value) should equal(Some("string"))
    fGeneric2.b should equal(Seq(1, 2, 3))
    fGeneric2.c.map(_.value) should equal(Some(42))
    fGeneric2.d.b.map(_.value) should equal(Some("string"))
    fGeneric2.e should equal(Left(42))

    // requires polymorphic handling with @JsonTypeInfo
    val withTypeBounds =
      mapper.parse[WithTypeBounds[BoundaryA]]("""
        |{
        |  "a": {"type":"a", "value":"Guineafowl"}
        |}
        |""".stripMargin)
    withTypeBounds.a.map(_.value) should equal(Some("Guineafowl"))

    // uses a specific json deserializer
    val someNumberType = mapper.parse[SomeNumberType[java.lang.Integer]](
      """
        |{
        |  "n": 42
        |}
        |""".stripMargin
    )
    someNumberType.n should equal(Some(42))

    // multi-parameter non-collection type
    val gGeneric =
      mapper.parse[G[B, Int, D, AGeneric[B], Int, String]]("""
          |{
          |  "gg": {
          |    "t":{"value":"string"},
          |    "u":42,
          |    "v":{"value":42},
          |    "x":{"b":{"value":"string"}},
          |    "y":137,
          |    "z":"string"
          |  }
          |}
          |""".stripMargin)
    gGeneric.gg.t.value should be("string")
    gGeneric.gg.u should equal(42)
    gGeneric.gg.v.value should equal(42)
    gGeneric.gg.x.b.map(_.value) should equal(Some("string"))
    gGeneric.gg.y should equal(137)
    gGeneric.gg.z should be("string")
  }

  test("JsonProperty#annotation inheritance") {
    val aumJson = """{"i":1,"j":"J"}"""
    val aum = parse[Aum](aumJson)
    aum should equal(Aum(1, "J"))
    mapper.writeValueAsString(Aum(1, "J")) should equal(aumJson)

    val testCaseClassJson = """{"fedoras":["felt","straw"],"oldness":27}"""
    val testCaseClass = parse[CaseClassTraitImpl](testCaseClassJson)
    testCaseClass should equal(CaseClassTraitImpl(Seq("felt", "straw"), 27))
    mapper.writeValueAsString(CaseClassTraitImpl(Seq("felt", "straw"), 27)) should equal(
      testCaseClassJson)
  }

  test("simple tests#parse super simple") {
    val foo = parse[SimplePerson]("""{"name": "Steve"}""")
    foo should equal(SimplePerson("Steve"))
  }

  test("simple tests#parse super simple -- YAML") {
    val yamlMapper = ScalaObjectMapper.builder.yamlObjectMapper
    val foo = yamlMapper.parse[SimplePerson]("""name: Steve""")
    foo should equal(SimplePerson("Steve"))

    yamlMapper.writeValueAsString(foo) should equal("""---
                                                      |name: "Steve"
                                                      |""".stripMargin)
  }

  test("get PropertyNamingStrategy") {
    val namingStrategy = mapper.propertyNamingStrategy
    namingStrategy should not be null
  }

  test("simple tests#parse simple") {
    val foo = parse[SimplePerson]("""{"name": "Steve"}""")
    foo should equal(SimplePerson("Steve"))
  }

  test("simple tests#parse CamelCase simple person") {
    val foo = parse[CamelCaseSimplePerson]("""{"myName": "Steve"}""")
    foo should equal(CamelCaseSimplePerson("Steve"))
  }

  test("simple tests#parse json") {
    val person = parse[Person](steveJson)
    person should equal(steve)
  }

  test("simple tests#parse json list of objects") {
    val json = Seq(steveJson, steveJson).mkString("[", ", ", "]")
    val persons = parse[Seq[Person]](json)
    persons should equal(Seq(steve, steve))
  }

  test("simple tests#parse json list of ints") {
    val nums = parse[Seq[Int]]("""[1,2,3]""")
    nums should equal(Seq(1, 2, 3))
  }

  test("simple tests#serialize case class with logging") {
    val steveWithLogging = PersonWithLogging(
      id = 1,
      name = "Steve",
      age = Some(20),
      age_with_default = Some(20),
      nickname = "ace"
    )

    assertJson(steveWithLogging, steveJson)
  }

  test("simple tests#parse json with extra field at end") {
    val person = parse[Person]("""
    {
       "id" : 1,
       "name" : "Steve",
       "age" : 20,
       "age_with_default" : 20,
       "nickname" : "ace",
       "extra" : "extra"
     }
                                """)
    person should equal(steve)
  }

  test("simple tests#parse json with extra field in middle") {
    val person = parse[Person]("""
    {
       "id" : 1,
       "name" : "Steve",
       "age" : 20,
       "extra" : "extra",
       "age_with_default" : 20,
       "nickname" : "ace"
     }
                                """)
    person should equal(steve)
  }

  test("simple tests#parse json with extra field name with dot") {
    val person = parse[PersonWithDottedName]("""
    {
      "id" : 1,
      "name.last" : "Cosenza"
    }
                                              """)

    person should equal(
      PersonWithDottedName(
        id = 1,
        lastName = "Cosenza"
      )
    )
  }

  test("simple tests#parse json with missing 'id' and 'name' field and invalid age field") {
    assertJsonParse[Person](
      """ {
             "age" : "foo",
             "age_with_default" : 20,
             "nickname" : "ace"
          }""",
      withErrors =
        Seq("age: 'foo' is not a valid Integer", "id: field is required", "name: field is required")
    )
  }

  test("simple tests#parse nested json with missing fields") {
    assertJsonParse[Car](
      """
       {
        "id" : 0,
        "make": "Foo",
        "year": 2000,
        "passengers" : [ { "id": "-1", "age": "blah" } ]
       }
      """,
      withErrors = Seq(
        "make: 'Foo' is not a valid CarMake with valid values: Ford, Honda",
        "model: field is required",
        "owners: field is required",
        "passengers.age: 'blah' is not a valid Integer",
        "passengers.name: field is required"
      )
    )
  }

  test("simple test#parse char") {
    assertJsonParse[CaseClassCharacter](
      """
        |{
        |"c" : -1
        |}
      """.stripMargin,
      withErrors = Seq(
        "c: '-1' is not a valid Character"
      )
    )
  }

  test("simple tests#parse json with missing 'nickname' field that has a string default") {
    val person = parse[Person]("""
    {
       "id" : 1,
       "name" : "Steve",
       "age" : 20,
       "age_with_default" : 20
     }""")
    person should equal(steve.copy(nickname = "unknown"))
  }

  test(
    "simple tests#parse json with missing 'age' field that is an Option without a default should succeed"
  ) {
    parse[Person]("""
        {
           "id" : 1,
           "name" : "Steve",
           "age_with_default" : 20,
           "nickname" : "bob"
         }
      """)
  }

  test("simple tests#parse json into JsonNode") {
    parse[JsonNode](steveJson)
  }

  test("simple tests#generate json") {
    assertJson(steve, steveJson)
  }

  test("simple tests#generate then parse") {
    val json = generate(steve)
    val person = parse[Person](json)
    person should equal(steve)
  }

  test("simple tests#generate then parse Either type") {
    type T = Either[String, Int]
    val l: T = Left("Q?")
    val r: T = Right(42)
    assertJson(l, """{"l":"Q?"}""")
    assertJson(r, """{"r":42}""")
  }

  test("simple tests#generate then parse nested case class") {
    val origCar = Car(1, CarMake.Ford, "Explorer", 2001, Seq(steve, steve))
    val carJson = generate(origCar)
    val car = parse[Car](carJson)
    car should equal(origCar)
  }

  test("simple tests#Prevent overwriting val in case class") {
    parse[CaseClassWithVal]("""{
        "name" : "Bob",
        "type" : "dog"
       }""") should equal(CaseClassWithVal("Bob"))
  }

  test("simple tests#parse WithEmptyJsonProperty then write and see if it equals original") {
    val withEmptyJsonProperty =
      """{
        |  "foo" : "abc"
        |}""".stripMargin
    val obj = parse[WithEmptyJsonProperty](withEmptyJsonProperty)
    val json = mapper.writePrettyString(obj)
    json should equal(withEmptyJsonProperty)
  }

  test("simple tests#parse WithNonemptyJsonProperty then write and see if it equals original") {
    val withNonemptyJsonProperty =
      """{
        |  "bar" : "abc"
        |}""".stripMargin
    val obj = parse[WithNonemptyJsonProperty](withNonemptyJsonProperty)
    val json = mapper.writePrettyString(obj)
    json should equal(withNonemptyJsonProperty)
  }

  test(
    "simple tests#parse WithoutJsonPropertyAnnotation then write and see if it equals original") {
    val withoutJsonPropertyAnnotation =
      """{
        |  "foo" : "abc"
        |}""".stripMargin
    val obj = parse[WithoutJsonPropertyAnnotation](withoutJsonPropertyAnnotation)
    val json = mapper.writePrettyString(obj)
    json should equal(withoutJsonPropertyAnnotation)
  }

  test(
    "simple tests#use default Jackson mapper without setting naming strategy to see if it remains camelCase to verify default Jackson behavior"
  ) {
    val objMapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    objMapper.registerModule(DefaultScalaModule)

    val response = objMapper.writeValueAsString(NamingStrategyJsonProperty("abc"))
    response should equal("""{"longFieldName":"abc"}""")
  }

  test(
    "simple tests#use default Jackson mapper after setting naming strategy and see if it changes to verify default Jackson behavior"
  ) {
    val objMapper = new JacksonObjectMapper with JacksonScalaObjectMapper
    objMapper.registerModule(DefaultScalaModule)
    objMapper.setPropertyNamingStrategy(new PropertyNamingStrategies.SnakeCaseStrategy)

    val response = objMapper.writeValueAsString(NamingStrategyJsonProperty("abc"))
    response should equal("""{"long_field_name":"abc"}""")
  }

  test("enums#simple") {
    parse[CaseClassWithEnum]("""{
        "name" : "Bob",
        "make" : "ford"
       }""") should equal(CaseClassWithEnum("Bob", CarMakeEnum.ford))
  }

  test("enums#complex") {
    JsonDiff.assertDiff(
      CaseClassWithComplexEnums(
        "Bob",
        CarMakeEnum.vw,
        Some(CarMakeEnum.ford),
        Seq(CarMakeEnum.vw, CarMakeEnum.ford),
        Set(CarMakeEnum.ford, CarMakeEnum.vw)
      ),
      parse[CaseClassWithComplexEnums]("""{
        "name" : "Bob",
        "make" : "vw",
        "make_opt" : "ford",
        "make_seq" : ["vw", "ford"],
        "make_set" : ["ford", "vw"]
       }""")
    )
  }

  test("enums#complex case insensitive") {
    val caseInsensitiveEnumMapper =
      ScalaObjectMapper.builder
        .withAdditionalMapperConfigurationFn(
          _.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true))
        .objectMapper

    JsonDiff.assertDiff(
      CaseClassWithComplexEnums(
        "Bob",
        CarMakeEnum.vw,
        Some(CarMakeEnum.ford),
        Seq(CarMakeEnum.vw, CarMakeEnum.ford),
        Set(CarMakeEnum.ford, CarMakeEnum.vw)
      ),
      caseInsensitiveEnumMapper.parse[CaseClassWithComplexEnums]("""{
        "name" : "Bob",
        "make" : "VW",
        "make_opt" : "Ford",
        "make_seq" : ["vW", "foRD"],
        "make_set" : ["fORd", "Vw"]
       }""")
    )
  }

  test("enums#invalid enum entry") {
    val e = intercept[CaseClassMappingException] {
      parse[CaseClassWithEnum]("""{
        "name" : "Bob",
        "make" : "foo"
       }""")
    }
    e.errors.map { _.getMessage } should equal(
      Seq("""make: 'foo' is not a valid CarMakeEnum with valid values: ford, vw""")
    )
  }

  test("LocalDateTime support") {
    parse[CaseClassWithDateTime]("""{
         "date_time" : "2014-05-30T03:57:59.302Z"
       }""") should equal(
      CaseClassWithDateTime(
        LocalDateTime.parse("2014-05-30T03:57:59.302Z", ISO8601DateTimeFormatter))
    )
  }

  test("invalid LocalDateTime 1") {
    assertJsonParse[CaseClassWithDateTime](
      """{
         "date_time" : ""
       }""",
      withErrors = Seq("date_time: error parsing ''"))
  }

  test("invalid LocalDateTime 2") {
    assertJsonParse[CaseClassWithIntAndDateTime](
      """{
         "name" : "Bob",
         "age" : "old",
         "age2" : "1",
         "age3" : "",
         "date_time" : "today",
         "date_time2" : "1",
         "date_time3" : -1,
         "date_time4" : ""
       }""",
      withErrors = Seq(
        "age3: '' is not a valid Integer",
        "age: 'old' is not a valid Integer",
        "date_time2: '1' is not a valid LocalDateTime",
        "date_time3: '' is not a valid LocalDateTime",
        "date_time4: error parsing ''",
        "date_time: 'today' is not a valid LocalDateTime"
      )
    )
  }

  test("TwitterUtilDuration serialize") {
    val serialized = mapper.writeValueAsString(
      CaseClassWithTwitterUtilDuration(Duration.fromTimeUnit(3L, TimeUnit.HOURS))
    )
    serialized should equal("""{"duration":"3.hours"}""")
  }

  test("TwitterUtilDuration deserialize") {
    parse[CaseClassWithTwitterUtilDuration]("""{
        "duration": "3.hours"
      }""") should equal(
      CaseClassWithTwitterUtilDuration(Duration.fromTimeUnit(3L, TimeUnit.HOURS))
    )
  }

  test("TwitterUtilDuration deserialize invalid") {
    assertJsonParse[CaseClassWithTwitterUtilDuration](
      """{"duration": "3.unknowns"}""",
      withErrors = Seq("duration: Invalid unit: unknowns")
    )
  }

  test("escaped fields#long") {
    parse[CaseClassWithEscapedLong]("""{
        "1-5" : 10
     }""") should equal(CaseClassWithEscapedLong(`1-5` = 10))
  }

  test("escaped fields#string") {
    parse[CaseClassWithEscapedString]("""{
        "1-5" : "10"
     }""") should equal(CaseClassWithEscapedString(`1-5` = "10"))
  }

  test("escaped fields#non-unicode escaped") {
    parse[CaseClassWithEscapedNormalString]("""{
          "a" : "foo"
         }""") should equal(CaseClassWithEscapedNormalString("foo"))
  }

  test("escaped fields#unicode and non-unicode fields") {
    parse[UnicodeNameCaseClass]("""{"winning-id":23,"name":"the name of this"}""") should equal(
      UnicodeNameCaseClass(23, "the name of this")
    )
  }

  test("wrapped values#support value class") {
    val orig = WrapperValueClass(1)
    val obj = parse[WrapperValueClass](generate(orig))
    orig should equal(obj)
  }

  test("wrapped values#direct WrappedValue for Int") {
    val origObj = WrappedValueInt(1)
    val obj = parse[WrappedValueInt](generate(origObj))
    origObj should equal(obj)
  }

  test("wrapped values#direct WrappedValue for String") {
    val origObj = WrappedValueString("1")
    val obj = parse[WrappedValueString](generate(origObj))
    origObj should equal(obj)
  }

  test(
    "wrapped values#direct WrappedValue for String when asked to parse wrapped json object should throw exception"
  ) {
    intercept[JsonMappingException] {
      parse[WrappedValueString]("""{"value": "1"}""")
    }
  }

  test("wrapped values#direct WrappedValue for Long") {
    val origObj = WrappedValueLong(1)
    val obj = parse[WrappedValueLong](generate(origObj))
    origObj should equal(obj)
  }

  test("wrapped values#WrappedValue for Int") {
    val origObj = WrappedValueIntInObj(WrappedValueInt(1))
    val json = mapper.writeValueAsString(origObj)
    val obj = parse[WrappedValueIntInObj](json)
    origObj should equal(obj)
  }

  test("wrapped values#WrappedValue for String") {
    val origObj = WrappedValueStringInObj(WrappedValueString("1"))
    val json = mapper.writeValueAsString(origObj)
    val obj = parse[WrappedValueStringInObj](json)
    origObj should equal(obj)
  }

  test("wrapped values#WrappedValue for Long") {
    val origObj = WrappedValueLongInObj(WrappedValueLong(11111111))
    val json = mapper.writeValueAsString(origObj)
    val obj = parse[WrappedValueLongInObj](json)
    origObj should equal(obj)
  }

  test("wrapped values#Seq[WrappedValue]") {
    generate(Seq(WrappedValueLong(11111111))) should be("""[11111111]""")
  }

  test("wrapped values#Map[WrappedValueString, String]") {
    val obj = Map(WrappedValueString("11111111") -> "asdf")
    val json = generate(obj)
    json should be("""{"11111111":"asdf"}""")
    parse[Map[WrappedValueString, String]](json) should be(obj)
  }

  test("wrapped values#Map[WrappedValueLong, String]") {
    assertJson(Map(WrappedValueLong(11111111) -> "asdf"), """{"11111111":"asdf"}""")
  }

  test("wrapped values#deser Map[Long, String]") {
    val obj = parse[Map[Long, String]]("""{"11111111":"asdf"}""")
    val expected = Map(11111111L -> "asdf")
    obj should equal(expected)
  }

  test("wrapped values#deser Map[String, String]") {
    parse[Map[String, String]]("""{"11111111":"asdf"}""") should
      be(Map("11111111" -> "asdf"))
  }

  test("wrapped values#Map[String, WrappedValueLong]") {
    generate(Map("asdf" -> WrappedValueLong(11111111))) should be("""{"asdf":11111111}""")
  }

  test("wrapped values#Map[String, WrappedValueString]") {
    generate(Map("asdf" -> WrappedValueString("11111111"))) should be("""{"asdf":"11111111"}""")
  }

  test("wrapped values#object with Map[WrappedValueString, String]") {
    assertJson(
      obj = WrappedValueStringMapObject(Map(WrappedValueString("11111111") -> "asdf")),
      expected = """{
              "map" : {
                "11111111":"asdf"
           }
         }"""
    )
  }

  test("fail when CaseClassWithSeqLongs with null array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqOfLongs]("""{"seq": [null]}""")
    }
  }

  test("fail when CaseClassWithSeqWrappedValueLong with null array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqWrappedValueLong]("""{"seq": [null]}""")
    }
  }

  test("fail when CaseClassWithArrayWrappedValueLong with null array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithArrayWrappedValueLong]("""{"array": [null]}""")
    }
  }

  test("fail when CaseClassWithSeqWrappedValueLongWithValidation with null array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqWrappedValueLongWithValidation]("""{"seq": [null]}""")
    }
  }

  test("fail when CaseClassWithSeqWrappedValueLongWithValidation with invalid field") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqWrappedValueLongWithValidation]("""{"seq": [{"value": 0}]}""")
    }
  }

  test("fail when CaseClassWithSeqOfCaseClassWithValidation with null array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqOfCaseClassWithValidation]("""{"seq": [null]}""")
    }
  }

  test("fail when CaseClassWithSeqOfCaseClassWithValidation with invalid array element") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqOfCaseClassWithValidation]("""{"seq": [0]}""")
    }
  }

  test("fail when CaseClassWithSeqOfCaseClassWithValidation with null field in object") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqOfCaseClassWithValidation]("""{"seq": [{"value": null}]}""")
    }
  }

  test("fail when CaseClassWithSeqOfCaseClassWithValidation with invalid field in object") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithSeqOfCaseClassWithValidation]("""{"seq": [{"value": 0}]}""")
    }
  }

  test("fail when CaseClassWithArrayLong with null field in object") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithArrayLong]("""{"array": [null]}""")
    }
  }

  test("fail when CaseClassWithArrayBoolean with null field in object") {
    intercept[CaseClassMappingException] {
      parse[CaseClassWithArrayBoolean]("""{"array": [null]}""")
    }
  }

  // ========================================================
  // Jerkson Inspired/Copied Tests Below
  test("A basic case class generates a JSON object with matching field values") {
    generate(CaseClass(1, "Coda")) should be("""{"id":1,"name":"Coda"}""")
  }

  test("A basic case class is parsable from a JSON object with corresponding fields") {
    parse[CaseClass]("""{"id":111,"name":"Coda"}""") should be(CaseClass(111L, "Coda"))
  }

  test("A basic case class is parsable from a JSON object with extra fields") {
    parse[CaseClass]("""{"id":1,"name":"Coda","derp":100}""") should be(CaseClass(1, "Coda"))
  }

  test("A basic case class is not parsable from an incomplete JSON object") {
    intercept[Exception] {
      parse[CaseClass]("""{"id":1}""")
    }
  }

  test("A case class with lazy fields generates a JSON object with those fields evaluated") {
    generate(CaseClassWithLazyVal(1)) should be("""{"id":1,"woo":"yeah"}""")
  }

  test("A case class with lazy fields is parsable from a JSON object without those fields") {
    parse[CaseClassWithLazyVal]("""{"id":1}""") should be(CaseClassWithLazyVal(1))
  }

  test("A case class with lazy fields is not parsable from an incomplete JSON object") {
    intercept[Exception] {
      parse[CaseClassWithLazyVal]("""{}""")
    }
  }

  test("A case class with ignored members generates a JSON object without those fields") {
    generate(CaseClassWithIgnoredField(1)) should be("""{"id":1}""")
    generate(CaseClassWithIgnoredFieldsExactMatch(1)) should be("""{"id":1}""")
    generate(CaseClassWithIgnoredFieldsMatchAfterToSnakeCase(1)) should be("""{"id":1}""")
  }

  test("A case class with some ignored members is not parsable from an incomplete JSON object") {
    intercept[Exception] {
      parse[CaseClassWithIgnoredField]("""{}""")
    }
    intercept[Exception] {
      parse[CaseClassWithIgnoredFieldsMatchAfterToSnakeCase]("""{}""")
    }
  }

  test("A case class with transient members generates a JSON object without those fields") {
    generate(CaseClassWithTransientField(1)) should be("""{"id":1}""")
  }

  test("A case class with transient members is parsable from a JSON object without those fields") {
    parse[CaseClassWithTransientField]("""{"id":1}""") should be(CaseClassWithTransientField(1))
  }

  test("A case class with some transient members is not parsable from an incomplete JSON object") {
    intercept[Exception] {
      parse[CaseClassWithTransientField]("""{}""")
    }
  }

  test("A case class with lazy vals generates a JSON object without those fields") {
    // jackson-module-scala serializes lazy vals and the only work around is to use @JsonIgnore on the field
    // see: https://github.com/FasterXML/jackson-module-scala/issues/113
    generate(CaseClassWithLazyField(1)) should be("""{"id":1}""")
  }

  test("A case class with lazy vals is parsable from a JSON object without those fields") {
    parse[CaseClassWithLazyField]("""{"id":1}""") should be(CaseClassWithLazyField(1))
  }

  test(
    "A case class with an overloaded field generates a JSON object with the nullary version of that field"
  ) {
    generate(CaseClassWithOverloadedField(1)) should be("""{"id":1}""")
  }

  test("A case class with an Option[String] member generates a field if the member is Some") {
    generate(CaseClassWithOption(Some("what"))) should be("""{"value":"what"}""")
  }

  test(
    "A case class with an Option[String] member is parsable from a JSON object with that field") {
    parse[CaseClassWithOption]("""{"value":"what"}""") should be(CaseClassWithOption(Some("what")))
  }

  test(
    "A case class with an Option[String] member doesn't generate a field if the member is None") {
    generate(CaseClassWithOption(None)) should be("""{}""")
  }

  test(
    "A case class with an Option[String] member is parsable from a JSON object without that field"
  ) {
    parse[CaseClassWithOption]("""{}""") should be(CaseClassWithOption(None))
  }

  test(
    "A case class with an Option[String] member is parsable from a JSON object with a null value for that field"
  ) {
    parse[CaseClassWithOption]("""{"value":null}""") should be(CaseClassWithOption(None))
  }

  test(
    "A case class with an Option[Int] member and validation annotation is parsable from a JSON object with value that passes the validation") {
    parse[CaseClassWithOptionAndValidation]("""{ "towing_capacity": 10000 }""") should be(
      CaseClassWithOptionAndValidation(Some(10000)))
  }

  test(
    "A case class with an Option[Int] member and validation annotation is parsable from a JSON object with null value") {
    parse[CaseClassWithOptionAndValidation]("""{ "towing_capacity": null }""") should be(
      CaseClassWithOptionAndValidation(None))
  }

  test(
    "A case class with an Option[Int] member and validation annotation is parsable from a JSON without that field") {
    parse[CaseClassWithOptionAndValidation]("""{}""") should be(
      CaseClassWithOptionAndValidation(None))
  }

  test(
    "A case class with an Option[Int] member and validation annotation is parsable from a JSON object with value that fails the validation") {
    val e = intercept[CaseClassMappingException] {
      parse[CaseClassWithOptionAndValidation]("""{ "towing_capacity": 1 }""") should be(
        CaseClassWithOptionAndValidation(Some(1)))
    }
    e.errors.head.getMessage should be("towing_capacity: must be greater than or equal to 100")
  }

  test(
    "A case class with an Option[Boolean] member and incompatible validation annotation is not parsable from a JSON object") {
    val e = intercept[jakarta.validation.UnexpectedTypeException] {
      parse[CaseClassWithOptionAndIncompatibleValidation]("""{ "are_you": true }""") should be(
        CaseClassWithOptionAndIncompatibleValidation(Some(true)))
    }
  }

  test(
    "A case class with an Option[Boolean] member and incompatible validation annotation is parsable from a JSON object with null value") {
    parse[CaseClassWithOptionAndIncompatibleValidation]("""{ "are_you": null }""") should be(
      CaseClassWithOptionAndIncompatibleValidation(None))
  }

  test(
    "A case class with an Option[Boolean] member and incompatible validation annotation is parsable from a JSON object without that field") {
    parse[CaseClassWithOptionAndIncompatibleValidation]("""{}""") should be(
      CaseClassWithOptionAndIncompatibleValidation(None))
  }

  test("A case class with a JsonNode member generates a field of the given type") {
    generate(CaseClassWithJsonNode(new IntNode(2))) should be("""{"value":2}""")
  }

  test("issues#standalone map") {
    val map = parse[Map[String, String]]("""
      {
        "one": "two"
      }
      """)
    map should equal(Map("one" -> "two"))
  }

  test("issues#case class with map") {
    val obj = parse[CaseClassWithMap]("""
      {
        "map": {"one": "two"}
      }
      """)
    obj.map should equal(Map("one" -> "two"))
  }

  test("issues#case class with multiple constructors") {
    intercept[AssertionError] {
      parse[CaseClassWithTwoConstructors]("""{"id":42,"name":"TwoConstructorsNoDefault"}"""")
    }
  }

  test("issues#case class with multiple (3) constructors") {
    intercept[AssertionError] {
      parse[CaseClassWithThreeConstructors]("""{"id":42,"name":"ThreeConstructorsNoDefault"}"""")
    }
  }

  test("issues#case class nested within an object") {
    parse[NestedCaseClassInObject]("""
      {
        "id": "foo"
      }
      """) should equal(NestedCaseClassInObject(id = "foo"))
  }

  test(
    "issues#case class nested within an object with member that is also a case class in an object"
  ) {
    parse[NestedCaseClassInObjectWithNestedCaseClassInObjectParam](
      """
      {
        "nested": {
          "id": "foo"
        }
      }
      """
    ) should equal(
      NestedCaseClassInObjectWithNestedCaseClassInObjectParam(
        nested = NestedCaseClassInObject(id = "foo")
      )
    )
  }

  test("deserialization#case class nested within a companion object works") {
    parse[NestedCaseClassInCompanion](
      """
      {
        "id": "foo"
      }
      """
    ) should equal(NestedCaseClassInCompanion(id = "foo"))
  }

  case class NestedCaseClassInClass(id: String)
  test("deserialization#case class nested within a class fails") {
    intercept[AssertionError] {
      parse[NestedCaseClassInClass]("""
        {
          "id": "foo"
        }
        """) should equal(NestedCaseClassInClass(id = "foo"))
    }
  }

  test("deserialization#case class with set of longs") {
    val obj = parse[CaseClassWithSetOfLongs]("""
      {
        "set": [5000000, 1, 2, 3, 1000]
      }
      """)
    obj.set.toSeq.sorted should equal(Seq(1L, 2L, 3L, 1000L, 5000000L))
  }

  test("deserialization#case class with seq of longs") {
    val obj = parse[CaseClassWithSeqOfLongs]("""
      {
        "seq": [%s]
      }
      """.format(1004 to 1500 mkString ","))
    obj.seq.sorted should equal(1004 to 1500)
  }

  test("deserialization#nested case class with collection of longs") {
    val idsStr = 1004 to 1500 mkString ","
    val obj = parse[CaseClassWithNestedSeqLong]("""
      {
        "seq_class" : {"seq": [%s]},
        "set_class" : {"set": [%s]}
      }
      """.format(idsStr, idsStr))
    obj.seqClass.seq.sorted should equal(1004 to 1500)
    obj.setClass.set.toSeq.sorted should equal(1004 to 1500)
  }

  test("deserialization#complex without companion class") {
    val json =
      """{
                 "entity_ids" : [ 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057, 1058, 1059, 1060, 1061, 1062, 1063, 1064, 1065, 1066, 1067, 1068, 1069, 1070, 1071, 1072, 1073, 1074, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083, 1084, 1085, 1086, 1087, 1088, 1089, 1090, 1091, 1092, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112, 1113, 1114, 1115, 1116, 1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126, 1127, 1128, 1129, 1130, 1131, 1132, 1133, 1134, 1135, 1136, 1137, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1152, 1153, 1154, 1155, 1156, 1157, 1158, 1159, 1160, 1161, 1162, 1163, 1164, 1165, 1166, 1167, 1168, 1169, 1170, 1171, 1172, 1173, 1174, 1175, 1176, 1177, 1178, 1179, 1180, 1181, 1182, 1183, 1184, 1185, 1186, 1187, 1188, 1189, 1190, 1191, 1192, 1193, 1194, 1195, 1196, 1197, 1198, 1199, 1200, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211, 1212, 1213, 1214, 1215, 1216, 1217, 1218, 1219, 1220, 1221, 1222, 1223, 1224, 1225, 1226, 1227, 1228, 1229, 1230, 1231, 1232, 1233, 1234, 1235, 1236, 1237, 1238, 1239, 1240, 1241, 1242, 1243, 1244, 1245, 1246, 1247, 1248, 1249, 1250, 1251, 1252, 1253, 1254, 1255, 1256, 1257, 1258, 1259, 1260, 1261, 1262, 1263, 1264, 1265, 1266, 1267, 1268, 1269, 1270, 1271, 1272, 1273, 1274, 1275, 1276, 1277, 1278, 1279, 1280, 1281, 1282, 1283, 1284, 1285, 1286, 1287, 1288, 1289, 1290, 1291, 1292, 1293, 1294, 1295, 1296, 1297, 1298, 1299, 1300, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1311, 1312, 1313, 1314, 1315, 1316, 1317, 1318, 1319, 1320, 1321, 1322, 1323, 1324, 1325, 1326, 1327, 1328, 1329, 1330, 1331, 1332, 1333, 1334, 1335, 1336, 1337, 1338, 1339, 1340, 1341, 1342, 1343, 1344, 1345, 1346, 1347, 1348, 1349, 1350, 1351, 1352, 1353, 1354, 1355, 1356, 1357, 1358, 1359, 1360, 1361, 1362, 1363, 1364, 1365, 1366, 1367, 1368, 1369, 1370, 1371, 1372, 1373, 1374, 1375, 1376, 1377, 1378, 1379, 1380, 1381, 1382, 1383, 1384, 1385, 1386, 1387, 1388, 1389, 1390, 1391, 1392, 1393, 1394, 1395, 1396, 1397, 1398, 1399, 1400, 1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1411, 1412, 1413, 1414, 1415, 1416, 1417, 1418, 1419, 1420, 1421, 1422, 1423, 1424, 1425, 1426, 1427, 1428, 1429, 1430, 1431, 1432, 1433, 1434, 1435, 1436, 1437, 1438, 1439, 1440, 1441, 1442, 1443, 1444, 1445, 1446, 1447, 1448, 1449, 1450, 1451, 1452, 1453, 1454, 1455, 1456, 1457, 1458, 1459, 1460, 1461, 1462, 1463, 1464, 1465, 1466, 1467, 1468, 1469, 1470, 1471, 1472, 1473, 1474, 1475, 1476, 1477, 1478, 1479, 1480, 1481, 1482, 1483, 1484, 1485, 1486, 1487, 1488, 1489, 1490, 1491, 1492, 1493, 1494, 1495, 1496, 1497, 1498, 1499, 1500 ],
                 "previous_cursor" : "$",
                 "next_cursor" : "2892e7ab37d44c6a15b438f78e8d76ed$"
               }"""
    val entityIdsResponse = parse[TestEntityIdsResponse](json)
    entityIdsResponse.entityIds.sorted.size should be > 0
  }

  test("deserialization#complex with companion class") {
    val json =
      """{
                 "entity_ids" : [ 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029, 1030, 1031, 1032, 1033, 1034, 1035, 1036, 1037, 1038, 1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046, 1047, 1048, 1049, 1050, 1051, 1052, 1053, 1054, 1055, 1056, 1057, 1058, 1059, 1060, 1061, 1062, 1063, 1064, 1065, 1066, 1067, 1068, 1069, 1070, 1071, 1072, 1073, 1074, 1075, 1076, 1077, 1078, 1079, 1080, 1081, 1082, 1083, 1084, 1085, 1086, 1087, 1088, 1089, 1090, 1091, 1092, 1093, 1094, 1095, 1096, 1097, 1098, 1099, 1100, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108, 1109, 1110, 1111, 1112, 1113, 1114, 1115, 1116, 1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126, 1127, 1128, 1129, 1130, 1131, 1132, 1133, 1134, 1135, 1136, 1137, 1138, 1139, 1140, 1141, 1142, 1143, 1144, 1145, 1146, 1147, 1148, 1149, 1150, 1151, 1152, 1153, 1154, 1155, 1156, 1157, 1158, 1159, 1160, 1161, 1162, 1163, 1164, 1165, 1166, 1167, 1168, 1169, 1170, 1171, 1172, 1173, 1174, 1175, 1176, 1177, 1178, 1179, 1180, 1181, 1182, 1183, 1184, 1185, 1186, 1187, 1188, 1189, 1190, 1191, 1192, 1193, 1194, 1195, 1196, 1197, 1198, 1199, 1200, 1201, 1202, 1203, 1204, 1205, 1206, 1207, 1208, 1209, 1210, 1211, 1212, 1213, 1214, 1215, 1216, 1217, 1218, 1219, 1220, 1221, 1222, 1223, 1224, 1225, 1226, 1227, 1228, 1229, 1230, 1231, 1232, 1233, 1234, 1235, 1236, 1237, 1238, 1239, 1240, 1241, 1242, 1243, 1244, 1245, 1246, 1247, 1248, 1249, 1250, 1251, 1252, 1253, 1254, 1255, 1256, 1257, 1258, 1259, 1260, 1261, 1262, 1263, 1264, 1265, 1266, 1267, 1268, 1269, 1270, 1271, 1272, 1273, 1274, 1275, 1276, 1277, 1278, 1279, 1280, 1281, 1282, 1283, 1284, 1285, 1286, 1287, 1288, 1289, 1290, 1291, 1292, 1293, 1294, 1295, 1296, 1297, 1298, 1299, 1300, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310, 1311, 1312, 1313, 1314, 1315, 1316, 1317, 1318, 1319, 1320, 1321, 1322, 1323, 1324, 1325, 1326, 1327, 1328, 1329, 1330, 1331, 1332, 1333, 1334, 1335, 1336, 1337, 1338, 1339, 1340, 1341, 1342, 1343, 1344, 1345, 1346, 1347, 1348, 1349, 1350, 1351, 1352, 1353, 1354, 1355, 1356, 1357, 1358, 1359, 1360, 1361, 1362, 1363, 1364, 1365, 1366, 1367, 1368, 1369, 1370, 1371, 1372, 1373, 1374, 1375, 1376, 1377, 1378, 1379, 1380, 1381, 1382, 1383, 1384, 1385, 1386, 1387, 1388, 1389, 1390, 1391, 1392, 1393, 1394, 1395, 1396, 1397, 1398, 1399, 1400, 1401, 1402, 1403, 1404, 1405, 1406, 1407, 1408, 1409, 1410, 1411, 1412, 1413, 1414, 1415, 1416, 1417, 1418, 1419, 1420, 1421, 1422, 1423, 1424, 1425, 1426, 1427, 1428, 1429, 1430, 1431, 1432, 1433, 1434, 1435, 1436, 1437, 1438, 1439, 1440, 1441, 1442, 1443, 1444, 1445, 1446, 1447, 1448, 1449, 1450, 1451, 1452, 1453, 1454, 1455, 1456, 1457, 1458, 1459, 1460, 1461, 1462, 1463, 1464, 1465, 1466, 1467, 1468, 1469, 1470, 1471, 1472, 1473, 1474, 1475, 1476, 1477, 1478, 1479, 1480, 1481, 1482, 1483, 1484, 1485, 1486, 1487, 1488, 1489, 1490, 1491, 1492, 1493, 1494, 1495, 1496, 1497, 1498, 1499, 1500 ],
                 "previous_cursor" : "$",
                 "next_cursor" : "2892e7ab37d44c6a15b438f78e8d76ed$"
               }"""
    val entityIdsResponse = parse[TestEntityIdsResponseWithCompanion](json)
    entityIdsResponse.entityIds.sorted.size should be > 0
  }

  val json = """
             {
               "map": {
                 "one": "two"
               },
               "set": [1, 2, 3],
               "string": "woo",
               "list": [4, 5, 6],
               "seq": [7, 8, 9],
               "sequence": [10, 11, 12],
               "collection": [13, 14, 15],
               "indexed_seq": [16, 17, 18],
               "random_access_seq": [19, 20, 21],
               "vector": [22, 23, 24],
               "big_decimal": 12.0,
               "big_int": 13,
               "int": 1,
               "long": 2,
               "char": "x",
               "bool": false,
               "short": 14,
               "byte": 15,
               "float": 34.5,
               "double": 44.9,
               "any": true,
               "any_ref": "wah",
               "int_map": {
                 "1": "1"
               },
               "long_map": {
                 "2": 2
               }
             }"""

  test(
    "deserialization#case class with members of all ScalaSig types " +
      "is parsable from a JSON object with those fields"
  ) {
    val got = parse[CaseClassWithAllTypes](json)
    val expected = CaseClassWithAllTypes(
      map = Map("one" -> "two"),
      set = Set(1, 2, 3),
      string = "woo",
      list = List(4, 5, 6),
      seq = Seq(7, 8, 9),
      indexedSeq = IndexedSeq(16, 17, 18),
      vector = Vector(22, 23, 24),
      bigDecimal = BigDecimal("12.0"),
      bigInt = 13,
      int = 1,
      long = 2L,
      char = 'x',
      bool = false,
      short = 14,
      byte = 15,
      float = 34.5f,
      double = 44.9d,
      any = true,
      anyRef = "wah",
      intMap = Map(1 -> 1),
      longMap = Map(2L -> 2L)
    )

    got should be(expected)
  }

  test("deserialization# case class that throws an exception is not parsable from a JSON object") {
    intercept[JsonMappingException] {
      parse[CaseClassWithException]("""{}""")
    }
  }

  test("deserialization#case class nested inside of an object is parsable from a JSON object") {
    parse[OuterObject.NestedCaseClass]("""{"id": 1}""") should be(OuterObject.NestedCaseClass(1))
  }

  test(
    "deserialization#case class nested inside of an object nested inside of an object is parsable from a JSON object"
  ) {
    parse[OuterObject.InnerObject.SuperNestedCaseClass]("""{"id": 1}""") should be(
      OuterObject.InnerObject.SuperNestedCaseClass(1)
    )
  }

  test("A case class with array members is parsable from a JSON object") {
    val jsonStr = """
      {
        "one":"1",
        "two":["a","b","c"],
        "three":[1,2,3],
        "four":[4, 5],
        "five":["x", "y"],
        "bools":["true", false],
        "bytes":[1,2],
        "doubles":[1,5.0],
        "floats":[1.1, 22]
      }
    """

    val c = parse[CaseClassWithArrays](jsonStr)
    c.one should be("1")
    c.two should be(Array("a", "b", "c"))
    c.three should be(Array(1, 2, 3))
    c.four should be(Array(4L, 5L))
    c.five should be(Array('x', 'y'))

    JsonDiff.assertDiff(
      """{"bools":[true,false],"bytes":"AQI=","doubles":[1.0,5.0],"five":"xy","floats":[1.1,22.0],"four":[4,5],"one":"1","three":[1,2,3],"two":["a","b","c"]}""",
      generate(c)
    )
  }

  test("deserialization#case class with collection of Longs array of longs") {
    val c = parse[CaseClassWithArrayLong]("""{"array":[3,1,2]}""")
    c.array.sorted should equal(Array(1, 2, 3))
  }

  test("deserialization#case class with collection of Longs seq of longs") {
    val c = parse[CaseClassWithSeqLong]("""{"seq":[3,1,2]}""")
    c.seq.sorted should equal(Seq(1, 2, 3))
  }

  test("deserialization#case class with an ArrayList of Integers") {
    val c = parse[CaseClassWithArrayListOfIntegers]("""{"arraylist":[3,1,2]}""")
    val l = new java.util.ArrayList[Integer](3)
    l.add(3)
    l.add(1)
    l.add(2)
    c.arraylist should equal(l)
  }

  test("serde#case class with a SortedMap[String, Int]") {
    val origCaseClass = CaseClassWithSortedMap(scala.collection.SortedMap("aggregate" -> 20))
    val caseClassJson = generate(origCaseClass)
    val caseClass = parse[CaseClassWithSortedMap](caseClassJson)
    caseClass should equal(origCaseClass)
  }

  test("serde#case class with a Seq of Longs") {
    val origCaseClass = CaseClassWithSeqOfLongs(Seq(10, 20, 30))
    val caseClassJson = generate(origCaseClass)
    val caseClass = parse[CaseClassWithSeqOfLongs](caseClassJson)
    caseClass should equal(origCaseClass)
  }

  test("deserialization#seq of longs") {
    val seq = parse[Seq[Long]]("""[3,1,2]""")
    seq.sorted should equal(Seq(1L, 2L, 3L))
  }

  test("deserialization#parse seq of longs") {
    val ids = parse[Seq[Long]]("[3,1,2]")
    ids.sorted should equal(Seq(1L, 2L, 3L))
  }

  test("deserialization#handle options and defaults in case class") {
    val bob = parse[Person]("""
      {
        "id" :1,
        "name" : "Bob",
        "age" : 21
      }
      """)
    bob should equal(Person(1, "Bob", None, Some(21), None))
  }

  test("deserialization#missing required field") {
    intercept[CaseClassMappingException] {
      parse[Person]("""
        {
        }
        """)
    }
  }

  test("deserialization#incorrectly specified required field") {
    intercept[CaseClassMappingException] {
      parse[PersonWithThings]("""
        {
          "id" :1,
          "name" : "Bob",
          "age" : 21,
          "things" : {
            "foo" : [
              "IhaveNoKey"
            ]
          }
        }
        """)
    }
  }

  test("serialization#nulls will not render") {
    generate(Person(1, null, null, null)) should equal("""{"id":1,"nickname":"unknown"}""")
  }

  test("deserialization#string wrapper deserialization") {
    val parsedValue = parse[ObjWithTestId]("""
    {
      "id": "5"
    }
      """)
    val expectedValue = ObjWithTestId(TestIdStringWrapper("5"))

    parsedValue should equal(expectedValue)
    parsedValue.id.onlyValue should equal(expectedValue.id.onlyValue)
    parsedValue.id.asString should equal(expectedValue.id.asString)
    parsedValue.id.toString should equal(expectedValue.id.toString)
  }

  test("deserialization#parse input stream") {
    val is = new ByteArrayInputStream("""{"foo": "bar"}""".getBytes)
    mapper.parse[Blah](is) should equal(Blah("bar"))
  }

  test("serialization#ogging Trait fields should be ignored") {
    generate(Group3("123")) should be("""{"id":"123"}""")
  }

  test("deserialization#class with no constructor") {
    parse[NoConstructorArgs]("""{}""")
  }

  //Jackson parses numbers into boolean type without error. see https://jira.codehaus.org/browse/JACKSON-78
  test("deserialization#case class with boolean as number") {
    parse[CaseClassWithBoolean](""" {
            "foo": 100
          }""") should equal(CaseClassWithBoolean(true))
  }

  //Jackson parses numbers into boolean type without error. see https://jira.codehaus.org/browse/JACKSON-78
  test("deserialization#case class with Seq[Boolean]") {
    parse[CaseClassWithSeqBooleans](""" {
            "foos": [100, 5, 0, 9]
          }""") should equal(CaseClassWithSeqBooleans(Seq(true, true, false, true)))
  }

  //Jackson parses numbers into boolean type without error. see https://jira.codehaus.org/browse/JACKSON-78
  test("Seq[Boolean]") {
    parse[Seq[Boolean]]("""[100, 5, 0, 9]""") should equal(Seq(true, true, false, true))
  }

  test("case class with boolean as number 0") {
    parse[CaseClassWithBoolean](""" {
            "foo": 0
          }""") should equal(CaseClassWithBoolean(false))
  }

  test("case class with boolean as string") {
    assertJsonParse[CaseClassWithBoolean](
      """ {
            "foo": "bar"
          }""",
      withErrors = Seq("foo: 'bar' is not a valid Boolean"))
  }

  test("case class with boolean number as string") {
    assertJsonParse[CaseClassWithBoolean](
      """ {
            "foo": "1"
          }""",
      withErrors = Seq("foo: '1' is not a valid Boolean"))
  }

  val msgHiJsonStr = """{"msg":"hi"}"""

  test("parse jsonParser") {
    val jsonNode = mapper.parse[JsonNode]("{}")
    val jsonParser = new TreeTraversingParser(jsonNode)
    mapper.parse[JsonNode](jsonParser) should equal(jsonNode)
  }

  test("writeValue") {
    val os = new ByteArrayOutputStream()
    mapper.writeValue(Map("msg" -> "hi"), os)
    os.close()
    new String(os.toByteArray) should equal(msgHiJsonStr)
  }

  test("writeValueAsBuf") {
    val buf = mapper.writeValueAsBuf(Map("msg" -> "hi"))
    val Buf.Utf8(str) = buf
    str should equal(msgHiJsonStr)
  }

  test("writeStringMapAsBuf") {
    val buf = mapper.writeStringMapAsBuf(Map("msg" -> "hi"))
    val Buf.Utf8(str) = buf
    str should equal(msgHiJsonStr)
  }

  test("writePrettyString") {
    val jsonStr = mapper.writePrettyString("""{"msg": "hi"}""")
    mapper.parse[JsonNode](jsonStr).get("msg").textValue() should equal("hi")
  }

  test("reader") {
    assert(mapper.reader[JsonNode] != null)
  }

  test(
    "deserialization#jackson JsonDeserialize annotations deserializes json to case class with 2 decimal places for mandatory field"
  ) {
    parse[CaseClassWithCustomDecimalFormat](""" {
          "my_big_decimal": 23.1201
        }""") should equal(CaseClassWithCustomDecimalFormat(BigDecimal(23.12), None))
  }

  test("deserialization#jackson JsonDeserialize annotations long with JsonDeserialize") {
    val result = parse[CaseClassWithLongAndDeserializer](""" {
          "long": 12345
        }""")
    result should equal(CaseClassWithLongAndDeserializer(12345))
    result.long should equal(12345L) // promotes the returned integer into a long
  }

  test(
    "deserialization#jackson JsonDeserialize annotations deserializes json to case class with 2 decimal places for option field"
  ) {
    parse[CaseClassWithCustomDecimalFormat](""" {
          "my_big_decimal": 23.1201,
          "opt_my_big_decimal": 23.1201
        }""") should equal(
      CaseClassWithCustomDecimalFormat(BigDecimal(23.12), Some(BigDecimal(23.12)))
    )
  }

  test("deserialization#jackson JsonDeserialize annotations opt long with JsonDeserialize") {
    parse[CaseClassWithOptionLongAndDeserializer]("""
      {
        "opt_long": 12345
      }
      """) should equal(CaseClassWithOptionLongAndDeserializer(Some(12345)))
  }

  test("deserialization#case class in package object") {
    parse[SimplePersonInPackageObject]("""{"name": "Steve"}""") should equal(
      SimplePersonInPackageObject("Steve")
    )
  }

  test("deserialization#case class in package object uses default when name not specified") {
    parse[SimplePersonInPackageObject]("""{}""") should equal(
      SimplePersonInPackageObject()
    )
  }

  test(
    "deserialization#case class in package object without constructor params and parsing an empty json object") {
    parse[SimplePersonInPackageObjectWithoutConstructorParams]("""{}""") should equal(
      SimplePersonInPackageObjectWithoutConstructorParams()
    )
  }

  test(
    "deserialization#case class in package object without constructor params and parsing a json object with extra fields"
  ) {
    parse[SimplePersonInPackageObjectWithoutConstructorParams](
      """{"name": "Steve"}""") should equal(
      SimplePersonInPackageObjectWithoutConstructorParams()
    )
  }

  test("serialization#upport sealed traits and case objects#json serialization") {
    val vin = Random.alphanumeric.take(17).mkString
    val vehicle = Vehicle(vin, Audi)

    mapper.writeValueAsString(vehicle) should equal(s"""{"vin":"$vin","type":"audi"}""")
  }

  test("deserialization#json creator") {
    parse[TestJsonCreator]("""{"s" : "1234"}""") should equal(TestJsonCreator("1234"))
  }

  test("deserialization#json creator with parameterized type") {
    parse[TestJsonCreator2]("""{"strings" : ["1", "2", "3", "4"]}""") should equal(
      TestJsonCreator2(Seq(1, 2, 3, 4)))
  }

  test("deserialization#multiple case class constructors#no annotation") {
    intercept[AssertionError] {
      parse[CaseClassWithMultipleConstructors](
        """
          |{
          |  "number1" : 12345,
          |  "number2" : 65789,
          |  "number3" : 99999
          |}
          |""".stripMargin
      )
    }
  }

  test("deserialization#multiple case class constructors#annotated") {
    parse[CaseClassWithMultipleConstructorsAnnotated](
      """
        |{
        |  "number_as_string1" : "12345",
        |  "number_as_string2" : "65789",
        |  "number_as_string3" : "99999"
        |}
        |""".stripMargin
    ) should equal(
      CaseClassWithMultipleConstructorsAnnotated(12345L, 65789L, 99999L)
    )
  }

  test("deserialization#JsonCreator with Validation") {
    val e = intercept[CaseClassMappingException] {
      // fails validation
      parse[TestJsonCreatorWithValidation]("""{ "s": "" }""")
    }
    e.errors.size should equal(1)
    e.errors.head.getMessage should be("s: must not be empty")
  }

  test("deserialization#JsonCreator with Validation 1") {
    // works with multiple validations
    var e = intercept[CaseClassMappingException] {
      // fails validation
      parse[TestJsonCreatorWithValidations]("""{ "s": "" }""")
    }
    e.errors.size should equal(2)
    // errors are alpha-sorted by message to be stable for testing
    e.errors.head.getMessage should be("s: <empty> not one of [42, 137]")
    e.errors.last.getMessage should be("s: must not be empty")

    e = intercept[CaseClassMappingException] {
      // fails validation
      parse[TestJsonCreatorWithValidations]("""{ "s": "99" }""")
    }
    e.errors.size should equal(1)
    e.errors.head.getMessage should be("s: 99 not one of [42, 137]")

    parse[TestJsonCreatorWithValidations]("""{ "s": "42" }""") should equal(
      TestJsonCreatorWithValidations("42"))

    parse[TestJsonCreatorWithValidations]("""{ "s": "137" }""") should equal(
      TestJsonCreatorWithValidations(137))
  }

  test("deserialization#JsonCreator with Validation 2") {
    val uuid = UUID.randomUUID().toString

    var e = intercept[CaseClassMappingException] {
      // fails validation
      parse[CaseClassWithMultipleConstructorsAnnotatedAndValidations](
        s"""
           |{
           |  "number_as_string1" : "",
           |  "number_as_string2" : "20002",
           |  "third_argument" : "$uuid"
           |}
           |""".stripMargin
      )
    }
    e.errors.size should equal(1)
    e.errors.head.getMessage should be("number_as_string1: must not be empty")

    e = intercept[CaseClassMappingException] {
      // fails validation
      parse[CaseClassWithMultipleConstructorsAnnotatedAndValidations](
        s"""
           |{
           |  "number_as_string1" : "",
           |  "number_as_string2" : "65789",
           |  "third_argument" : "$uuid"
           |}
           |""".stripMargin
      )
    }
    e.errors.size should equal(2)
    // errors are alpha-sorted by message to be stable for testing
    e.errors.head.getMessage should be("number_as_string1: must not be empty")
    e.errors.last.getMessage should be("number_as_string2: 65789 not one of [10001, 20002, 30003]")

    e = intercept[CaseClassMappingException] {
      // fails validation
      parse[CaseClassWithMultipleConstructorsAnnotatedAndValidations](
        s"""
           |{
           |  "number_as_string1" : "",
           |  "number_as_string2" : "65789",
           |  "third_argument" : "foobar"
           |}
           |""".stripMargin
      )
    }
    e.errors.size should equal(3)
    // errors are alpha-sorted by message to be stable for testing
    e.errors.head.getMessage should be("number_as_string1: must not be empty")
    e.errors(1).getMessage should be("number_as_string2: 65789 not one of [10001, 20002, 30003]")
    e.errors(2).getMessage should be("third_argument: must be a valid UUID")

    parse[CaseClassWithMultipleConstructorsAnnotatedAndValidations](
      s"""
        |{
        |  "number_as_string1" : "12345",
        |  "number_as_string2" : "20002",
        |  "third_argument" : "$uuid"
        |}
        |""".stripMargin
    ) should equal(
      CaseClassWithMultipleConstructorsAnnotatedAndValidations(12345L, 20002L, uuid)
    )
  }

  test("deserialization#JsonFormat with c.t.util.Time") {
    val timeString = "2018-09-14T23:20:08.000-07:00"
    val format = new com.twitter.util.TimeFormat(
      "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
      None,
      TimeZone.getTimeZone("UTC"))
    val time: com.twitter.util.Time = format.parse(timeString)

    parse[TimeWithFormat](
      s"""
        |{
        |  "when": "$timeString"
        |}
        |""".stripMargin
    ) should equal(TimeWithFormat(time))
  }

  test("deserialization#complex object") {
    val profiles = Seq(
      LimiterProfile(1, "up"),
      LimiterProfile(2, "down"),
      LimiterProfile(3, "left"),
      LimiterProfile(4, "right"))
    val expected = LimiterProfiles(profiles)

    parse[LimiterProfiles]("""
        |{
        |  "profiles" : [
        |    {
        |      "id" : "1",
        |      "name" : "up"
        |    },
        |    {
        |      "id" : "2",
        |      "name" : "down"
        |    },
        |    {
        |      "id" : "3",
        |      "name" : "left"
        |    },
        |    {
        |      "id" : "4",
        |      "name" : "right"
        |    }
        |  ]
        |}
        |""".stripMargin) should equal(expected)
  }

  test("deserialization#simple class with boxed primitive constructor args") {
    parse[CaseClassWithBoxedPrimitives]("""
        |{
        |  "events" : 2,
        |  "errors" : 0
        |}
        |""".stripMargin) should equal(CaseClassWithBoxedPrimitives(2, 0))
  }

  test("deserialization#complex object with primitives") {
    val json = """
                 |{
                 |  "cluster_name" : "cluster1",
                 |  "zone" : "abcd1",
                 |  "environment" : "devel",
                 |  "job" : "devel-1",
                 |  "owners" : "owning-group",
                 |  "dtab" : "/s#/devel/abcd1/space/devel-1",
                 |  "address" : "local-test.foo.bar.com:1111/owning-group/space/devel-1",
                 |  "enabled" : "true"
                 |}
                 |""".stripMargin

    parse[AddClusterRequest](json) should equal(
      AddClusterRequest(
        clusterName = "cluster1",
        zone = "abcd1",
        environment = "devel",
        job = "devel-1",
        dtab = "/s#/devel/abcd1/space/devel-1",
        address = "local-test.foo.bar.com:1111/owning-group/space/devel-1",
        owners = "owning-group"
      ))
  }

  test("deserialization#inject request field fails with mapping exception") {
    // with an injector and default injectable values but the field cannot be parsed
    // thus we get a case class mapping exception
    // without an injector, the incoming JSON is used to populate the case class, which
    // is empty and thus we also get a case class mapping exception
    intercept[CaseClassMappingException] {
      parse[ClassWithQueryParamDateTimeInject]("""{}""")
    }
  }

  test("deserialization#ignored constructor field - no default value") {
    val json =
      """
        |{
        |  "name" : "Widget",
        |  "description" : "This is a thing."
        |}
        |""".stripMargin

    val e = intercept[CaseClassMappingException] {
      parse[CaseClassIgnoredFieldInConstructorNoDefault](json)
    }
    e.errors.head.getMessage should be("id: ignored field has no default value specified")
  }

  test("ignored constructor field - with default value") {
    val json =
      """
        |{
        |  "name" : "Widget",
        |  "description" : "This is a thing."
        |}
        |""".stripMargin

    val result = parse[CaseClassIgnoredFieldInConstructorWithDefault](json)
    result.id should equal(42L)
    result.name should equal("Widget")
    result.description should equal("This is a thing.")
  }

  test("deserialization#mixin annotations") {
    val points = Points(first = Point(1, 1), second = Point(4, 5))
    val json =
      """
        |{
        |  "first": { "x": 1, "y": 1 },
        |  "second": { "x": 4, "y": 5 }
        |}
        |""".stripMargin

    parse[Points](json) should equal(points)
    generate(points) should be("""{"first":{"x":1,"y":1},"second":{"x":4,"y":5}}""".stripMargin)
  }

  test("deserialization#mixin annotations with validations") {
    val json =
      """
        |{
        |  "first": { "x": -1, "y": 120 },
        |  "second": { "x": 4, "y": 5 }
        |}
        |""".stripMargin

    val e = intercept[CaseClassMappingException] {
      parse[Points](json)
    }
    e.errors.size should equal(2)
    e.errors.head.getMessage should be("first.x: must be greater than or equal to 0")
    e.errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Field, None) =>
        violation.getPropertyPath.toString should equal("Point.x")
        violation.getMessage should equal("must be greater than or equal to 0")
        violation.getInvalidValue should equal(-1)
        violation.getRootBeanClass should equal(classOf[Point])
        violation.getRootBean == null should be(true)
      case _ => fail()
    }
    e.errors.last.getMessage should be("first.y: must be less than or equal to 100")
    e.errors.last.reason.detail match {
      case ValidationError(violation, ValidationError.Field, None) =>
        violation.getPropertyPath.toString should equal("Point.y")
        violation.getMessage should equal("must be less than or equal to 100")
        violation.getInvalidValue should equal(120)
        violation.getRootBeanClass should equal(classOf[Point])
        violation.getRootBean == null should be(true)
      case _ => fail()
    }
  }

  test("deserialization#ignore type with no default fails") {
    val json =
      """
        |{
        |  "name" : "Widget",
        |  "description" : "This is a thing."
        |}
        |""".stripMargin
    val e = intercept[CaseClassMappingException] {
      parse[ContainsAnIgnoreTypeNoDefault](json)
    }
    e.errors.head.getMessage should be("ignored: ignored field has no default value specified")
  }

  test("deserialization#ignore type with default passes") {
    val json =
      """
        |{
        |  "name" : "Widget",
        |  "description" : "This is a thing."
        |}
        |""".stripMargin
    val result = parse[ContainsAnIgnoreTypeWithDefault](json)
    result.ignored should equal(IgnoreMe(42L))
    result.name should equal("Widget")
    result.description should equal("This is a thing.")
  }

  test("deserialization#parse SimpleClassWithInjection fails") {
    // no field injection configured, parsing happens normally
    val json =
      """{
      |  "hello" : "Mom"
      |}""".stripMargin

    val result = parse[SimpleClassWithInjection](json)
    result.hello should equal("Mom")
  }

  test("deserialization#support JsonView") {
    /* using [[Item]] which has @JsonView annotation on all fields:
       Public -> id
       Public -> name
       Internal -> owner
     */
    val json =
      """
        |{
        |    "id": 42,
        |    "name": "My Item"
        |}
        |""".stripMargin

    val item =
      mapper.underlying.readerWithView[Views.Public].forType(classOf[Item]).readValue[Item](json)
    item.id should equal(42L)
    item.name should equal("My Item")
    item.owner should equal("")
  }

  test("deserialization#support JsonView 2") {
    /* using [[Item]] which has @JsonView annotation on all fields:
       Public -> id
       Public -> name
       Internal -> owner
     */
    val json =
      """
        |{
        |    "owner": "Mister Magoo"
        |}
        |""".stripMargin

    val item =
      mapper.underlying.readerWithView[Views.Internal].forType(classOf[Item]).readValue[Item](json)
    item.id should equal(1L)
    item.name should equal("")
    item.owner should equal("Mister Magoo")
  }

  test("deserialization#support JsonView 3") {
    /* using [[ItemSomeViews]] which has no @JsonView annotation on owner field:
       Public -> id
       Public -> name
              -> owner
     */
    val json =
      """
        |{
        |    "id": 42,
        |    "name": "My Item",
        |    "owner": "Mister Magoo"
        |}
        |""".stripMargin

    val item =
      mapper.underlying
        .readerWithView[Views.Public]
        .forType(classOf[ItemSomeViews])
        .readValue[ItemSomeViews](json)
    item.id should equal(42L)
    item.name should equal("My Item")
    item.owner should equal("Mister Magoo")
  }

  test("deserialization#support JsonView 4") {
    /* using [[ItemSomeViews]] which has no @JsonView annotation on owner field:
       Public -> id
       Public -> name
              -> owner
     */
    val json =
      """
        |{
        |    "owner": "Mister Magoo"
        |}
        |""".stripMargin

    val item =
      mapper.underlying
        .readerWithView[Views.Internal]
        .forType(classOf[ItemSomeViews])
        .readValue[ItemSomeViews](json)
    item.id should equal(1L)
    item.name should equal("")
    item.owner should equal("Mister Magoo")
  }

  test("deserialization#support JsonView 5") {
    /* using [[ItemNoDefaultForView]] which has @JsonView annotation on non-defaulted field:
       Public -> name (no default value)
     */
    val json =
      """
        |{
        |    "name": "My Item"
        |}
        |""".stripMargin

    val item =
      mapper.underlying
        .readerWithView[Views.Public]
        .forType(classOf[ItemNoDefaultForView])
        .readValue[ItemNoDefaultForView](json)
    item.name should equal("My Item")
  }

  test("deserialization#support JsonView 6") {
    /* using [[ItemNoDefaultForView]] which has @JsonView annotation on non-defaulted field:
       Public -> name (no default value)
     */

    intercept[CaseClassMappingException] {
      mapper.underlying
        .readerWithView[Views.Public]
        .forType(classOf[ItemNoDefaultForView])
        .readValue[ItemNoDefaultForView]("{}")
    }
  }

  test("deserialization#test @JsonNaming") {
    val json =
      """
        |{
        |  "please-use-kebab-case": true
        |}
        |""".stripMargin

    parse[CaseClassWithKebabCase](json) should equal(CaseClassWithKebabCase(true))

    val snakeCaseMapper = ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying)
    snakeCaseMapper.parse[CaseClassWithKebabCase](json) should equal(CaseClassWithKebabCase(true))

    val camelCaseMapper = ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying)
    camelCaseMapper.parse[CaseClassWithKebabCase](json) should equal(CaseClassWithKebabCase(true))
  }

  test("deserialization#test @JsonNaming 2") {
    val snakeCaseMapper = ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying)

    // case class is marked with @JsonNaming and the default naming strategy is LOWER_CAMEL_CASE
    // which overrides the mapper's configured naming strategy
    val json =
      """
        |{
        |  "thisFieldShouldUseDefaultPropertyNamingStrategy": true
        |}
        |""".stripMargin

    snakeCaseMapper.parse[UseDefaultNamingStrategy](json) should equal(
      UseDefaultNamingStrategy(true))

    // default for mapper is snake_case, but the case class is annotated with @JsonNaming which
    // tells the mapper what property naming strategy to expect.
    parse[UseDefaultNamingStrategy](json) should equal(UseDefaultNamingStrategy(true))
  }

  test("deserialization#test @JsonNaming 3") {
    val camelCaseMapper = ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying)

    val json =
      """
        |{
        |  "thisFieldShouldUseDefaultPropertyNamingStrategy": true
        |}
        |""".stripMargin

    camelCaseMapper.parse[UseDefaultNamingStrategy](json) should equal(
      UseDefaultNamingStrategy(true))

    // default for mapper is snake_case, but the case class is annotated with @JsonNaming which
    // tells the mapper what property naming strategy to expect.
    parse[UseDefaultNamingStrategy](json) should equal(UseDefaultNamingStrategy(true))
  }

  test("deserialization#test @JsonNaming with mixin") {
    val json =
      """
        |{
        |  "will-this-get-the-right-casing": true
        |}
        |""".stripMargin

    parse[CaseClassShouldUseKebabCaseFromMixin](json) should equal(
      CaseClassShouldUseKebabCaseFromMixin(true))

    val snakeCaseMapper = ScalaObjectMapper.snakeCaseObjectMapper(mapper.underlying)
    snakeCaseMapper.parse[CaseClassShouldUseKebabCaseFromMixin](json) should equal(
      CaseClassShouldUseKebabCaseFromMixin(true))

    val camelCaseMapper = ScalaObjectMapper.camelCaseObjectMapper(mapper.underlying)
    camelCaseMapper.parse[CaseClassShouldUseKebabCaseFromMixin](json) should equal(
      CaseClassShouldUseKebabCaseFromMixin(true))
  }

  test("serde#can serialize and deserialize polymorphic types") {
    val view = View(shapes = Seq(Circle(10), Rectangle(5, 5), Circle(3), Rectangle(10, 5)))
    val result = generate(view)
    result should be(
      """{"shapes":[{"type":"circle","radius":10},{"type":"rectangle","width":5,"height":5},{"type":"circle","radius":3},{"type":"rectangle","width":10,"height":5}]}""")

    parse[View](result) should equal(view)
  }

  test("serde#can serialize and deserialize optional polymorphic types") {
    val view =
      OptionalView(shapes = Seq(Circle(10), Rectangle(5, 5)), optional = Some(Rectangle(10, 5)))
    val result = generate(view)
    result should be(
      """{"shapes":[{"type":"circle","radius":10},{"type":"rectangle","width":5,"height":5}],"optional":{"type":"rectangle","width":10,"height":5}}""")

    parse[OptionalView](result) should equal(view)
  }

  test("serde#can deserialize case class with option when missing") {
    val view = OptionalView(shapes = Seq(Circle(10), Rectangle(5, 5)), optional = None)
    val result = generate(view)
    result should equal(
      """{"shapes":[{"type":"circle","radius":10},{"type":"rectangle","width":5,"height":5}]}""")

    parse[OptionalView](result) should equal(view)
  }

  test("nullable deserializer - allows parsing a JSON null into a null type") {
    parse[WithNullableCarMake](
      """{"non_null_car_make": "vw", "nullable_car_make": null}""") should be(
      WithNullableCarMake(nonNullCarMake = CarMakeEnum.vw, nullableCarMake = null))
  }

  test("nullable deserializer - fail on missing value even when null allowed") {
    val e = intercept[CaseClassMappingException] {
      parse[WithNullableCarMake]("""{}""")
    }
    e.errors.size should equal(2)
    e.errors.head.getMessage should equal("non_null_car_make: field is required")
    e.errors.last.getMessage should equal("nullable_car_make: field is required")
  }

  test("nullable deserializer - allow null values when default is provided") {
    parse[WithDefaultNullableCarMake]("""{"nullable_car_make":null}""") should be(
      WithDefaultNullableCarMake(null))
  }

  test("nullable deserializer - use default when null is allowed, but not provided") {
    parse[WithDefaultNullableCarMake]("""{}""") should be(
      WithDefaultNullableCarMake(CarMakeEnum.ford))
  }

  test("deserialization of json array into a string field") {
    val json =
      """
        |{
        |  "value": [{
        |   "languages": "es",
        |   "reviewable": "tweet",
        |   "baz": "too",
        |   "foo": "bar",
        |   "coordinates": "polar"
        | }]
        |}
        |""".stripMargin

    val expected = WithJsonStringType(
      """[{"languages":"es","reviewable":"tweet","baz":"too","foo":"bar","coordinates":"polar"}]""".stripMargin)
    parse[WithJsonStringType](json) should equal(expected)
  }

  test("deserialization of json int into a string field") {
    val json =
      """
        |{
        |  "value": 1
        |}
        |""".stripMargin

    val expected = WithJsonStringType("1")
    parse[WithJsonStringType](json) should equal(expected)
  }

  test("deserialization of json object into a string field") {
    val json =
      """
        |{
        |  "value": {"foo": "bar"}
        |}
        |""".stripMargin

    val expected = WithJsonStringType("""{"foo":"bar"}""")
    parse[WithJsonStringType](json) should equal(expected)
  }
}
