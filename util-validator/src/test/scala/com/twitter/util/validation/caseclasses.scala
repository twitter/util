package com.twitter.util.validation

import com.fasterxml.jackson.annotation.JsonCreator
import com.twitter.util.Try
import com.twitter.util.logging.Logging
import com.twitter.util.validation.constraints._
import com.twitter.util.validation.engine.MethodValidationResult
import jakarta.validation.{Payload, Valid, ValidationException}
import jakarta.validation.constraints._
import java.time.LocalDate
import scala.annotation.meta.field
import scala.beans.BeanProperty

object OuterObject {

  case class NestedCaseClass(id: Long)

  object InnerObject {

    case class SuperNestedCaseClass(@Min(5) id: Long)
  }
}

object caseclasses {

  object Path {
    val FieldSeparator = "."

    /** An empty path (no names) */
    val Empty: Path = Path(Seq.empty[String])

    /** Creates a new path from the given name */
    def apply(name: String): Path = new Path(Seq(name))
  }

  /**
   * Represents the navigation path from an object to another in an object graph.
   */
  case class Path(names: Seq[String])

  case class Page[T](
    data: List[T],
    @Min(1) pageSize: Int,
    next: Option[Long],
    previous: Option[Long])

  case class UnicodeNameCaseClass(@Max(3) `winning-id`: Int, @NotEmpty name: String)

  class EvaluateConstraints

  trait NotACaseClassTrait {
    @UUID def uuid: String
  }

  class NotACaseClass(
    @NotNull manufacturer: String,
    @NotNull @Size(min = 2, max = 14) licensePlate: String,
    @Min(2) seatCount: Int,
    uuid: String)

  final case class WithFinalValField(
    @UUID uuid: String,
    @NotEmpty first: Option[String],
    last: Option[String],
    private val permissions: Option[Set[Int]])

  case class WithBogusCascadedField(
    @NotNull @Size(min = 2, max = 14) licensePlate: String,
    @Valid ints: Seq[Int])

  case class WithMetaFieldAnnotation(@(NotEmpty @field) id: String)

  // @NotEmpty is copied to generated field, but @Size is not. We should find both.
  case class WithPartialMetaFieldAnnotation(@(NotEmpty @field) @Size(min = 2, max = 10) id: String)

  case class PathNotEmpty(@NotEmpty path: Path, @NotEmpty id: String)

  case class Customer(@NotEmpty first: String, last: String)

  case class AlwaysFails(@InvalidConstraint name: String)

  case class AlwaysFailsMethodValidation(@NotEmpty id: String) {
    @MethodValidation(fields = Array("id"))
    def checkId: MethodValidationResult = {
      throw new ValidationException("oh noes!")
    }
  }

  // throws a ConstraintDefinitionException as `@ethodValidation` must not define args
  case class WithIncorrectlyDefinedMethodValidation(@NotEmpty id: String) {
    @MethodValidation(fields = Array("id"))
    def checkId(a: String): MethodValidationResult = {
      MethodValidationResult.validIfTrue(a == id, "id not equal")
    }
  }

  // throws a ConstraintDefinitionException as `@ethodValidation` must return a MethodValidationResult
  case class AnotherIncorrectlyDefinedMethodValidation(@NotEmpty id: String) {
    @MethodValidation(fields = Array("id"))
    def checkId: Boolean = id.nonEmpty
  }

  case class SmallCar(
    @NotNull manufacturer: String,
    @NotNull @Size(min = 2, max = 14) licensePlate: String,
    @Min(2) seatCount: Int)

  trait RentalStationMixin {
    @Min(10) def name: String
  }

  // doesn't define any field
  trait RandoMixin

  case class RentalStation(@NotNull name: String) {
    def rentCar(
      @NotNull customer: Customer,
      @NotNull @Future start: LocalDate,
      @Min(1) duration: Int
    ): Unit = ???

    def updateCustomerRecords(
      @NotEmpty @Size(min = 1) @Valid customers: Seq[Customer]
    ): Unit = ???

    @NotEmpty
    @Size(min = 1)
    def getCustomers: Seq[Customer] = ???

    def listCars(@NotEmpty `airport-code`: String): Seq[String] = ???

    @ConsistentDateParameters
    def reserve(
      start: LocalDate,
      end: LocalDate
    ): Boolean = ???
  }

  trait OtherCheck
  trait PersonCheck

  case class MinIntExample(@Min(1) numberValue: Int)

  // CountryCode
  case class CountryCodeExample(@CountryCode countryCode: String)
  case class CountryCodeOptionExample(@CountryCode countryCode: Option[String])
  case class CountryCodeSeqExample(@CountryCode countryCode: Seq[String])
  case class CountryCodeOptionSeqExample(@CountryCode countryCode: Option[Seq[String]])
  case class CountryCodeArrayExample(@CountryCode countryCode: Array[String])
  case class CountryCodeOptionArrayExample(@CountryCode countryCode: Option[Array[String]])
  case class CountryCodeInvalidTypeExample(@CountryCode countryCode: Long)
  case class CountryCodeOptionInvalidTypeExample(@CountryCode countryCode: Option[Long])
  // OneOf
  case class OneOfExample(@OneOf(value = Array("a", "B", "c")) enumValue: String)
  case class OneOfOptionExample(@OneOf(value = Array("a", "B", "c")) enumValue: Option[String])
  case class OneOfSeqExample(@OneOf(Array("a", "B", "c")) enumValue: Seq[String])
  case class OneOfOptionSeqExample(@OneOf(Array("a", "B", "c")) enumValue: Option[Seq[String]])
  case class OneOfInvalidTypeExample(@OneOf(Array("a", "B", "c")) enumValue: Long)
  case class OneOfOptionInvalidTypeExample(@OneOf(Array("a", "B", "c")) enumValue: Option[Long])
  // UUID
  case class UUIDExample(@UUID uuid: String)
  case class UUIDOptionExample(@UUID uuid: Option[String])

  case class NestedOptionExample(
    @NotEmpty id: String,
    name: String,
    @Valid user: Option[User])

  // multiple annotations
  case class User(
    @NotEmpty id: String,
    name: String,
    @OneOf(Array("F", "M", "Other")) gender: String) {
    @MethodValidation(fields = Array("name"))
    def nameCheck: MethodValidationResult = {
      MethodValidationResult.validIfTrue(name.length > 0, "cannot be empty")
    }
  }
  // nested NotEmpty on a Scala collection of a case class type
  case class Users(@NotEmpty @Valid users: Seq[User])

  // Other fields not in executable
  case class Person(@NotEmpty id: String, @NotEmpty name: String, @Valid address: Address) {
    val company: String = "Twitter"
    val city: String = "San Francisco"
    val state: String = "California"

    @MethodValidation(fields = Array("address"))
    def validateAddress: MethodValidationResult =
      MethodValidationResult.validIfTrue(address.state.nonEmpty, "state must be specified")
  }

  case class WithPersonCheck(
    @NotEmpty(groups = Array(classOf[PersonCheck])) id: String,
    @NotEmpty name: String)

  case class NoConstructorParams() {
    @BeanProperty @NotEmpty var id: String = ""
  }

  case class AnnotatedInternalFields(
    @NotEmpty id: String,
    @Size(min = 1, max = 100) bigString: String) {
    @NotEmpty val company: String = "" // this will be validated
  }

  case class AnnotatedBeanProperties(@Min(1) numbers: Int) {
    @BeanProperty @NotEmpty var field1: String = "default"
  }

  case class Address(
    line1: String,
    line2: Option[String] = None,
    city: String,
    @StateConstraint(payload = Array(classOf[StateConstraintPayload])) state: String,
    zipcode: String,
    additionalPostalCode: Option[String] = None)

  // Customer defined validations
  case class StateValidationExample(@StateConstraint state: String)
  // Integration test
  case class ValidateUserRequest(
    @NotEmpty @Pattern(regexp = "[a-z]+") userName: String,
    @Max(value = 9999) id: Long,
    title: String)

  case class NestedUserPayload(id: String, job: String) extends Payload
  case class NestedUser(
    @NotEmpty id: String,
    @Valid person: Person,
    @OneOf(Array("F", "M", "Other")) gender: String,
    job: String) {
    @MethodValidation(fields = Array("job"), payload = Array(classOf[NestedUserPayload]))
    def jobCheck: MethodValidationResult = {
      MethodValidationResult.validIfTrue(
        job.length > 0,
        "cannot be empty",
        payload = Some(NestedUserPayload(id, job))
      )
    }
  }

  case class Division(
    name: String,
    @Valid team: Seq[Group])

  case class Persons(
    @NotEmpty id: String,
    @Min(1) @Valid people: Seq[Person])

  case class Group(
    @NotEmpty id: String,
    @Min(1) @Valid people: Seq[Person])

  case class CustomerAccount(
    @NotEmpty accountName: String,
    @Valid customer: Option[User])

  case class CaseClassWithBoxedPrimitives(events: Integer, errors: Integer)

  case class CollectionOfCollection(
    @NotEmpty id: String,
    @Valid @Min(1) people: Seq[Persons])

  case class CollectionWithArray(
    @NotEmpty @Size(min = 1, max = 30) names: Array[String],
    @NotEmpty @Max(2) users: Array[User])

  // cycle -- impossible to instantiate without using null to terminate somewhere
  case class A(@NotEmpty id: String, @Valid b: B)
  case class B(@NotEmpty id: String, @Valid c: C)
  case class C(@NotEmpty id: String, @Valid a: A)
  // another cycle -- fails as we detect the D type in F
  case class D(@NotEmpty id: String, @Valid e: E)
  case class E(@NotEmpty id: String, @Valid f: F)
  case class F(@NotEmpty id: String, @Valid d: Option[D])
  // last one -- fails as we detect the G type in I
  case class G(@NotEmpty id: String, @Valid h: H)
  case class H(@NotEmpty id: String, @Valid i: I)
  case class I(@NotEmpty id: String, @Valid g: Seq[G])

  // no validation -- no annotations
  object TestJsonCreator {
    @JsonCreator
    def apply(s: String): TestJsonCreator = TestJsonCreator(s.toInt)
  }
  case class TestJsonCreator(int: Int)

  // no validation -- no annotations
  object TestJsonCreator2 {
    @JsonCreator
    def apply(strings: Seq[String]): TestJsonCreator2 = TestJsonCreator2(strings.map(_.toInt))
  }
  case class TestJsonCreator2(ints: Seq[Int], default: String = "Hello, World")

  // no validation -- annotation is on a factory method "executable"
  object TestJsonCreatorWithValidation {
    @JsonCreator
    def apply(@NotEmpty s: String): TestJsonCreatorWithValidation =
      TestJsonCreatorWithValidation(s.toInt)
  }
  case class TestJsonCreatorWithValidation(int: Int)

  // this should validate per the primary executable annotation
  object TestJsonCreatorWithValidations {
    @JsonCreator
    def apply(@NotEmpty s: String): TestJsonCreatorWithValidations =
      TestJsonCreatorWithValidations(s.toInt)
  }
  case class TestJsonCreatorWithValidations(@OneOf(Array("42", "137")) int: Int)

  // no validation -- no annotations
  case class CaseClassWithMultipleConstructors(number1: Long, number2: Long, number3: Long) {
    def this(numberAsString1: String, numberAsString2: String, numberAsString3: String) {
      this(numberAsString1.toLong, numberAsString2.toLong, numberAsString3.toLong)
    }
  }

  // no validation -- no annotations
  case class CaseClassWithMultipleConstructorsAnnotated(
    number1: Long,
    number2: Long,
    number3: Long) {
    @JsonCreator
    def this(numberAsString1: String, numberAsString2: String, numberAsString3: String) {
      this(numberAsString1.toLong, numberAsString2.toLong, numberAsString3.toLong)
    }
  }

  // no validation -- annotations are on secondary executable
  case class CaseClassWithMultipleConstructorsAnnotatedAndValidations(
    number1: Long,
    number2: Long,
    uuid: String) {
    @JsonCreator
    def this(
      @NotEmpty numberAsString1: String,
      @OneOf(Array("10001", "20002", "30003")) numberAsString2: String,
      @UUID thirdArgument: String
    ) {
      this(numberAsString1.toLong, numberAsString2.toLong, thirdArgument)
    }
  }

  // this should validate -- annotations are on the primary executable
  case class CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations(
    @Min(10000) number1: Long,
    @OneOf(Array("10001", "20002", "30003")) number2: Long,
    @UUID uuid: String) {
    def this(
      numberAsString1: String,
      numberAsString2: String,
      thirdArgument: String
    ) {
      this(numberAsString1.toLong, numberAsString2.toLong, thirdArgument)
    }
  }

  trait AncestorWithValidation {
    @NotEmpty def field1: String

    @MethodValidation(fields = Array("field1"))
    def validateField1: MethodValidationResult =
      MethodValidationResult.validIfTrue(Try(field1.toDouble).isReturn, "not a double value")
  }

  case class ImplementsAncestor(override val field1: String) extends AncestorWithValidation

  case class InvalidDoublePerson(@NotEmpty name: String)(@NotEmpty otherName: String)
  case class DoublePerson(@NotEmpty name: String)(@NotEmpty val otherName: String)
  case class ValidDoublePerson(@NotEmpty name: String)(@NotEmpty otherName: String) {
    @MethodValidation
    def checkOtherName: MethodValidationResult =
      MethodValidationResult.validIfTrue(
        otherName.length >= 3,
        "otherName must be longer than 3 chars")
  }
  case class PossiblyValidDoublePerson(@NotEmpty name: String)(@NotEmpty otherName: String) {
    def referenceSecondArgInMethod: String =
      s"The otherName value is $otherName"
  }

  case class CaseClassWithIntAndLocalDate(
    @NotEmpty name: String,
    age: Int,
    age2: Int,
    age3: Int,
    dateTime: LocalDate,
    dateTime2: LocalDate,
    dateTime3: LocalDate,
    dateTime4: LocalDate,
    @NotEmpty dateTime5: Option[LocalDate])

  case class SimpleCar(
    id: Long,
    make: CarMake,
    model: String,
    @Min(2000) year: Int,
    @NotEmpty @Size(min = 2, max = 14) licensePlate: String, //multiple annotations
    @Min(0) numDoors: Int = 4,
    manual: Boolean = false)

  // executable annotation -- note the validator must specify a `@SupportedValidationTarget` of annotated element
  case class CarWithPassengerCount @ValidPassengerCountReturnValue(max = 1) (
    passengers: Seq[Person])

  // class-level annotation
  @ValidPassengerCount(max = 4)
  case class Car(
    id: Long,
    make: CarMake,
    model: String,
    @Min(2000) year: Int,
    @Valid owners: Seq[Person],
    @NotEmpty @Size(min = 2, max = 14) licensePlate: String, //multiple annotations
    @Min(0) numDoors: Int = 4,
    manual: Boolean = false,
    ownershipStart: LocalDate = LocalDate.now,
    ownershipEnd: LocalDate = LocalDate.now.plusYears(1),
    warrantyStart: Option[LocalDate] = None,
    warrantyEnd: Option[LocalDate] = None,
    @Valid passengers: Seq[Person] = Seq.empty[Person]) {

    @MethodValidation
    def validateId: MethodValidationResult = {
      MethodValidationResult.validIfTrue(id % 2 == 1, "id may not be even")
    }

    @MethodValidation
    def validateYearBeforeNow: MethodValidationResult = {
      val thisYear = LocalDate.now().getYear
      val yearMoreThanOneYearInFuture: Boolean =
        if (year > thisYear) { (year - thisYear) > 1 }
        else false
      MethodValidationResult.validIfFalse(
        yearMoreThanOneYearInFuture,
        "Model year can be at most one year newer."
      )
    }

    @MethodValidation(fields = Array("ownershipEnd"))
    def ownershipTimesValid: MethodValidationResult = {
      validateTimeRange(
        ownershipStart,
        ownershipEnd,
        "ownershipStart",
        "ownershipEnd"
      )
    }

    @MethodValidation(fields = Array("warrantyStart", "warrantyEnd"))
    def warrantyTimeValid: MethodValidationResult = {
      validateTimeRange(
        warrantyStart,
        warrantyEnd,
        "warrantyStart",
        "warrantyEnd"
      )
    }
  }

  case class Driver(
    @Valid person: Person,
    @Valid car: Car)

  case class MultipleConstraints(
    @NotEmpty @Max(100) ints: Seq[Int],
    @NotEmpty @Pattern(regexp = "\\d") digits: String,
    @NotEmpty @Size(min = 3, max = 3) @OneOf(Array("how", "now", "brown",
        "cow")) strings: Seq[String])

  // maps not supported
  case class MapsAndMaps(
    @NotEmpty id: String,
    @AssertTrue bool: Boolean,
    @Valid carAndDriver: Map[Person, SimpleCar])

  // @Valid only visible on field not on type arg
  case class TestAnnotations(
    @NotEmpty cars: Seq[SimpleCar @Valid])

  case class GenericTestCaseClass[T](@NotEmpty @Valid data: T)
  case class GenericMinTestCaseClass[T](@Min(4) data: T)
  case class GenericTestCaseClassMultipleTypes[T, U, V](
    @NotEmpty @Valid data: T,
    @Size(max = 100) things: Seq[U],
    @Size(min = 3, max = 3) @Valid otherThings: Seq[V])
  case class GenericTestCaseClassWithMultipleArgs[T](
    @NotEmpty data: T,
    @Min(5) number: Int)

  def validateTimeRange(
    startTime: Option[LocalDate],
    endTime: Option[LocalDate],
    startTimeProperty: String,
    endTimeProperty: String
  ): MethodValidationResult = {

    val rangeDefined = startTime.isDefined && endTime.isDefined
    val partialRange = !rangeDefined && (startTime.isDefined || endTime.isDefined)

    if (rangeDefined)
      validateTimeRange(startTime.get, endTime.get, startTimeProperty, endTimeProperty)
    else if (partialRange)
      MethodValidationResult.Invalid(
        "both %s and %s are required for a valid range".format(startTimeProperty, endTimeProperty)
      )
    else MethodValidationResult.Valid
  }

  def validateTimeRange(
    startTime: LocalDate,
    endTime: LocalDate,
    startTimeProperty: String,
    endTimeProperty: String
  ): MethodValidationResult = {

    MethodValidationResult.validIfTrue(
      startTime.isBefore(endTime),
      "%s [%s] must be after %s [%s]"
        .format(endTimeProperty, endTime.toEpochDay, startTimeProperty, startTime.toEpochDay)
    )
  }

  case class PersonWithLogging(
    id: Int,
    name: String,
    age: Option[Int],
    age_with_default: Option[Int] = None,
    nickname: String = "unknown")
      extends Logging

  case class UsersRequest(
    @Max(100) @DoesNothing max: Int,
    @Past @DoesNothing startDate: Option[LocalDate],
    @DoesNothing verbose: Boolean = false)
}
