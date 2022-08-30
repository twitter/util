package com.twitter.util.jackson

import com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.ValidationError
import com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException
import java.util.UUID
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ValidationScalaObjectMapperTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with ScalaObjectMapperFunctions {

  /* Class under test */
  protected val mapper: ScalaObjectMapper = ScalaObjectMapper()

  override def beforeAll(): Unit = {
    super.beforeAll()
    mapper.registerModule(ScalaObjectMapperTest.MixInAnnotationsModule)
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
}
