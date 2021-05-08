package com.twitter.util.validation.executable

import com.twitter.util.validation.AssertViolationTest.WithViolation
import com.twitter.util.validation.ScalaValidatorTest.DefaultAddress
import com.twitter.util.validation.caseclasses.{
  Car,
  CarWithPassengerCount,
  Customer,
  NestedUser,
  Page,
  Path,
  PathNotEmpty,
  Person,
  RandoMixin,
  RentalStation,
  RentalStationMixin,
  UnicodeNameCaseClass,
  UsersRequest
}
import com.twitter.util.validation.cfg.ConstraintMapping
import com.twitter.util.validation.constraints.ValidPassengerCountReturnValue
import com.twitter.util.validation.engine.ConstraintViolationHelper
import com.twitter.util.validation.internal.validators.ValidPassengerCountReturnValueConstraintValidator
import com.twitter.util.validation.{AssertViolationTest, CarMake, ScalaValidator}
import jakarta.validation.{ConstraintViolation, UnexpectedTypeException}
import java.time.LocalDate

class ScalaExecutableValidatorTest extends AssertViolationTest {

  override protected val validator: ScalaValidator =
    ScalaValidator.builder
      .withConstraintMappings(
        Set(
          ConstraintMapping(
            classOf[ValidPassengerCountReturnValue],
            classOf[ValidPassengerCountReturnValueConstraintValidator]
          )
        )
      )
      .validator

  private[this] val executableValidator: ScalaExecutableValidator = validator.forExecutables

  test("ScalaExecutableValidator#validateMethods 1") {
    intercept[IllegalArgumentException] {
      executableValidator.validateMethods[Car](null)
    }
  }

  test("ScalaExecutableValidator#validateMethods 2") {
    val owner = Person(id = "", name = "A. Einstein", address = DefaultAddress)
    val car = Car(
      id = 1234,
      make = CarMake.Volkswagen,
      model = "Beetle",
      year = 1970,
      owners = Seq(owner),
      licensePlate = "CA123",
      numDoors = 2,
      manual = true,
      ownershipStart = LocalDate.now,
      ownershipEnd = LocalDate.now.plusYears(10),
      warrantyEnd = Some(LocalDate.now.plusYears(3)),
      passengers = Seq(
        Person(id = "1001", name = "R. Franklin", address = DefaultAddress)
      )
    )

    val violations = executableValidator.validateMethods(car)
    assertViolations(
      violations,
      Seq(
        WithViolation("validateId", "id may not be even", car, classOf[Car], car),
        WithViolation(
          "warrantyTimeValid.warrantyEnd",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Car],
          car),
        WithViolation(
          "warrantyTimeValid.warrantyStart",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Car],
          car)
      )
    )
  }

  test("ScalaExecutableValidator#validateMethods 3") {
    val owner = Person(id = "", name = "A. Einstein", address = DefaultAddress)
    val car = Car(
      id = 1234,
      make = CarMake.Volkswagen,
      model = "Beetle",
      year = 1970,
      owners = Seq(owner),
      licensePlate = "CA123",
      numDoors = 2,
      manual = true,
      ownershipStart = LocalDate.now,
      ownershipEnd = LocalDate.now.plusYears(10),
      warrantyEnd = Some(LocalDate.now.plusYears(3)),
      passengers = Seq(
        Person(id = "1001", name = "R. Franklin", address = DefaultAddress)
      )
    )

    val methods = validator.describeMethods(classOf[Car])
    val violations = executableValidator.validateMethods(methods, car)
    assertViolations(
      violations,
      Seq(
        WithViolation("validateId", "id may not be even", car, classOf[Car], car),
        WithViolation(
          "warrantyTimeValid.warrantyEnd",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Car],
          car),
        WithViolation(
          "warrantyTimeValid.warrantyStart",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Car],
          car)
      )
    )
  }

  test("ScalaExecutableValidator#validateMethods 4") {
    val nestedUser = NestedUser(
      "abcd1234",
      Person(id = "1234abcd", name = "A. Einstein", address = DefaultAddress),
      "Other",
      ""
    )
    val methods = validator.describeMethods(classOf[NestedUser])
    val violations = executableValidator.validateMethods(methods, nestedUser)
    assertViolations(
      violations,
      Seq(
        WithViolation(
          "jobCheck.job",
          "cannot be empty",
          nestedUser,
          classOf[NestedUser],
          nestedUser)
      )
    )
  }

  test("ScalaExecutableValidator#validateMethod") {
    val owner = Person(id = "", name = "A. Einstein", address = DefaultAddress)
    val car = Car(
      id = 1234,
      make = CarMake.Volkswagen,
      model = "Beetle",
      year = 1970,
      owners = Seq(owner),
      licensePlate = "CA123",
      numDoors = 2,
      manual = true,
      ownershipStart = LocalDate.now,
      ownershipEnd = LocalDate.now.plusYears(10),
      warrantyEnd = Some(LocalDate.now.plusYears(3)),
      passengers = Seq(
        Person(id = "1001", name = "R. Franklin", address = DefaultAddress)
      )
    )

    val method = classOf[Car].getDeclaredMethod("validateId")

    val violations = executableValidator.validateMethod(car, method)
    assertViolations(
      violations,
      Seq(
        WithViolation("validateId", "id may not be even", car, classOf[Car], car)
      )
    )
  }

  test("ScalaExecutableValidator#validateParameters 1") {
    val customer = Customer("Jane", "Smith")
    val rentalStation = RentalStation("Hertz")

    val method =
      classOf[RentalStation].getMethod(
        "rentCar",
        classOf[Customer],
        classOf[LocalDate],
        classOf[Int])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateParameters(
        rentalStation,
        method,
        Array(customer, LocalDate.now().minusDays(1), Integer.valueOf(5)))
    violations.size should equal(1)
    val invalidRentalStartDateViolation = violations.head
    invalidRentalStartDateViolation.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation.getRootBean should equal(rentalStation)
    invalidRentalStartDateViolation.getLeafBean should equal(rentalStation)

    val invalidRentalStartDateAndNullCustomerViolations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateParameters(
        rentalStation,
        method,
        Array(null, LocalDate.now(), Integer.valueOf(5)))
    invalidRentalStartDateAndNullCustomerViolations.size should equal(2)
    val sortedInvalidRentalStartDateAndNullCustomerViolationsViolations =
      ConstraintViolationHelper.sortViolations[RentalStation](
        invalidRentalStartDateAndNullCustomerViolations)
    val nullCustomerViolation = sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.head
    nullCustomerViolation.getMessage should equal("must not be null")
    nullCustomerViolation.getPropertyPath.toString should equal("rentCar.customer")
    nullCustomerViolation.getRootBeanClass should equal(classOf[RentalStation])
    nullCustomerViolation.getRootBean should equal(rentalStation)
    nullCustomerViolation.getLeafBean should equal(rentalStation)

    val invalidRentalStartDateViolation2 =
      sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.last
    invalidRentalStartDateViolation2.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation2.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation2.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation2.getRootBean should equal(rentalStation)
    invalidRentalStartDateViolation2.getLeafBean should equal(rentalStation)
  }

  test("ScalaExecutableValidator#validateParameters 2") {
    val rentalStation = RentalStation("Hertz")
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateParameters(
        rentalStation,
        method,
        Array(Seq.empty[Customer])
      )
    violations.size should equal(2)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should equal(rentalStation)
    violation1.getInvalidValue should equal(Seq.empty[Customer])

    val violation2 = sortedViolations.last
    violation2.getMessage should equal("size must be between 1 and 2147483647")
    violation2.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation2.getRootBeanClass should equal(classOf[RentalStation])
    violation2.getRootBean should equal(rentalStation)
    violation2.getInvalidValue should equal(Seq.empty[Customer])
  }

  test("ScalaExecutableValidator#validateParameters 3") {
    // validation should cascade to customer parameter which is missing first name
    val rentalStation = RentalStation("Hertz")
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateParameters(
        rentalStation,
        method,
        Array(
          Seq(
            Customer("", "Ride")
          ))
      )
    violations.size should equal(1)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers[0].first")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should equal(rentalStation)
    violation1.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateParameters 4") {
    // validation should handle encoded parameter name
    val rentalStation = RentalStation("Hertz")
    val method =
      classOf[RentalStation].getMethod("listCars", classOf[String])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateParameters(
        rentalStation,
        method,
        Array("")
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("listCars.airport-code")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should equal(rentalStation)
    violation.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateReturnValue") {
    val rentalStation = RentalStation("Hertz")

    val method =
      classOf[RentalStation].getMethod("getCustomers")
    val returnValue = Seq.empty[Customer]

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateReturnValue(
        rentalStation,
        method,
        returnValue
      )
    violations.size should equal(2)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)

    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("getCustomers.<return value>")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should equal(rentalStation)
    violation1.getInvalidValue should equal(Seq.empty[Customer])

    val violation2 = sortedViolations.last
    violation2.getMessage should equal("size must be between 1 and 2147483647")
    violation2.getPropertyPath.toString should equal("getCustomers.<return value>")
    violation2.getRootBeanClass should equal(classOf[RentalStation])
    violation2.getRootBean should equal(rentalStation)
    violation2.getInvalidValue should equal(Seq.empty[Customer])
  }

  test("ScalaExecutableValidator#validateConstructorParameters 1") {
    val constructor = classOf[RentalStation].getConstructor(classOf[String])

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateConstructorParameters(
        constructor,
        Array(null)
      )
    violations.size should equal(1)

    val violation = violations.head
    violation.getMessage should equal("must not be null")
    violation.getPropertyPath.toString should equal("RentalStation.name")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should be(null)
  }

  test("ScalaExecutableValidator#validateConstructorParameters 2") {
    val constructor = classOf[UnicodeNameCaseClass].getConstructor(classOf[Int], classOf[String])

    val violations: Set[ConstraintViolation[UnicodeNameCaseClass]] =
      executableValidator.validateConstructorParameters(
        constructor,
        Array(5, "")
      )
    violations.size should equal(2)

    val sortedViolations: Seq[ConstraintViolation[UnicodeNameCaseClass]] =
      ConstraintViolationHelper.sortViolations(violations)

    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("UnicodeNameCaseClass.name")
    violation1.getRootBeanClass should equal(classOf[UnicodeNameCaseClass])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal("")

    val violation2 = sortedViolations(1)
    violation2.getMessage should equal("must be less than or equal to 3")
    violation2.getPropertyPath.toString should equal("UnicodeNameCaseClass.winning-id")
    violation2.getRootBeanClass should equal(classOf[UnicodeNameCaseClass])
    violation2.getRootBean should be(null)
    violation2.getInvalidValue should equal(5)
  }

  test("ScalaExecutableValidator#validateConstructorParameters 3") {
    val constructor = classOf[Page[String]].getConstructor(
      classOf[List[String]],
      classOf[Int],
      classOf[Option[Long]],
      classOf[Option[Long]])

    val violations: Set[ConstraintViolation[Page[String]]] =
      executableValidator.validateConstructorParameters(
        constructor,
        Array(
          List("foo", "bar"),
          0.asInstanceOf[AnyRef],
          Some(1),
          Some(2)
        )
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getPropertyPath.toString should be("Page.pageSize")
    violation.getMessage should be("must be greater than or equal to 1")
    violation.getRootBeanClass should equal(classOf[Page[Person]])
    violation.getRootBean should be(null)
    violation.getInvalidValue should equal(0)
  }

  test("ScalaExecutableValidator#validateMethodParameters 1") {
    // validation should handle encoded parameter name
    val method =
      classOf[RentalStation].getMethod("listCars", classOf[String])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array("")
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("listCars.airport-code")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateMethodParameters 2") {
    // validation should cascade to customer parameter which is missing first name
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array(
          Seq(
            Customer("", "Ride")
          ))
      )
    violations.size should equal(1)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers[0].first")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateMethodParameters 3") {
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array(Seq.empty[Customer])
      )
    violations.size should equal(2)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal(Seq.empty[Customer])

    val violation2 = sortedViolations.last
    violation2.getMessage should equal("size must be between 1 and 2147483647")
    violation2.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation2.getRootBeanClass should equal(classOf[RentalStation])
    violation2.getRootBean should be(null)
    violation2.getInvalidValue should equal(Seq.empty[Customer])
  }

  test("ScalaExecutableValidator#validateMethodParameters 4") {
    val customer = Customer("Jane", "Smith")

    val method =
      classOf[RentalStation].getMethod(
        "rentCar",
        classOf[Customer],
        classOf[LocalDate],
        classOf[Int])
    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array(customer, LocalDate.now().minusDays(1), Integer.valueOf(5)))
    violations.size should equal(1)
    val invalidRentalStartDateViolation = violations.head
    invalidRentalStartDateViolation.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation.getRootBean should be(null)
    invalidRentalStartDateViolation.getLeafBean should be(null)

    val invalidRentalStartDateAndNullCustomerViolations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array(null, LocalDate.now(), Integer.valueOf(5)))
    invalidRentalStartDateAndNullCustomerViolations.size should equal(2)
    val sortedInvalidRentalStartDateAndNullCustomerViolationsViolations: Seq[
      ConstraintViolation[RentalStation]
    ] = ConstraintViolationHelper.sortViolations(invalidRentalStartDateAndNullCustomerViolations)

    val nullCustomerViolation = sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.head
    nullCustomerViolation.getMessage should equal("must not be null")
    nullCustomerViolation.getPropertyPath.toString should equal("rentCar.customer")
    nullCustomerViolation.getRootBeanClass should equal(classOf[RentalStation])
    nullCustomerViolation.getRootBean should be(null)
    nullCustomerViolation.getLeafBean should be(null)

    val invalidRentalStartDateViolation2 =
      sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.last
    invalidRentalStartDateViolation2.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation2.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation2.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation2.getRootBean should be(null)
    invalidRentalStartDateViolation2.getLeafBean should be(null)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 1") {
    val constructor = classOf[RentalStation].getConstructor(classOf[String])
    val descriptor = validator.describeExecutable(constructor, None)

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(null),
        constructor.getParameters.map(_.getName)
      )
    violations.size should equal(1)

    val violation = violations.head
    violation.getMessage should equal("must not be null")
    violation.getPropertyPath.toString should equal("RentalStation.name")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should be(null)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 2") {
    val constructor = classOf[UnicodeNameCaseClass].getConstructor(classOf[Int], classOf[String])
    val descriptor = validator.describeExecutable(constructor, None)

    val violations: Set[ConstraintViolation[UnicodeNameCaseClass]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(5, ""),
        constructor.getParameters.map(_.getName)
      )
    violations.size should equal(2)

    val sortedViolations: Seq[ConstraintViolation[UnicodeNameCaseClass]] =
      ConstraintViolationHelper.sortViolations(violations)

    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("UnicodeNameCaseClass.name")
    violation1.getRootBeanClass should equal(classOf[UnicodeNameCaseClass])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal("")

    val violation2 = sortedViolations(1)
    violation2.getMessage should equal("must be less than or equal to 3")
    violation2.getPropertyPath.toString should equal("UnicodeNameCaseClass.winning-id")
    violation2.getRootBeanClass should equal(classOf[UnicodeNameCaseClass])
    violation2.getRootBean should be(null)
    violation2.getInvalidValue should equal(5)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 3") {
    val constructor = classOf[Page[String]].getConstructor(
      classOf[List[String]],
      classOf[Int],
      classOf[Option[Long]],
      classOf[Option[Long]])
    val descriptor = validator.describeExecutable(constructor, None)

    val violations: Set[ConstraintViolation[Page[String]]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(
          List("foo", "bar"),
          0.asInstanceOf[AnyRef],
          Some(1),
          Some(2)
        ),
        constructor.getParameters.map(_.getName)
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getPropertyPath.toString should be("Page.pageSize")
    violation.getMessage should be("must be greater than or equal to 1")
    violation.getRootBeanClass should equal(classOf[Page[Person]])
    violation.getRootBean should be(null)
    violation.getInvalidValue should equal(0)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 4") {
    // validation should handle encoded parameter name
    val method =
      classOf[RentalStation].getMethod("listCars", classOf[String])
    val descriptor = validator.describeExecutable(method, None)

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(""),
        method.getParameters.map(_.getName)
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("listCars.airport-code")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateExecutableParameters 5") {
    // validation should cascade to customer parameter which is missing first name
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val descriptor = validator.describeExecutable(method, None)

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(
          Seq(
            Customer("", "Ride")
          )
        ),
        method.getParameters.map(_.getName)
      )
    violations.size should equal(1)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers[0].first")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal("")
  }

  test("ScalaExecutableValidator#validateExecutableParameters 6") {
    val method =
      classOf[RentalStation].getMethod("updateCustomerRecords", classOf[Seq[Customer]])
    val descriptor = validator.describeExecutable(method, None)

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(Seq.empty[Customer]),
        method.getParameters.map(_.getName)
      )
    violations.size should equal(2)
    val sortedViolations: Seq[ConstraintViolation[RentalStation]] =
      ConstraintViolationHelper.sortViolations(violations)
    val violation1 = sortedViolations.head
    violation1.getMessage should equal("must not be empty")
    violation1.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation1.getRootBeanClass should equal(classOf[RentalStation])
    violation1.getRootBean should be(null)
    violation1.getInvalidValue should equal(Seq.empty[Customer])

    val violation2 = sortedViolations.last
    violation2.getMessage should equal("size must be between 1 and 2147483647")
    violation2.getPropertyPath.toString should equal("updateCustomerRecords.customers")
    violation2.getRootBeanClass should equal(classOf[RentalStation])
    violation2.getRootBean should be(null)
    violation2.getInvalidValue should equal(Seq.empty[Customer])
  }

  test("ScalaExecutableValidator#validateExecutableParameters 7") {
    val customer = Customer("Jane", "Smith")

    val method =
      classOf[RentalStation].getMethod(
        "rentCar",
        classOf[Customer],
        classOf[LocalDate],
        classOf[Int])
    val descriptor = validator.describeExecutable(method, None)

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(customer, LocalDate.now().minusDays(1), Integer.valueOf(5)),
        method.getParameters.map(_.getName)
      )
    violations.size should equal(1)
    val invalidRentalStartDateViolation = violations.head
    invalidRentalStartDateViolation.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation.getRootBean should be(null)
    invalidRentalStartDateViolation.getLeafBean should be(null)

    val invalidRentalStartDateAndNullCustomerViolations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateMethodParameters(
        method,
        Array(null, LocalDate.now(), Integer.valueOf(5)))
    invalidRentalStartDateAndNullCustomerViolations.size should equal(2)
    val sortedInvalidRentalStartDateAndNullCustomerViolationsViolations: Seq[
      ConstraintViolation[RentalStation]
    ] = ConstraintViolationHelper.sortViolations(invalidRentalStartDateAndNullCustomerViolations)

    val nullCustomerViolation = sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.head
    nullCustomerViolation.getMessage should equal("must not be null")
    nullCustomerViolation.getPropertyPath.toString should equal("rentCar.customer")
    nullCustomerViolation.getRootBeanClass should equal(classOf[RentalStation])
    nullCustomerViolation.getRootBean should be(null)
    nullCustomerViolation.getLeafBean should be(null)

    val invalidRentalStartDateViolation2 =
      sortedInvalidRentalStartDateAndNullCustomerViolationsViolations.last
    invalidRentalStartDateViolation2.getMessage should equal("must be a future date")
    invalidRentalStartDateViolation2.getPropertyPath.toString should equal("rentCar.start")
    invalidRentalStartDateViolation2.getRootBeanClass should equal(classOf[RentalStation])
    invalidRentalStartDateViolation2.getRootBean should be(null)
    invalidRentalStartDateViolation2.getLeafBean should be(null)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 8") {
    // test mixin class support
    val constructor = classOf[RentalStation].getConstructor(classOf[String])
    val descriptor = validator.describeExecutable(constructor, Some(classOf[RentalStationMixin]))

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array("Hertz"),
        constructor.getParameters.map(_.getName)
      )
    violations.size should equal(1)

    // this violates the @Min(10) constraint from RentalStationMixin
    val violation = violations.head
    violation.getMessage should equal("must be greater than or equal to 10")
    violation.getPropertyPath.toString should equal("RentalStation.name")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should be("Hertz")
  }

  test("ScalaExecutableValidator#validateExecutableParameters 9") {
    // test mixin class support and replacement of parameter names
    val constructor = classOf[RentalStation].getConstructor(classOf[String])
    val descriptor = validator.describeExecutable(constructor, Some(classOf[RentalStationMixin]))

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array("Hertz"),
        Array("id")
      )
    violations.size should equal(1)

    // this violates the @Min(10) constraint from RentalStationMixin
    val violation = violations.head
    violation.getMessage should equal("must be greater than or equal to 10")
    violation.getPropertyPath.toString should equal("RentalStation.id")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should be(null)
    violation.getInvalidValue should be("Hertz")
  }

  test("ScalaExecutableValidator#validateExecutableParameters 10") {
    // test mixin class support with mixin that doesn't define the field
    val constructor = classOf[RentalStation].getConstructor(classOf[String])
    val descriptor =
      validator.describeExecutable(
        constructor,
        Some(classOf[RandoMixin])
      ) // doesn't define any fields so no additional constraints

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateExecutableParameters(
        descriptor,
        Array("Hertz"),
        Array("id")
      )
    violations.isEmpty should be(true)
  }

  test("ScalaExecutableValidator#validateExecutableParameters 11") {
    val pathValue = Path.apply("foo")
    val constructor = classOf[PathNotEmpty].getConstructor(classOf[Path], classOf[String])
    val descriptor = validator.describeExecutable(constructor, None)

    // no validator registered `@NotEmpty` for Path type, should be returned as a violation not an exception
    val e = intercept[UnexpectedTypeException] {
      executableValidator.validateExecutableParameters(
        descriptor,
        Array(pathValue, "12345"),
        constructor.getParameters.map(_.getName)
      )
    }
    e.getMessage should equal(
      s"No validator could be found for constraint 'interface jakarta.validation.constraints.NotEmpty' validating type 'com.twitter.util.validation.caseclasses$$Path'. Check configuration for 'PathNotEmpty.path'")
  }

  test("ScalaExecutableValidator#validateExecutableParameters 12") {
    val constructor = classOf[UsersRequest].getConstructor(
      classOf[Int],
      classOf[Option[LocalDate]],
      classOf[Boolean])
    val descriptor = validator.describeExecutable(constructor, None)

    executableValidator
      .validateExecutableParameters(
        descriptor,
        Array(
          10,
          Some(LocalDate.parse("2013-01-01")),
          true
        ),
        constructor.getParameters.map(_.getName)
      ).isEmpty should be(true)

    val violations: Set[ConstraintViolation[UsersRequest]] =
      executableValidator
        .validateExecutableParameters(
          descriptor,
          Array(
            200,
            Some(LocalDate.parse("2013-01-01")),
            true
          ),
          constructor.getParameters.map(_.getName)
        )
    violations.size should equal(1)

    val violation = violations.head
    violation.getMessage should equal("must be less than or equal to 100")
    violation.getPropertyPath.toString should equal("UsersRequest.max")
    violation.getRootBeanClass should equal(classOf[UsersRequest])
    violation.getRootBean should be(null)
    violation.getInvalidValue should be(200)
  }

  test("ScalaExecutableValidator#validateConstructorReturnValue") {
    val car = CarWithPassengerCount(
      Seq(
        Person("abcd1234", "J Doe", DefaultAddress),
        Person("abcd1235", "K Doe", DefaultAddress),
        Person("abcd1236", "L Doe", DefaultAddress),
        Person("abcd1237", "M Doe", DefaultAddress),
        Person("abcd1238", "N Doe", DefaultAddress)
      )
    )
    val constructor = classOf[CarWithPassengerCount].getConstructor(classOf[Seq[Person]])

    val violations: Set[ConstraintViolation[CarWithPassengerCount]] =
      executableValidator.validateConstructorReturnValue(
        constructor,
        car
      )
    violations.size should equal(1)

    val violation = violations.head
    violation.getMessage should equal("number of passenger(s) is not valid")
    violation.getPropertyPath.toString should equal("CarWithPassengerCount.<return value>")
    violation.getRootBeanClass should equal(classOf[CarWithPassengerCount])
    violation.getRootBean should equal(null)
    violation.getInvalidValue should be(car)
  }

  test("ScalaExecutableValidator#cross-parameter") {
    val rentalStation = RentalStation("Hertz")
    val method =
      classOf[RentalStation].getMethod("reserve", classOf[LocalDate], classOf[LocalDate])

    val start = LocalDate.now.plusDays(1)
    val end = LocalDate.now
    val violations = executableValidator.validateParameters(
      rentalStation,
      method,
      Array(start, end)
    )
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("start is not before end")
    violation.getPropertyPath.toString should equal("reserve.<cross-parameter>")
    violation.getRootBeanClass should equal(classOf[RentalStation])
    violation.getRootBean should equal(rentalStation)
    violation.getLeafBean should equal(rentalStation)
  }
}
