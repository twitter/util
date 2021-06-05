package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.NumberDeserializers.BigDecimalDeserializer
import com.twitter.util.jackson.{
  JacksonScalaObjectMapperType,
  ScalaObjectMapper,
  TestInjectableValue,
  _
}
import com.twitter.util.validation.ScalaValidator
import jakarta.validation.constraints.NotEmpty
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CaseClassFieldTest extends AnyFunSuite with Matchers {
  private[this] val mapper: JacksonScalaObjectMapperType =
    ScalaObjectMapper().underlying

  test("CaseClassField.createFields have field name foo") {
    val fields = deserializerFor(classOf[WithEmptyJsonProperty]).fields

    fields.length should equal(1)
    fields.head.name should equal("foo")
  }

  test("CaseClassField.createFields also have field name foo") {
    val fields = deserializerFor(classOf[WithoutJsonPropertyAnnotation]).fields

    fields.length should equal(1)
    fields.head.name should equal("foo")
  }

  test("CaseClassField.createFields have field name bar") {
    val fields = deserializerFor(classOf[WithNonemptyJsonProperty]).fields

    fields.length should equal(1)
    fields.head.name should equal("bar")
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation") {
    val fields = deserializerFor(classOf[Aum]).fields

    fields.length should equal(2)

    val iField = fields.head
    iField.name should equal("i")
    iField.annotations.length should equal(1)
    iField.annotations.head.annotationType() should be(classOf[JsonProperty])

    val jField = fields.last
    jField.name should equal("j")
    jField.annotations.length should equal(1)
    jField.annotations.head.annotationType() should be(classOf[JsonProperty])
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 2") {
    val fields = deserializerFor(classOf[FooBar]).fields

    fields.length should equal(1)

    val helloField = fields.head
    helloField.name should equal("helloWorld")
    helloField.annotations.length should equal(2)
    helloField.annotations.head.annotationType() should be(classOf[JsonProperty])
    helloField.annotations.exists(_.annotationType() == classOf[TestInjectableValue]) should be(
      true)
    helloField.annotations.last
      .asInstanceOf[TestInjectableValue].value() should be("accept") // from Bar
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 3") {
    val fields = deserializerFor(classOf[TestTraitImpl]).fields

    /*
      in trait:
      ---------
      @JsonProperty("oldness")
      def age: Int
      @NotEmpty
      def name: String

      case class constructor:
      -----------------------
      @JsonProperty("ageness") age: Int, // should override inherited annotation from trait
      @TestInjectableValue name: String, // should have two annotations, one from trait and one here
      @TestInjectableValue dateTime: LocalDate,
      @JsonProperty foo: String, // empty JsonProperty should default to field name
      @JsonDeserialize(contentAs = classOf[BigDecimal], using = classOf[BigDecimalDeserializer])
      double: BigDecimal,
      @JsonIgnore ignoreMe: String
     */
    fields.length should equal(6)

    val fieldMap: Map[String, CaseClassField] =
      fields.map(field => field.name -> field).toMap

    val ageField = fieldMap("ageness")
    ageField.annotations.length should equal(1)
    ageField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    ageField.annotations.head.asInstanceOf[JsonProperty].value() should equal("ageness")

    val nameField = fieldMap("name")
    nameField.annotations.length should equal(2)
    nameField.annotations.exists(_.annotationType() == classOf[NotEmpty]) should be(true)
    nameField.annotations.exists(_.annotationType() == classOf[TestInjectableValue]) should be(true)

    val dateTimeField = fieldMap("dateTime")
    dateTimeField.annotations.length should equal(1)
    dateTimeField.annotations.exists(_.annotationType() == classOf[TestInjectableValue]) should be(
      true)

    val fooField = fieldMap("foo")
    fooField.annotations.length should equal(1)
    fooField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    fooField.annotations.head.asInstanceOf[JsonProperty].value() should equal("")

    val doubleField = fieldMap("double")
    doubleField.annotations.length should equal(1)
    doubleField.annotations.exists(_.annotationType() == classOf[JsonDeserialize]) should be(true)
    doubleField.annotations.head.asInstanceOf[JsonDeserialize].contentAs() should be(
      classOf[BigDecimal])
    doubleField.annotations.head.asInstanceOf[JsonDeserialize].using() should be(
      classOf[BigDecimalDeserializer])

    val ignoreMeField = fieldMap("ignoreMe")
    ignoreMeField.annotations.length should equal(1)
    ignoreMeField.annotations.exists(_.annotationType() == classOf[JsonIgnore]) should be(true)
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 4") {
    val fields: Seq[CaseClassField] = deserializerFor(classOf[FooBaz]).fields

    fields.length should equal(1)
    val helloField: CaseClassField = fields.head
    helloField.annotations.length should equal(2)
    helloField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    helloField.annotations.head
      .asInstanceOf[JsonProperty].value() should equal("goodbyeWorld") // from Baz

    helloField.annotations.exists(_.annotationType() == classOf[TestInjectableValue]) should be(
      true)
    helloField.annotations.last
      .asInstanceOf[TestInjectableValue].value() should be("accept") // from Bar
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 5") {
    val fields = deserializerFor(classOf[FooBarBaz]).fields

    fields.length should equal(1)
    val helloField: CaseClassField = fields.head
    helloField.annotations.length should equal(2)
    helloField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    helloField.annotations.head
      .asInstanceOf[JsonProperty].value() should equal("goodbye") // from BarBaz

    helloField.annotations.exists(_.annotationType() == classOf[TestInjectableValue]) should be(
      true)
    helloField.annotations.last
      .asInstanceOf[TestInjectableValue].value() should be("accept") // from Bar
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 6") {
    val fields = deserializerFor(classOf[File]).fields

    fields.length should equal(1)
    val uriField: CaseClassField = fields.head
    uriField.annotations.length should equal(1)
    uriField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    uriField.annotations.head.asInstanceOf[JsonProperty].value() should equal("file")
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 7") {
    val fields = deserializerFor(classOf[Folder]).fields

    fields.length should equal(1)
    val uriField: CaseClassField = fields.head
    uriField.annotations.length should equal(1)
    uriField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    uriField.annotations.head.asInstanceOf[JsonProperty].value() should equal("folder")
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 8") {
    val fields = deserializerFor(classOf[LoadableFile]).fields

    fields.length should equal(1)
    val uriField: CaseClassField = fields.head
    uriField.annotations.length should equal(1)
    uriField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    uriField.annotations.head.asInstanceOf[JsonProperty].value() should equal("file")
  }

  test("CaseClassField.createFields sees inherited JsonProperty annotation 9") {
    val fields = deserializerFor(classOf[LoadableFolder]).fields

    fields.length should equal(1)
    val uriField: CaseClassField = fields.head
    uriField.annotations.length should equal(1)
    uriField.annotations.exists(_.annotationType() == classOf[JsonProperty]) should be(true)
    uriField.annotations.head.asInstanceOf[JsonProperty].value() should equal("folder")
  }

  test("Seq[Long]") {
    val fields = deserializerFor(classOf[CaseClassWithArrayLong]).fields

    fields.length should equal(1)
    val arrayField: CaseClassField = fields.head
    arrayField.javaType.getTypeName should be(
      "[array type, component type: [simple type, class long]]")
  }

  private[this] def deserializerFor(clazz: Class[_]): CaseClassDeserializer = {
    val javaType: JavaType = mapper.constructType(clazz)
    new CaseClassDeserializer(
      javaType = javaType,
      mapper.getDeserializationConfig,
      mapper.getDeserializationConfig.introspect(javaType),
      Some(ScalaValidator())
    )
  }
}
