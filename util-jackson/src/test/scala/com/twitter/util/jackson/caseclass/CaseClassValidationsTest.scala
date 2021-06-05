package com.twitter.util.jackson.caseclass

import com.twitter.util.jackson._
import com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException.ValidationError
import com.twitter.util.jackson.caseclass.exceptions.{
  CaseClassFieldMappingException,
  CaseClassMappingException
}
import com.twitter.util.validation.conversions.PathOps._
import jakarta.validation.Path.ParameterNode
import jakarta.validation.{ElementKind, UnexpectedTypeException}
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}
import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

private object CaseClassValidationsTest {
  val now: LocalDateTime =
    LocalDateTime.parse("2015-04-09T05:17:15Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  val BaseCar: Car = {
    Car(
      id = 1,
      make = CarMake.Ford,
      model = "Model-T",
      year = 2000,
      owners = Seq(),
      numDoors = 2,
      manual = true,
      ownershipStart = now,
      ownershipEnd = now.plusYears(15),
      warrantyStart = Some(now),
      warrantyEnd = Some(now.plusYears(5)),
      passengers = Seq.empty[Person]
    )
  }
}

@RunWith(classOf[JUnitRunner])
class CaseClassValidationsTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with ScalaObjectMapperFunctions {
  import CaseClassValidationsTest._

  /* Class under test */
  override protected val mapper: ScalaObjectMapper = ScalaObjectMapper()

  test("deserialization#generic types") {
    // passes validation
    parse[GenericTestCaseClassWithValidation[String]]("""{"data" : "widget"}""") should equal(
      GenericTestCaseClassWithValidation("widget"))

    val e1 = intercept[CaseClassMappingException] {
      parse[GenericTestCaseClassWithValidationAndMultipleArgs[String]](
        """{"data" : "", "number" : 3}""")
    }
    e1.errors.size should equal(2)
    val e2 = intercept[CaseClassMappingException] {
      parse[GenericTestCaseClassWithValidationAndMultipleArgs[String]](
        """{"data" : "widget", "number" : 3}""")
    }
    e2.errors.size should equal(1)
    // passes validation
    parse[GenericTestCaseClassWithValidationAndMultipleArgs[String]](
      """{"data" : "widget", "number" : 5}""")
  }

  test("enums#default validation") {
    val e = intercept[CaseClassMappingException] {
      parse[CaseClassWithNotEmptyValidation]("""{
        "name" : "",
        "make" : "foo"
       }""")
    }
    e.errors.map { _.getMessage } should equal(
      Seq(
        """make: 'foo' is not a valid CarMakeEnum with valid values: ford, vw""",
        "name: must not be empty")
    )
  }

  test("enums#invalid validation") {
    // the name field is annotated with @ThrowsRuntimeExceptionConstraint which is implemented
    // by the ThrowsRuntimeExceptionConstraintValidator...that simply throws a RuntimeException
    val e = intercept[RuntimeException] {
      parse[CaseClassWithInvalidValidation]("""{
        "name" : "Bob",
        "make" : "foo"
       }""")
    }

    e.getMessage should be("validator foo error")
  }

  test("enums#invalid validation 1") {
    // the name field is annotated with @NoValidatorImplConstraint which has no constraint validator
    // specified nor registered and thus errors when applied.
    val e = intercept[UnexpectedTypeException] {
      parse[CaseClassWithNoValidatorImplConstraint]("""{
        "name" : "Bob",
        "make" : "foo"
       }""")
    }

    e.getMessage.contains(
      "No validator could be found for constraint 'interface com.twitter.util.jackson.NoValidatorImplConstraint' validating type 'java.lang.String'. Check configuration for 'CaseClassWithNoValidatorImplConstraint.name'") should be(
      true)
  }

  test("method validation exceptions") {
    val e = intercept[RuntimeException] {
      parse[CaseClassWithMethodValidationException](
        """{
          | "name" : "foo barr",
          | "passphrase" : "the quick brown fox jumps over the lazy dog" 
          |}""".stripMargin)
    }
    e.getMessage.contains("oh noes")
  }

  /*
   Notes:

   ValidationError.Field types will not have a root bean instance and the violation property path
   will include the class name, e.g., for a violation on the `id` parameter of the `RentalStation` case
   class, the property path would be: `RentalStation.id`.

   ValidationError.Method types will have a root bean instance (since they require an instance in order for
   the method to be invoked) and the violation property path will not include the class name since this
   is understood to be known since the method validations typically have to be manually invoked, e.g.,
   for the method `validateState` that is annotated with field `state`, the property path of a violation would be:
   `validateState.state`.
   */

  test("class and field level validations#success") {
    parse[Car](BaseCar)
  }

  test("class and field level validations#top-level failed validations") {
    val value = BaseCar.copy(id = 2, year = 1910)
    val e = intercept[CaseClassMappingException] {
      parse[Car](value)
    }

    e.errors.size should equal(1)

    val error = e.errors.head
    error.path should equal(CaseClassFieldMappingException.PropertyPath.leaf("year"))
    error.reason.message should equal("must be greater than or equal to 2000")
    error.reason.detail match {
      case ValidationError(violation, ValidationError.Field, None) =>
        violation.getPropertyPath.toString should equal("Car.year")
        violation.getMessage should equal("must be greater than or equal to 2000")
        violation.getInvalidValue should equal(1910)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean == null should be(
          true
        ) // ValidationError.Field types won't have a root bean instance
      case _ => fail()
    }
  }

  test("method validation#top-level") {
    val address = Address(
      street = Some("123 Main St."),
      city = "Tampa",
      state = "FL" // invalid
    )

    val e = intercept[CaseClassMappingException] {
      parse[Address](address)
    }

    e.errors.size should equal(1)
    val errors = e.errors

    errors.head.path should equal(CaseClassFieldMappingException.PropertyPath.Empty)
    errors.head.reason.message should equal("state must be one of [CA, MD, WI]")
    errors.head.reason.detail.getClass should equal(classOf[ValidationError])
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal("state must be one of [CA, MD, WI]")
        violation.getInvalidValue should equal(address)
        violation.getRootBeanClass should equal(classOf[Address])
        violation.getRootBean should equal(
          address
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "validateState"
        ) // the @MethodValidation annotation does not specify a field.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PROPERTY)
        leafNode.getName should equal("validateState")
      case _ =>
        fail()
    }
  }

  test("class and field level validations#nested failed validations") {
    val invalidAddress =
      Address(
        street = Some(""), // invalid
        city = "", // invalid
        state = "FL" // invalid
      )

    val owners = Seq(
      Person(
        id = 1,
        name = "joe smith",
        dob = Some(LocalDate.now),
        age = None,
        address = Some(invalidAddress)
      )
    )
    val car = BaseCar.copy(owners = owners)

    val e = intercept[CaseClassMappingException] {
      parse[Car](car)
    }

    e.errors.size should equal(2)
    val errors = e.errors

    errors.head.path should equal(
      CaseClassFieldMappingException.PropertyPath
        .leaf("city").withParent("address").withParent("owners"))
    errors.head.reason.message should equal("must not be empty")
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Field, None) =>
        violation.getPropertyPath.toString should equal("Address.city")
        violation.getMessage should equal("must not be empty")
        violation.getInvalidValue should equal("")
        violation.getRootBeanClass should equal(classOf[Address])
        violation.getRootBean == null should be(
          true
        ) // ValidationError.Field types won't have a root bean instance
      case _ =>
        fail()
    }

    errors(1).path should equal(
      CaseClassFieldMappingException.PropertyPath
        .leaf("street").withParent("address").withParent("owners")
    )
    errors(1).reason.message should equal("must not be empty")
    errors(1).reason.detail match {
      case ValidationError(violation, ValidationError.Field, None) =>
        violation.getPropertyPath.toString should equal("Address.street")
        violation.getMessage should equal("must not be empty")
        violation.getInvalidValue should equal("")
        violation.getRootBeanClass should equal(classOf[Address])
        violation.getRootBean == null should be(
          true
        ) // ValidationError.Field types won't have a root bean instance
      case _ =>
        fail()
    }
  }

  test("class and field level validations#nested method validations") {
    val owners = Seq(
      Person(
        id = 2,
        name = "joe smith",
        dob = Some(LocalDate.now),
        age = None,
        address = Some(Address(city = "pyongyang", state = "KP" /* invalid */ ))
      )
    )
    val car: Car = BaseCar.copy(owners = owners)

    val e = intercept[CaseClassMappingException] {
      parse[Car](car)
    }

    e.errors.size should equal(1)
    val errors = e.errors

    errors.head.path should equal(
      CaseClassFieldMappingException.PropertyPath
        .leaf("address").withParent("owners")
    )
    errors.head.reason.message should equal("state must be one of [CA, MD, WI]")
    errors.head.reason.detail.getClass should equal(classOf[ValidationError])
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal("state must be one of [CA, MD, WI]")
        violation.getInvalidValue should equal(Address(city = "pyongyang", state = "KP"))
        violation.getRootBeanClass should equal(classOf[Address])
        violation.getRootBean should equal(
          Address(city = "pyongyang", state = "KP")
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "validateState"
        ) // the @MethodValidation annotation does not specify a field.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PROPERTY)
        leafNode.getName should equal("validateState")
      case _ =>
        fail()
    }

    errors.map(_.getMessage) should equal(Seq("owners.address: state must be one of [CA, MD, WI]"))
  }

  test("class and field level validations#end before start") {
    val value: Car =
      BaseCar.copy(ownershipStart = BaseCar.ownershipEnd, ownershipEnd = BaseCar.ownershipStart)
    val e = intercept[CaseClassMappingException] {
      parse[Car](value)
    }

    e.errors.size should equal(1)
    val errors = e.errors

    errors.head.path should equal(CaseClassFieldMappingException.PropertyPath.leaf("ownership_end"))
    errors.head.reason.message should equal(
      "ownershipEnd [2015-04-09T05:17:15] must be after ownershipStart [2030-04-09T05:17:15]")
    errors.head.reason.detail.getClass should equal(classOf[ValidationError])
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal(
          "ownershipEnd [2015-04-09T05:17:15] must be after ownershipStart [2030-04-09T05:17:15]")
        violation.getInvalidValue should equal(value)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean should equal(
          value
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "ownershipTimesValid.ownershipEnd"
        ) // the @MethodValidation annotation specifies a single field.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PARAMETER)
        leafNode.getName should equal("ownershipEnd")
        leafNode.as(classOf[ParameterNode]) // shouldn't blow up
      case _ =>
        fail()
    }
  }

  test("class and field level validations#optional end before start") {
    val value: Car =
      BaseCar.copy(warrantyStart = BaseCar.warrantyEnd, warrantyEnd = BaseCar.warrantyStart)
    val e = intercept[CaseClassMappingException] {
      parse[Car](value)
    }

    e.errors.size should equal(2)
    val errors = e.errors

    errors.head.path should equal(CaseClassFieldMappingException.PropertyPath.leaf("warranty_end"))
    errors.head.reason.message should equal(
      "warrantyEnd [2015-04-09T05:17:15] must be after warrantyStart [2020-04-09T05:17:15]"
    )
    errors.head.reason.detail.getClass should equal(classOf[ValidationError])
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal(
          "warrantyEnd [2015-04-09T05:17:15] must be after warrantyStart [2020-04-09T05:17:15]")
        violation.getInvalidValue should equal(value)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean should equal(
          value
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "warrantyTimeValid.warrantyEnd"
        ) // the @MethodValidation annotation specifies two fields.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PARAMETER)
        leafNode.getName should equal("warrantyEnd")
        leafNode.as(classOf[ParameterNode]) // shouldn't blow up
      case _ =>
        fail()
    }

    errors(1).path should equal(CaseClassFieldMappingException.PropertyPath.leaf("warranty_start"))
    errors(1).reason.message should equal(
      "warrantyEnd [2015-04-09T05:17:15] must be after warrantyStart [2020-04-09T05:17:15]"
    )
    errors(1).reason.detail.getClass should equal(classOf[ValidationError])
    errors(1).reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal(
          "warrantyEnd [2015-04-09T05:17:15] must be after warrantyStart [2020-04-09T05:17:15]")
        violation.getInvalidValue should equal(value)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean should equal(
          value
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "warrantyTimeValid.warrantyStart"
        ) // the @MethodValidation annotation specifies two fields.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PARAMETER)
        leafNode.getName should equal("warrantyStart")
        leafNode.as(classOf[ParameterNode]) // shouldn't blow up
      case _ =>
        fail()
    }
  }

  test("class and field level validations#no start with end") {
    val value: Car = BaseCar.copy(warrantyStart = None, warrantyEnd = BaseCar.warrantyEnd)
    val e = intercept[CaseClassMappingException] {
      parse[Car](value)
    }

    e.errors.size should equal(2)
    val errors = e.errors

    errors.head.path should equal(CaseClassFieldMappingException.PropertyPath.leaf("warranty_end"))
    errors.head.reason.message should equal(
      "both warrantyStart and warrantyEnd are required for a valid range"
    )
    errors.head.reason.detail.getClass should equal(classOf[ValidationError])
    errors.head.reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal(
          "both warrantyStart and warrantyEnd are required for a valid range")
        violation.getInvalidValue should equal(value)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean should equal(
          value
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "warrantyTimeValid.warrantyEnd"
        ) // the @MethodValidation annotation specifies two fields.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PARAMETER)
        leafNode.getName should equal("warrantyEnd")
        leafNode.as(classOf[ParameterNode]) // shouldn't blow up
      case _ =>
        fail()
    }

    errors(1).path should equal(CaseClassFieldMappingException.PropertyPath.leaf("warranty_start"))
    errors(1).reason.message should equal(
      "both warrantyStart and warrantyEnd are required for a valid range"
    )
    errors(1).reason.detail.getClass should equal(classOf[ValidationError])
    errors(1).reason.detail match {
      case ValidationError(violation, ValidationError.Method, None) =>
        violation.getMessage should equal(
          "both warrantyStart and warrantyEnd are required for a valid range")
        violation.getInvalidValue should equal(value)
        violation.getRootBeanClass should equal(classOf[Car])
        violation.getRootBean should equal(
          value
        ) // ValidationError.Method types have a root bean instance
        violation.getPropertyPath.toString should equal(
          "warrantyTimeValid.warrantyStart"
        ) // the @MethodValidation annotation specifies two fields.
        val leafNode = violation.getPropertyPath.getLeafNode
        leafNode.getKind should equal(ElementKind.PARAMETER)
        leafNode.getName should equal("warrantyStart")
        leafNode.as(classOf[ParameterNode]) // shouldn't blow up
      case _ =>
        fail()
    }
  }

  test("class and field level validations#errors sorted by message") {
    val first =
      CaseClassFieldMappingException(
        CaseClassFieldMappingException.PropertyPath.Empty,
        CaseClassFieldMappingException.Reason("123"))
    val second =
      CaseClassFieldMappingException(
        CaseClassFieldMappingException.PropertyPath.Empty,
        CaseClassFieldMappingException.Reason("aaa"))
    val third = CaseClassFieldMappingException(
      CaseClassFieldMappingException.PropertyPath.leaf("bla"),
      CaseClassFieldMappingException.Reason("zzz"))
    val fourth =
      CaseClassFieldMappingException(
        CaseClassFieldMappingException.PropertyPath.Empty,
        CaseClassFieldMappingException.Reason("xxx"))

    val unsorted = Set(third, second, fourth, first)
    val expectedSorted = Seq(first, second, third, fourth)

    CaseClassMappingException(unsorted).errors should equal((expectedSorted))
  }

  test("class and field level validations#option[string] validation") {
    val address = Address(
      street = Some(""), // invalid
      city = "New Orleans",
      state = "LA"
    )

    intercept[CaseClassMappingException] {
      mapper.parse[Address](mapper.writeValueAsBytes(address))
    }
  }
}
