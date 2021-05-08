package com.twitter.util.validation

import com.twitter.util.validation.AssertViolationTest.WithViolation
import com.twitter.util.validation.caseclasses._
import com.twitter.util.validation.cfg.ConstraintMapping
import com.twitter.util.validation.constraints.{
  CountryCode,
  InvalidConstraint,
  StateConstraint,
  StateConstraintPayload,
  ValidPassengerCount
}
import com.twitter.util.validation.conversions.ConstraintViolationOps._
import com.twitter.util.validation.internal.AnnotationFactory
import com.twitter.util.validation.metadata.{CaseClassDescriptor, PropertyDescriptor}
import com.twitter.util.validation.internal.validators.{
  ISO3166CountryCodeConstraintValidator,
  InvalidConstraintValidator,
  NotEmptyAnyConstraintValidator,
  NotEmptyPathConstraintValidator,
  ValidPassengerCountConstraintValidator
}
import jakarta.validation.constraints.{Min, NotEmpty, Pattern, Size}
import jakarta.validation.{
  ConstraintDeclarationException,
  ConstraintValidator,
  ConstraintViolation,
  ConstraintViolationException,
  UnexpectedTypeException,
  Validation,
  ValidationException
}
import java.lang.annotation.Annotation
import java.time.LocalDate
import java.util
import java.util.{UUID => JUUID}
import org.hibernate.validator.HibernateValidatorConfiguration
import org.json4s.reflect.{ClassDescriptor, ConstructorDescriptor, Reflector}
import org.scalatest.BeforeAndAfterAll
import scala.jdk.CollectionConverters._

private object ScalaValidatorTest {
  val DefaultAddress: Address =
    Address(line1 = "1234 Main St", city = "Anywhere", state = "CA", zipcode = "94102")

  val CustomConstraintMappings: Set[ConstraintMapping] =
    Set(
      ConstraintMapping(
        classOf[ValidPassengerCount],
        classOf[ValidPassengerCountConstraintValidator],
        includeExistingValidators = false
      ),
      ConstraintMapping(
        classOf[InvalidConstraint],
        classOf[InvalidConstraintValidator]
      )
    )
}

class ScalaValidatorTest extends AssertViolationTest with BeforeAndAfterAll {
  import ScalaValidatorTest._

  override protected val validator: ScalaValidator =
    ScalaValidator.builder
      .withConstraintMappings(CustomConstraintMappings)
      .validator

  override protected def afterAll(): Unit = {
    validator.close()
    super.afterAll()
  }

  test("ScalaValidator#with custom message interpolator") {
    val withCustomMessageInterpolator = ScalaValidator.builder
      .withMessageInterpolator(new WrongMessageInterpolator)
      .validator
    try {
      val car = SmallCar(null, "DD-AB-123", 4)
      val violations = withCustomMessageInterpolator.validate(car)
      assertViolations(
        violations = violations,
        withViolations = Seq(
          WithViolation(
            "manufacturer",
            "Whatever you entered was wrong",
            null,
            classOf[SmallCar],
            car)
        )
      )
    } finally {
      withCustomMessageInterpolator.close()
    }
  }

  test("ScalaValidator#nested case class definition") {
    val value = OuterObject.InnerObject.SuperNestedCaseClass(4L)

    val violations = validator.validate(value)
    violations.size should equal(1)
    val violation = violations.head
    violation.getPropertyPath.toString should be("id")
    violation.getMessage should be("must be greater than or equal to 5")
    violation.getRootBeanClass should equal(classOf[OuterObject.InnerObject.SuperNestedCaseClass])
    violation.getRootBean should equal(value)
    violation.getInvalidValue should equal(4L)
  }

  test("ScalaValidator#unicode field name") {
    val value = UnicodeNameCaseClass(5, "")

    assertViolations(
      obj = value,
      withViolations = Seq(
        WithViolation(
          "name",
          "must not be empty",
          "",
          classOf[UnicodeNameCaseClass],
          value,
          getValidationAnnotations(classOf[UnicodeNameCaseClass], "name").lastOption
        ),
        WithViolation(
          "winning-id",
          "must be less than or equal to 3",
          5,
          classOf[UnicodeNameCaseClass],
          value,
          getValidationAnnotations(classOf[UnicodeNameCaseClass], "winning-id").lastOption
        ),
      )
    )
  }

  test("ScalaValidator#correctly handle not cascading for a non-case class type") {
    val value = WithBogusCascadedField("DD-AB-123", Seq(1, 2, 3))
    assertViolations(value)
  }

  test("ScalaValidator#with InvalidConstraint") {
    val value = AlwaysFails("fails")

    val e = intercept[RuntimeException] {
      validator.validate(value)
    }
    e.getMessage should equal("validator foo error")
  }

  test("ScalaValidator#with failing MethodValidation") {
    // fails the @NotEmpty on the id field but the method validation blows up
    val value = AlwaysFailsMethodValidation("")

    val e = intercept[ValidationException] {
      validator.validate(value)
    }
    e.getMessage should equal("oh noes!")
  }

  test("ScalaValidator#isConstraintValidator") {
    val stateConstraint =
      AnnotationFactory.newInstance(classOf[StateConstraint], Map.empty)
    validator.isConstraintAnnotation(stateConstraint) should be(true)
  }

  test("ScalaValidator#constraint mapping 1") {
    val value = PathNotEmpty(Path.Empty, "abcd1234")

    // no validator registered `@NotEmpty` for Path type
    intercept[UnexpectedTypeException] {
      validator.validate(value)
    }

    // define an additional constraint validator for `@NotEmpty` which works for
    // Path types. note we include all existing validators
    val pathCustomConstraintMapping =
      ConstraintMapping(classOf[NotEmpty], classOf[NotEmptyPathConstraintValidator])

    val withPathConstraintValidator: ScalaValidator =
      ScalaValidator.builder
        .withConstraintMapping(pathCustomConstraintMapping).validator

    try {
      val violations = withPathConstraintValidator.validate(value)
      violations.size should equal(1)
      violations.head.getPropertyPath.toString should be("path")
      violations.head.getMessage should be("must not be empty")
      violations.head.getRootBeanClass should equal(classOf[PathNotEmpty])
      violations.head.getRootBean should equal(value)
      violations.head.getInvalidValue should equal(Path.Empty)

      // valid instance
      withPathConstraintValidator
        .validate(PathNotEmpty(Path("node"), "abcd1234"))
        .isEmpty should be(true)
    } finally {
      withPathConstraintValidator.close()
    }

    // define an additional constraint validator for `@NotEmpty` which works for
    // Path types. note we do include all existing validators which will cause validation to
    // fail since we can no longer find a validator for `@NotEmpty` which works for string
    val pathCustomConstraintMapping2 =
      ConstraintMapping(
        classOf[NotEmpty],
        classOf[NotEmptyPathConstraintValidator],
        includeExistingValidators = false)

    val withPathConstraintValidator2: ScalaValidator =
      ScalaValidator.builder
        .withConstraintMapping(pathCustomConstraintMapping2).validator
    try {
      intercept[UnexpectedTypeException] {
        withPathConstraintValidator2.validate(value)
      }
    } finally {
      withPathConstraintValidator2.close()
    }
  }

  test("ScalaValidator#constraint mapping 2") {
    // attempting to add a mapping for the same annotation multiple times will result in an error
    val pathCustomConstraintMapping1 =
      ConstraintMapping(classOf[NotEmpty], classOf[NotEmptyPathConstraintValidator])
    val pathCustomConstraintMapping2 =
      ConstraintMapping(classOf[NotEmpty], classOf[NotEmptyAnyConstraintValidator])

    val e1 = intercept[ValidationException] {
      ScalaValidator.builder
        .withConstraintMappings(Set(pathCustomConstraintMapping1, pathCustomConstraintMapping2))
        .validator
    }
    e1.getMessage.endsWith(
      "jakarta.validation.constraints.NotEmpty is configured more than once via the programmatic constraint definition API.") should be(
      true)

    val pathCustomConstraintMapping3 =
      ConstraintMapping(classOf[NotEmpty], classOf[NotEmptyPathConstraintValidator])
    val pathCustomConstraintMapping4 =
      ConstraintMapping(
        classOf[NotEmpty],
        classOf[NotEmptyAnyConstraintValidator],
        includeExistingValidators = false)

    val e2 = intercept[ValidationException] {
      ScalaValidator.builder
        .withConstraintMappings(Set(pathCustomConstraintMapping3, pathCustomConstraintMapping4))
        .validator
    }

    e2.getMessage.endsWith(
      "jakarta.validation.constraints.NotEmpty is configured more than once via the programmatic constraint definition API.") should be(
      true)

    val pathCustomConstraintMapping5 =
      ConstraintMapping(
        classOf[NotEmpty],
        classOf[NotEmptyPathConstraintValidator],
        includeExistingValidators = false)
    val pathCustomConstraintMapping6 =
      ConstraintMapping(classOf[NotEmpty], classOf[NotEmptyAnyConstraintValidator])

    val e3 = intercept[ValidationException] {
      ScalaValidator.builder
        .withConstraintMappings(Set(pathCustomConstraintMapping5, pathCustomConstraintMapping6))
        .validator
    }

    e3.getMessage.endsWith(
      "jakarta.validation.constraints.NotEmpty is configured more than once via the programmatic constraint definition API.") should be(
      true)

    val pathCustomConstraintMapping7 =
      ConstraintMapping(
        classOf[NotEmpty],
        classOf[NotEmptyPathConstraintValidator],
        includeExistingValidators = false)
    val pathCustomConstraintMapping8 =
      ConstraintMapping(
        classOf[NotEmpty],
        classOf[NotEmptyAnyConstraintValidator],
        includeExistingValidators = false)

    val e4 = intercept[ValidationException] {
      ScalaValidator.builder
        .withConstraintMappings(Set(pathCustomConstraintMapping7, pathCustomConstraintMapping8))
        .validator
    }

    e4.getMessage.endsWith(
      "jakarta.validation.constraints.NotEmpty is configured more than once via the programmatic constraint definition API.") should be(
      true)
  }

  test("ScalaValidator#payload") {
    val address =
      Address(line1 = "1234 Main St", city = "Anywhere", state = "PA", zipcode = "94102")
    val violations = validator.validate(address)
    violations.size should equal(1)
    val violation = violations.head
    val payload = violation.getDynamicPayload(classOf[StateConstraintPayload]).orNull
    payload == null should be(false)
    payload.isInstanceOf[StateConstraintPayload] should be(true)
    payload should equal(StateConstraintPayload("PA", "CA"))
  }

  test("ScalaValidator#method validation payload") {
    val user = NestedUser(
      id = "abcd1234",
      person = Person("abcd1234", "R. Franklin", DefaultAddress),
      gender = "F",
      job = ""
    )
    val violations = validator.validate(user)
    violations.size should equal(1)
    val violation = violations.head
    val payload = violation.getDynamicPayload(classOf[NestedUserPayload]).orNull
    payload == null should be(false)
    payload.isInstanceOf[NestedUserPayload] should be(true)
    payload should equal(NestedUserPayload("abcd1234", ""))
  }

  test("ScalaValidator#newInstance") {
    val stateConstraint =
      AnnotationFactory.newInstance(classOf[StateConstraint], Map.empty)
    validator.isConstraintAnnotation(stateConstraint) should be(true)

    val minConstraint = AnnotationFactory.newInstance(
      classOf[Min],
      Map("value" -> 1L)
    )
    minConstraint.groups().isEmpty should be(true)
    minConstraint.message() should equal("{jakarta.validation.constraints.Min.message}")
    minConstraint.payload().isEmpty should be(true)
    minConstraint.value() should equal(1L)
    validator.isConstraintAnnotation(minConstraint) should be(true)

    val patternConstraint = AnnotationFactory.newInstance(
      classOf[Pattern],
      Map("regexp" -> ".*", "flags" -> Array[Pattern.Flag](Pattern.Flag.CASE_INSENSITIVE))
    )
    patternConstraint.groups().isEmpty should be(true)
    patternConstraint.message() should equal("{jakarta.validation.constraints.Pattern.message}")
    patternConstraint.payload().isEmpty should be(true)
    patternConstraint.regexp() should equal(".*")
    patternConstraint.flags() should equal(Array[Pattern.Flag](Pattern.Flag.CASE_INSENSITIVE))
    validator.isConstraintAnnotation(patternConstraint) should be(true)
  }

  test("ScalaValidator#constraint validators by annotation class") {
    val validators = validator.findConstraintValidators(classOf[CountryCode])
    validators.size should equal(1)
    validators.head.getClass.getName should be(
      classOf[ISO3166CountryCodeConstraintValidator].getName)

    validators.head
      .asInstanceOf[ConstraintValidator[CountryCode, String]].isValid("US", null) should be(true)
  }

  test("ScalaValidator#nulls") {
    val car = SmallCar(null, "DD-AB-123", 4)
    assertViolations(
      obj = car,
      withViolations = Seq(
        WithViolation("manufacturer", "must not be null", null, classOf[SmallCar], car)
      )
    )
  }

  test("ScalaValidator#not a case class") {
    val value = new NotACaseClass(null, "DD-AB-123", 4, "NotAUUID")
    val e = intercept[ValidationException] {
      validator.validate(value)
    }
    e.getMessage should equal(s"${classOf[NotACaseClass]} is not a valid case class.")

    // underlying validator doesn't find the Scala annotations (either executable nor inherited)
    val violations = validator.underlying.validate(value).asScala.toSet
    violations.isEmpty should be(true)
  }

  test("ScalaValidator#works with meta annotation 1") {
    val value = WithMetaFieldAnnotation("")

    assertViolations(
      obj = value,
      withViolations = Seq(
        WithViolation(
          "id",
          "must not be empty",
          "",
          classOf[WithMetaFieldAnnotation],
          value,
          getValidationAnnotations(classOf[WithMetaFieldAnnotation], "id").headOption
        )
      )
    )

    // also works with underlying validator
    val violations = validator.underlying.validate(value).asScala.toSet
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("id")
    violation.getInvalidValue should equal("")
    violation.getRootBeanClass should equal(classOf[WithMetaFieldAnnotation])
    violation.getRootBean should equal(value)
    violation.getLeafBean should equal(value)
  }

  test("ScalaValidator#works with meta annotation 2") {
    val value = WithPartialMetaFieldAnnotation("")

    assertViolations(
      obj = value,
      withViolations = Seq(
        WithViolation(
          "id",
          "must not be empty",
          "",
          classOf[WithPartialMetaFieldAnnotation],
          value,
          getValidationAnnotations(classOf[WithPartialMetaFieldAnnotation], "id").lastOption
        ),
        WithViolation(
          "id",
          "size must be between 2 and 10",
          "",
          classOf[WithPartialMetaFieldAnnotation],
          value,
          getValidationAnnotations(classOf[WithPartialMetaFieldAnnotation], "id").headOption
        )
      )
    )
  }

  test("ScalaValidator#validateValue 1") {
    val violations = validator.validateValue(classOf[OneOfSeqExample], "enumValue", Seq("g"))
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("g not one of [a, B, c]")
    violation.getPropertyPath.toString should equal("enumValue")
    violation.getInvalidValue should equal(Seq("g"))
    violation.getRootBeanClass should equal(classOf[OneOfSeqExample])
    violation.getRootBean should be(null)
    violation.getLeafBean should be(null)
  }

  test("ScalaValidator#validateValue 2") {
    val descriptor = validator.getConstraintsForClass(classOf[OneOfSeqExample])
    val violations = validator.validateValue(descriptor, "enumValue", Seq("g"))
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("g not one of [a, B, c]")
    violation.getPropertyPath.toString should equal("enumValue")
    violation.getInvalidValue should equal(Seq("g"))
    violation.getRootBeanClass should equal(classOf[OneOfSeqExample])
    violation.getRootBean should be(null)
    violation.getLeafBean should be(null)
  }

  test("ScalaValidator#validateProperty 1") {
    val value = CustomerAccount("", None)
    val violations = validator.validateProperty(value, "accountName")
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("accountName")
    violation.getInvalidValue should equal("")
    violation.getRootBeanClass should equal(classOf[CustomerAccount])
    violation.getRootBean should equal(value)
    violation.getLeafBean should equal(value)
  }

  test("ScalaValidator#validateProperty 2") {
    validator
      .validateProperty(
        MinIntExample(numberValue = 2),
        "numberValue"
      ).isEmpty should be(true)
    assertViolations(obj = MinIntExample(numberValue = 2))

    val invalid = MinIntExample(numberValue = 0)
    val violations = validator
      .validateProperty(
        invalid,
        "numberValue"
      )
    violations.size should equal(1)
    val violation = violations.head
    violation.getPropertyPath.toString should equal("numberValue")
    violation.getMessage should equal("must be greater than or equal to 1")
    violation.getRootBeanClass should equal(classOf[MinIntExample])
    violation.getRootBean.getClass.getName should equal(invalid.getClass.getName)

    assertViolations(
      obj = invalid,
      withViolations = Seq(
        WithViolation(
          "numberValue",
          "must be greater than or equal to 1",
          0,
          classOf[MinIntExample],
          invalid,
          getValidationAnnotations(classOf[MinIntExample], "numberValue").headOption
        )
      )
    )
  }

  test("ScalaValidator#validateFieldValue") {
    val constraints: Map[Class[_ <: Annotation], Map[String, Any]] =
      Map(classOf[jakarta.validation.constraints.Size] -> Map("min" -> 5, "max" -> 7))

    // size doesn't work for ints
    intercept[UnexpectedTypeException] {
      validator.validateFieldValue(constraints, "intField", 4)
    }

    // should work fine for collections of the right size
    validator
      .validateFieldValue(constraints, "javaSetIntField", newJavaSet(6)).isEmpty should be(true)
    validator
      .validateFieldValue(constraints, "seqIntField", Seq(1, 2, 3, 4, 5, 6)).isEmpty should be(true)
    validator
      .validateFieldValue(constraints, "setIntField", Set(1, 2, 3, 4, 5, 6)).isEmpty should be(true)
    validator
      .validateFieldValue(constraints, "arrayIntField", Array(1, 2, 3, 4, 5, 6)).isEmpty should be(
      true)
    validator
      .validateFieldValue(constraints, "stringField", "123456").isEmpty should be(true)

    // will fail for types of the wrong size
    val violations =
      validator.validateFieldValue(constraints, "seqIntField", Seq(1, 2, 3))
    violations.size should equal(1)
    val violation: ConstraintViolation[Any] = violations.head
    violation.getMessage should equal("size must be between 5 and 7")
    violation.getPropertyPath.toString should equal("seqIntField")
    violation.getInvalidValue should equal(Seq(1, 2, 3))
    violation.getRootBeanClass should equal(classOf[AnyRef])
    violation.getRootBean == null should be(true)
    violation.getLeafBean should be(null)

    val invalidJavaSet = newJavaSet(3)
    val violations2 = validator
      .validateFieldValue(constraints, "javaSetIntField", invalidJavaSet)
    violations2.size should equal(1)
    val violation2: ConstraintViolation[Any] = violations.head
    violation2.getMessage should equal("size must be between 5 and 7")
    violation2.getPropertyPath.toString should equal("seqIntField")
    violation2.getInvalidValue should equal(List(1, 2, 3))
    violation2.getRootBeanClass should equal(classOf[AnyRef])
    violation2.getRootBean == null should be(true)
    violation2.getLeafBean should be(null)

    val constraintsWithGroup: Map[Class[_ <: Annotation], Map[String, Any]] =
      Map(
        classOf[jakarta.validation.constraints.Size] -> Map(
          "min" -> 5,
          "max" -> 7,
          "groups" -> Array(classOf[PersonCheck]) // groups MUST be an array type
        )
      )

    // constraint group is not passed (not activated)
    validator
      .validateFieldValue(constraintsWithGroup, "seqIntField", Seq(1, 2, 3)).isEmpty should be(true)
    // constraint group is passed (activated)
    val violationsWithGroup = validator
      .validateFieldValue(constraintsWithGroup, "seqIntField", Seq(1, 2, 3), classOf[PersonCheck])
    violationsWithGroup.size should equal(1)
    val violationWg = violationsWithGroup.head
    violationWg.getMessage should equal("size must be between 5 and 7")
    violationWg.getPropertyPath.toString should equal("seqIntField")
    violationWg.getInvalidValue should equal(Seq(1, 2, 3))
    violationWg.getRootBeanClass should equal(classOf[AnyRef])
    violationWg.getRootBean == null should be(true)
    violationWg.getLeafBean should be(null)

    val violationsSt =
      validator.validateFieldValue(constraints, "setIntField", Set(1, 2, 3, 4, 5, 6, 7, 8))
    violationsSt.size should equal(1)
    val violationSt = violationsSt.head
    violationSt.getMessage should equal("size must be between 5 and 7")
    violationSt.getPropertyPath.toString should equal("setIntField")
    violationSt.getInvalidValue should equal(Set(1, 2, 3, 4, 5, 6, 7, 8))
    violationSt.getRootBeanClass should equal(classOf[AnyRef])
    violationSt.getRootBean == null should be(true)
    violationSt.getLeafBean should be(null)

    validator
      .validateFieldValue(
        Map[Class[_ <: Annotation], Map[String, AnyRef]](classOf[NotEmpty]
          .asInstanceOf[Class[_ <: Annotation]] -> Map[String, AnyRef]()),
        "data",
        "Hello, world").isEmpty should be(true)
  }

  test("ScalaValidator#groups support") {
    // see: https://docs.jboss.org/hibernate/stable/scalaValidator/reference/en-US/html_single/#chapter-groups
    val toTest = WithPersonCheck("", "Jane Doe")
    // PersonCheck group not enabled, no violations
    assertViolations(obj = toTest)
    // Completely diff group enabled, no violations
    validator.validate(obj = toTest, groups = classOf[OtherCheck])
    // PersonCheck group enabled
    assertViolations(
      obj = toTest,
      groups = Seq(classOf[PersonCheck]),
      withViolations = Seq(
        WithViolation("id", "must not be empty", "", classOf[WithPersonCheck], toTest)
      )
    )

    // multiple groups with PersonCheck group enabled
    assertViolations(
      obj = toTest,
      groups = Seq(classOf[OtherCheck], classOf[PersonCheck]),
      withViolations = Seq(
        WithViolation("id", "must not be empty", "", classOf[WithPersonCheck], toTest)
      )
    )
  }

  test("ScalaValidator#isCascaded method validation - defined fields") {
    // nested method validation fields
    val owner = Person(id = "", name = "A. Einstein", address = DefaultAddress)
    val car = Car(
      id = 1234,
      make = CarMake.Volkswagen,
      model = "Beetle",
      year = 1970,
      owners = Seq(Person(id = "9999", name = "", address = DefaultAddress)),
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
    val driver = Driver(
      person = owner,
      car = car
    )

    // no PersonCheck -- doesn't fail
    assertViolations(obj = driver, groups = Seq(classOf[PersonCheck]))
    // with default group - fail
    assertViolations(
      obj = driver,
      withViolations = Seq(
        WithViolation("car.owners[0].name", "must not be empty", "", classOf[Driver], driver),
        WithViolation("car.validateId", "id may not be even", car, classOf[Driver], driver),
        WithViolation(
          "car.warrantyTimeValid.warrantyEnd",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Driver],
          driver),
        WithViolation(
          "car.warrantyTimeValid.warrantyStart",
          "both warrantyStart and warrantyEnd are required for a valid range",
          car,
          classOf[Driver],
          driver),
        WithViolation(
          "car.year",
          "must be greater than or equal to 2000",
          1970,
          classOf[Driver],
          driver),
        WithViolation("person.id", "must not be empty", "", classOf[Driver], driver)
      )
    )

    // Validation with the default group does not return any violations with the standard
    // Hibernate Validator because Scala only places annotations the executable fields of the
    // case class by default and does copy them to the generated fields with the @field meta annotation.
    Validation
      .buildDefaultValidatorFactory()
      .getValidator
      .validate(driver)
      .asScala
      .isEmpty should be(true)

    val carWithInvalidNumberOfPassengers = Car(
      id = 1235,
      make = CarMake.Audi,
      model = "A4",
      year = 2010,
      owners = Seq(Person(id = "9999", name = "M Bailey", address = DefaultAddress)),
      licensePlate = "CA123",
      numDoors = 2,
      manual = true,
      ownershipStart = LocalDate.now,
      ownershipEnd = LocalDate.now.plusYears(10),
      warrantyStart = Some(LocalDate.now),
      warrantyEnd = Some(LocalDate.now.plusYears(3)),
      passengers = Seq.empty
    )
    val anotherDriver = Driver(
      person = Person(id = "55555", name = "K. Brown", address = DefaultAddress),
      car = carWithInvalidNumberOfPassengers
    )

    // ensure the class-level Car validation reports the correct property path of the field name
    assertViolations(
      obj = anotherDriver,
      withViolations = Seq(
        WithViolation(
          "car",
          "number of passenger(s) is not valid",
          carWithInvalidNumberOfPassengers,
          classOf[Driver],
          anotherDriver)
      )
    )
  }

  test("ScalaValidator#class-level annotations") {
    // The Car case class is annotated with @ValidPassengerCount which checks if the number of
    // passengers is greater than zero and less then some max specified in the annotation.
    // In this test, no passengers are defined so the validation fails the class-level constraint.
    // see: https://docs.jboss.org/hibernate/stable/scalaValidator/reference/en-US/html_single/#scalaValidator-usingscalaValidator-classlevel
    val car = Car(
      id = 1234,
      make = CarMake.Volkswagen,
      model = "Beetle",
      year = 2004,
      owners = Seq(Person(id = "9999", name = "K. Ann", address = DefaultAddress)),
      licensePlate = "CA123",
      numDoors = 2,
      manual = true,
      ownershipStart = LocalDate.now,
      ownershipEnd = LocalDate.now.plusYears(10),
      warrantyEnd = Some(LocalDate.now.plusYears(3))
    )

    assertViolations(
      obj = car,
      withViolations = Seq(
        WithViolation("", "number of passenger(s) is not valid", car, classOf[Car], car),
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

    // compare the class-level violation which should be the same as from the ScalaValidator
    val fromUnderlyingViolations = validator.underlying.validate(car).asScala.toSet
    fromUnderlyingViolations.size should equal(1)
    val violation = fromUnderlyingViolations.head
    violation.getPropertyPath.toString should equal("")
    violation.getMessage should equal("number of passenger(s) is not valid")
    violation.getRootBeanClass should equal(classOf[Car])
    violation.getInvalidValue should equal(car)
    violation.getRootBean should equal(car)

    // Validation with the default group does not return any violations with the standard
    // Hibernate Validator because Scala only places annotations the executable fields of the
    // case class by default and does copy them to the generated fields with the @field meta annotation.
    // However, class-level annotations will work.
    val configuration = Validation
      .byDefaultProvider()
      .configure().asInstanceOf[HibernateValidatorConfiguration]
    val mapping = configuration.createConstraintMapping()
    mapping
      .constraintDefinition(classOf[ValidPassengerCount])
      .validatedBy(classOf[ValidPassengerCountConstraintValidator])
    configuration.addMapping(mapping)

    val hibernateViolations = configuration.buildValidatorFactory.getValidator
      .validate(car)
      .asScala
    // only has class-level constraint violation
    val classLevelConstraintViolation = hibernateViolations.head
    classLevelConstraintViolation.getPropertyPath.toString should be("")
    classLevelConstraintViolation.getMessage should equal("number of passenger(s) is not valid")
    classLevelConstraintViolation.getRootBeanClass should equal(classOf[Car])
    classLevelConstraintViolation.getRootBean == car should be(true)

  }

  test("ScalaValidator#cascaded validations") {
    val validUser: User = User("1234567", "ion", "Other")
    val invalidUser: User = User("", "anion", "M")
    val nestedValidUser: Users = Users(Seq(validUser))
    val nestedInvalidUser: Users = Users(Seq(invalidUser))
    val nestedDuplicateUser: Users = Users(Seq(validUser, validUser))

    assertViolations(obj = validUser)
    assertViolations(
      obj = invalidUser,
      withViolations = Seq(
        WithViolation("id", "must not be empty", "", classOf[User], invalidUser)
      )
    )

    assertViolations(obj = nestedValidUser)
    assertViolations(
      obj = nestedInvalidUser,
      withViolations = Seq(
        WithViolation("users[0].id", "must not be empty", "", classOf[Users], nestedInvalidUser))
    )

    assertViolations(obj = nestedDuplicateUser)
  }

  /*
   * Builder withDescriptorCacheSize(...) tests
   */
  test("ScalaValidator#withDescriptorCacheSize should override the default cache size") {
    val customizedCacheSize: Long = 512
    val scalaValidator = ScalaValidator.builder
      .withDescriptorCacheSize(customizedCacheSize)
    scalaValidator.descriptorCacheSize should be(customizedCacheSize)
  }

  test("ScalaValidator#validate is valid") {
    val testUser: User = User(id = "9999", name = "April", gender = "F")
    validator.validate(testUser).isEmpty should be(true)
  }

  test("ScalaValidator#validate returns valid result even when case class has other fields") {
    val testPerson: Person = Person(id = "9999", name = "April", address = DefaultAddress)
    validator.validate(testPerson).isEmpty should be(true)
  }

  test("ScalaValidator#validate is invalid") {
    val testUser: User = User(id = "", name = "April", gender = "F")
    val fieldAnnotation = getValidationAnnotations(classOf[User], "id").headOption

    assertViolations(
      obj = testUser,
      withViolations = Seq(
        WithViolation("id", "must not be empty", "", classOf[User], testUser, fieldAnnotation)
      )
    )
  }

  test(
    "ScalaValidator#validate returns invalid result of both field validations and method validations") {
    val testUser: User = User(id = "", name = "", gender = "Female")

    val idAnnotation: Option[Annotation] =
      getValidationAnnotations(classOf[User], "id").headOption
    val genderAnnotation: Option[Annotation] =
      getValidationAnnotations(classOf[User], "gender").headOption
    val methodAnnotation: Option[Annotation] =
      getValidationAnnotations(classOf[User], "nameCheck").headOption

    assertViolations(
      obj = testUser,
      withViolations = Seq(
        WithViolation(
          "gender",
          "Female not one of [F, M, Other]",
          "Female",
          classOf[User],
          testUser,
          genderAnnotation),
        WithViolation("id", "must not be empty", "", classOf[User], testUser, idAnnotation),
        WithViolation(
          "nameCheck.name",
          "cannot be empty",
          testUser,
          classOf[User],
          testUser,
          methodAnnotation)
      )
    )
  }

  test("ScalaValidator#should register the user defined constraint validator") {
    val testState: StateValidationExample = StateValidationExample(state = "NY")
    val fieldAnnotation: Option[Annotation] =
      getValidationAnnotations(classOf[StateValidationExample], "state").headOption

    assertViolations(
      obj = testState,
      withViolations = Seq(
        WithViolation(
          "state",
          "Please register with state CA",
          "NY",
          classOf[StateValidationExample],
          testState,
          fieldAnnotation)
      )
    )
  }

  test("ScalaValidator#secondary case class constructors") {
    // the framework does not validate on construction, however, it must use
    // the case class executable for finding the field validation annotations
    // as Scala carries case class annotations on the executable for fields specified
    // and annotated in the executable. Validations only apply to executable parameters that are
    // also class fields, e.g., from the primary executable.
    assertViolations(obj = TestJsonCreator("42"))
    assertViolations(obj = TestJsonCreator(42))
    assertViolations(obj = TestJsonCreator2(scala.collection.immutable.Seq("1", "2", "3")))
    assertViolations(
      obj = TestJsonCreator2(scala.collection.immutable.Seq(1, 2, 3), default = "Goodbye, world"))

    assertViolations(obj = TestJsonCreatorWithValidation("42"))
    intercept[NumberFormatException] {
      // can't validate after the fact -- the instance is already constructed, then we validate
      assertViolations(obj = TestJsonCreatorWithValidation(""))
    }
    assertViolations(obj = TestJsonCreatorWithValidation(42))

    // annotations are on primary executable
    assertViolations(
      obj = TestJsonCreatorWithValidations("99"),
      withViolations = Seq(
        WithViolation(
          "int",
          "99 not one of [42, 137]",
          99,
          classOf[TestJsonCreatorWithValidations],
          TestJsonCreatorWithValidations("99")
        )
      )
    )
    assertViolations(obj = TestJsonCreatorWithValidations(42))

    assertViolations(obj = new CaseClassWithMultipleConstructors("10001", "20002", "30003"))
    assertViolations(obj = CaseClassWithMultipleConstructors(10001L, 20002L, 30003L))

    assertViolations(obj = CaseClassWithMultipleConstructorsAnnotated(10001L, 20002L, 30003L))
    assertViolations(obj =
      new CaseClassWithMultipleConstructorsAnnotated("10001", "20002", "30003"))

    assertViolations(
      obj = CaseClassWithMultipleConstructorsAnnotatedAndValidations(
        10001L,
        20002L,
        JUUID.randomUUID().toString))

    assertViolations(
      obj = new CaseClassWithMultipleConstructorsAnnotatedAndValidations(
        "10001",
        "20002",
        JUUID.randomUUID().toString))

    val invalid = new CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations(
      "9999",
      "10001",
      JUUID.randomUUID().toString)
    assertViolations(
      obj = invalid,
      withViolations = Seq(
        WithViolation(
          "number1",
          "must be greater than or equal to 10000",
          9999,
          classOf[CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations],
          invalid
        )
      )
    )

    val d = InvalidDoublePerson("Andrea")("")
    assertViolations(d)
    val d2 = DoublePerson("Andrea")("")
    assertViolations(
      obj = d2,
      withViolations =
        Seq(WithViolation("otherName", "must not be empty", "", classOf[DoublePerson], d2)))
    val d3 = ValidDoublePerson("Andrea")("")
    val ve = intercept[ValidationException] {
      validator.verify(d3)
    }
    ve.isInstanceOf[ConstraintViolationException] should be(true)
    val cve = ve.asInstanceOf[ConstraintViolationException]
    cve.getConstraintViolations.size should equal(2)
    cve.getMessage should equal(
      "\nValidation Errors:\t\tcheckOtherName: otherName must be longer than 3 chars, otherName: must not be empty\n\n")

    val d4 = PossiblyValidDoublePerson("Andrea")("")
    assertViolations(
      obj = d4,
      withViolations = Seq(
        WithViolation("otherName", "must not be empty", "", classOf[PossiblyValidDoublePerson], d4))
    )

    val d5 = WithFinalValField(
      JUUID.randomUUID().toString,
      Some("Joan"),
      Some("Jett"),
      Some(Set(1, 2, 3))
    )
    assertViolations(d5)
  }

  test("ScalaValidator#cycles") {
    validator.validate(A("5678", B("9876", C("444", null)))).isEmpty should be(true)
    validator.validate(D("1", E("2", F("3", None)))).isEmpty should be(true)
    validator.validate(G("1", H("2", I("3", Seq.empty[G])))).isEmpty should be(true)
  }

  test("ScalaValidator#scala BeanProperty") {
    val value = AnnotatedBeanProperties(3)
    value.setField1("")
    assertViolations(
      obj = value,
      withViolations = Seq(
        WithViolation("field1", "must not be empty", "", classOf[AnnotatedBeanProperties], value))
    )
  }

  test("ScalaValidator#case class with no executable params") {
    val value = NoConstructorParams()
    value.setId("1234")
    assertViolations(value)

    assertViolations(
      obj = NoConstructorParams(),
      withViolations = Seq(
        WithViolation(
          "id",
          "must not be empty",
          "",
          classOf[NoConstructorParams],
          NoConstructorParams()))
    )
  }

  test("ScalaValidator#case class annotated non executable field") {
    assertViolations(
      obj = AnnotatedInternalFields("1234", "thisisabigstring"),
      withViolations = Seq(
        WithViolation(
          "company",
          "must not be empty",
          "",
          classOf[AnnotatedInternalFields],
          AnnotatedInternalFields("1234", "thisisabigstring"))
      )
    )
  }

  test("ScalaValidator#inherited validation annotations") {
    assertViolations(
      obj = ImplementsAncestor(""),
      withViolations = Seq(
        WithViolation(
          "field1",
          "must not be empty",
          "",
          classOf[ImplementsAncestor],
          ImplementsAncestor("")),
        WithViolation(
          "validateField1.field1",
          "not a double value",
          ImplementsAncestor(""),
          classOf[ImplementsAncestor],
          ImplementsAncestor(""))
      )
    )

    assertViolations(
      obj = ImplementsAncestor("blimey"),
      withViolations = Seq(
        WithViolation(
          "validateField1.field1",
          "not a double value",
          ImplementsAncestor("blimey"),
          classOf[ImplementsAncestor],
          ImplementsAncestor("blimey")
        )
      )
    )

    assertViolations(obj = ImplementsAncestor("3.141592653589793d"))
  }

  test("ScalaValidator#getConstraintsForClass") {
    // there is no resolution of nested types in a description
    val driverCaseClassDescriptor = validator.getConstraintsForClass(classOf[Driver])
    driverCaseClassDescriptor.members.size should equal(2)

    verifyDriverClassDescriptor(driverCaseClassDescriptor)
  }

  test("ScalaValidator#annotations") {
    val caseClassDescriptor = validator.descriptorFactory.describe(classOf[TestAnnotations])
    caseClassDescriptor.members.size should equal(1)
    val (name, memberDescriptor) = caseClassDescriptor.members.head
    name should equal("cars")
    memberDescriptor.isInstanceOf[PropertyDescriptor] should be(true)
    memberDescriptor.asInstanceOf[PropertyDescriptor].annotations.length should equal(1)
    memberDescriptor
      .asInstanceOf[PropertyDescriptor].annotations.head.annotationType() should be(
      classOf[NotEmpty])
  }

  test("ScalaValidator#generics 1") {
    val caseClassDescriptor =
      validator.descriptorFactory.describe(classOf[GenericTestCaseClass[_]])
    caseClassDescriptor.members.size should equal(1)
    val value = GenericTestCaseClass(data = "Hello, World")
    val results = validator.validate(value)
    results.isEmpty should be(true)

    intercept[UnexpectedTypeException] {
      validator.validate(GenericTestCaseClass(3))
    }

    val genericValue = GenericTestCaseClass[Seq[String]](Seq.empty[String])
    val violations = validator.validate(genericValue)
    violations.size should equal(1)
    val violation = violations.head
    violation.getMessage should equal("must not be empty")
    violation.getPropertyPath.toString should equal("data")
    violation.getRootBeanClass should equal(classOf[GenericTestCaseClass[Seq[String]]])
    violation.getLeafBean should equal(genericValue)
    violation.getInvalidValue should equal(Seq.empty[String])

    validator.validate(GenericMinTestCaseClass(5)).isEmpty should be(true)

    val caseClassDescriptor1 =
      validator.descriptorFactory.describe(classOf[GenericTestCaseClassMultipleTypes[_, _, _]])
    caseClassDescriptor1.members.size should equal(3)

    val value1 = GenericTestCaseClassMultipleTypes(
      data = None,
      things = Seq(1, 2, 3),
      otherThings = Seq(
        UUIDExample("1234"),
        UUIDExample(JUUID.randomUUID().toString),
        UUIDExample(JUUID.randomUUID().toString))
    )
    val results1 = validator.validate(value1)
    results1.isEmpty should be(true)
  }

  test("ScalaValidator#generics 2") {
    val value: Page[Person] = Page[Person](
      List(
        Person(id = "9999", name = "April", address = DefaultAddress),
        Person(id = "9999", name = "April", address = DefaultAddress)
      ),
      0,
      None,
      None)

    val caseClassDescriptor =
      validator.descriptorFactory.describe(classOf[Page[Person]])
    caseClassDescriptor.members.size should equal(4)

    val violations = validator.validate(value)
    violations.size should equal(1)

    val violation = violations.head
    violation.getPropertyPath.toString should be("pageSize")
    violation.getMessage should be("must be greater than or equal to 1")
    violation.getRootBeanClass should equal(classOf[Page[Person]])
    violation.getRootBean should equal(value)
    violation.getInvalidValue should equal(0)
  }

  test("ScalaValidator#test boxed primitives") {
    validator
      .validateValue(
        classOf[CaseClassWithBoxedPrimitives],
        "events",
        java.lang.Integer.valueOf(42)).isEmpty should be(true)
  }

  test("ScalaValidator#find executable 1") {
    /* CaseClassWithMultipleConstructorsAnnotatedAndValidations */
    val clazzDescriptor: ClassDescriptor =
      Reflector
        .describe(classOf[CaseClassWithMultipleConstructorsAnnotatedAndValidations])
        .asInstanceOf[ClassDescriptor]

    val constructorDescriptor: ConstructorDescriptor =
      validator.descriptorFactory.findConstructor(clazzDescriptor /*, Seq.empty*/ )

    // by default this will find Seq(Long, Long, String)
    val constructorParameterTypes: Array[Class[_]] =
      constructorDescriptor.constructor.getParameterTypes()
    constructorParameterTypes.length should equal(3)
    constructorParameterTypes(0) should equal(classOf[Long])
    constructorParameterTypes(1) should equal(classOf[Long])
    constructorParameterTypes(2) should equal(classOf[String])

    val caseClazzDescriptor = validator.getConstraintsForClass(
      classOf[CaseClassWithMultipleConstructorsAnnotatedAndValidations])
    caseClazzDescriptor.members.size should equal(3)
    caseClazzDescriptor.members.contains("number1") should be(true)
    caseClazzDescriptor.members("number1").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("number2") should be(true)
    caseClazzDescriptor.members("number2").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("uuid") should be(true)
    caseClazzDescriptor.members("uuid").annotations.isEmpty should be(true)
  }

  test("ScalaValidator#find executable 2") {
    /* CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations */
    val clazzDescriptor = Reflector
      .describe(classOf[CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations])
      .asInstanceOf[ClassDescriptor]

    val constructorDescriptor =
      validator.descriptorFactory.findConstructor(clazzDescriptor)

    // by default this will find Seq(Long, Long, String)
    val constructorParameterTypes: Array[Class[_]] =
      constructorDescriptor.constructor.getParameterTypes()
    constructorParameterTypes.length should equal(3)
    constructorParameterTypes(0) should equal(classOf[Long])
    constructorParameterTypes(1) should equal(classOf[Long])
    constructorParameterTypes(2) should equal(classOf[String])

    val caseClazzDescriptor = validator.getConstraintsForClass(
      classOf[CaseClassWithMultipleConstructorsPrimaryAnnotatedAndValidations])
    caseClazzDescriptor.members.size should equal(3)
    caseClazzDescriptor.members.contains("number1") should be(true)
    caseClazzDescriptor.members("number1").annotations.length should equal(1)
    caseClazzDescriptor.members.contains("number2") should be(true)
    caseClazzDescriptor.members("number2").annotations.length should equal(1)
    caseClazzDescriptor.members.contains("uuid") should be(true)
    caseClazzDescriptor.members("uuid").annotations.length should equal(1)
  }

  test("ScalaValidator#find executable 3") {
    /* CaseClassWithMultipleConstructorsAnnotated */
    val clazzDescriptor = Reflector
      .describe(classOf[CaseClassWithMultipleConstructorsAnnotated])
      .asInstanceOf[ClassDescriptor]

    val constructorDescriptor =
      validator.descriptorFactory.findConstructor(clazzDescriptor)

    // this should find Seq(Long, Long, Long)
    val constructorParameterTypes: Array[Class[_]] =
      constructorDescriptor.constructor.getParameterTypes()
    constructorParameterTypes.length should equal(3)
    constructorParameterTypes(0) should equal(classOf[Long])
    constructorParameterTypes(1) should equal(classOf[Long])
    constructorParameterTypes(2) should equal(classOf[Long])

    val caseClazzDescriptor =
      validator.getConstraintsForClass(classOf[CaseClassWithMultipleConstructorsAnnotated])
    caseClazzDescriptor.members.size should equal(3)
    caseClazzDescriptor.members.contains("number1") should be(true)
    caseClazzDescriptor.members("number1").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("number2") should be(true)
    caseClazzDescriptor.members("number2").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("number3") should be(true)
    caseClazzDescriptor.members("number3").annotations.isEmpty should be(true)
  }

  test("ScalaValidator#find executable 4") {
    /* CaseClassWithMultipleConstructors */
    val clazzDescriptor = Reflector
      .describe(classOf[CaseClassWithMultipleConstructors])
      .asInstanceOf[ClassDescriptor]

    val constructorDescriptor =
      validator.descriptorFactory.findConstructor(clazzDescriptor)

    // this should find Seq(Long, Long, Long)
    val constructorParameterTypes: Array[Class[_]] =
      constructorDescriptor.constructor.getParameterTypes()
    constructorParameterTypes.length should equal(3)
    constructorParameterTypes(0) should equal(classOf[Long])
    constructorParameterTypes(1) should equal(classOf[Long])
    constructorParameterTypes(2) should equal(classOf[Long])

    val caseClazzDescriptor =
      validator.getConstraintsForClass(classOf[CaseClassWithMultipleConstructors])
    caseClazzDescriptor.members.size should equal(3)
    caseClazzDescriptor.members.contains("number1") should be(true)
    caseClazzDescriptor.members("number1").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("number2") should be(true)
    caseClazzDescriptor.members("number2").annotations.isEmpty should be(true)
    caseClazzDescriptor.members.contains("number3") should be(true)
    caseClazzDescriptor.members("number3").annotations.isEmpty should be(true)
  }

  test("ScalaValidator#find executable 5") {
    /* TestJsonCreatorWithValidations */
    val clazzDescriptor = Reflector
      .describe(classOf[TestJsonCreatorWithValidations])
      .asInstanceOf[ClassDescriptor]

    val constructorDescriptor =
      validator.descriptorFactory.findConstructor(clazzDescriptor)

    // should find Seq(int)
    val constructorParameterTypes: Array[Class[_]] =
      constructorDescriptor.constructor.getParameterTypes()
    constructorParameterTypes.length should equal(1)
    constructorParameterTypes.head should equal(classOf[Int])

    val caseClazzDescriptor =
      validator.getConstraintsForClass(classOf[TestJsonCreatorWithValidations])
    caseClazzDescriptor.members.size should equal(1)
    caseClazzDescriptor.members.contains("int") should be(true)
    caseClazzDescriptor.members("int").annotations.length should equal(1)
  }

  test("ScalaValidator#with Logging trait") {
    val value = PersonWithLogging(42, "Baz Var", None)
    validator.validate(value).isEmpty should be(true)
  }

  private[this] def newJavaSet(numElements: Int): util.Set[Int] = {
    val result: util.Set[Int] = new util.HashSet[Int]()
    for (i <- 1 to numElements) {
      result.add(i)
    }
    result
  }

  private[this] def verifyDriverClassDescriptor(
    driverCaseClassDescriptor: CaseClassDescriptor[Driver]
  ): Unit = {
    val person = driverCaseClassDescriptor.members("person")
    person.scalaType.erasure should equal(classOf[Person])
    // cascaded types will have a contained scala type
    person.cascadedScalaType.isDefined should be(true)
    person.cascadedScalaType.get.erasure should equal(classOf[Person])
    person.annotations.isEmpty should be(true)
    person.isCascaded should be(true)

    // person is cascaded, so let's look it up
    val personDescriptor = validator.getConstraintsForClass(classOf[Person])
    personDescriptor.members.size should equal(6)
    val city = personDescriptor.members("city")
    city.scalaType.erasure should equal(classOf[String])
    city.annotations.isEmpty should be(true)
    city.isCascaded should be(false)

    val name = personDescriptor.members("name")
    name.scalaType.erasure == classOf[String] should be(true)
    name.annotations.length should equal(1)
    name.annotations.head.annotationType() should equal(classOf[NotEmpty])
    name.isCascaded should be(false)

    val state = personDescriptor.members("state")
    state.scalaType.erasure should equal(classOf[String])
    state.annotations.isEmpty should be(true)
    state.isCascaded should be(false)

    val company = personDescriptor.members("company")
    company.scalaType.erasure should equal(classOf[String])
    company.annotations.isEmpty should be(true)
    company.isCascaded should be(false)

    val id = personDescriptor.members("id")
    id.scalaType.erasure == classOf[String] should be(true)
    id.annotations.length should equal(1)
    id.annotations.head.annotationType() should equal(classOf[NotEmpty])
    id.isCascaded should be(false)

    val address = personDescriptor.members("address")
    address.scalaType.erasure should equal(classOf[Address])
    address.annotations.isEmpty should be(true)
    address.isCascaded should be(true)

    // address is cascaded, so let's look it up
    val addressDescriptor = validator.getConstraintsForClass(classOf[Address])
    addressDescriptor.members.size should equal(6)

    val line1 = addressDescriptor.members("line1")
    line1.scalaType.erasure == classOf[String] should be(true)
    line1.annotations.isEmpty should be(true)
    line1.isCascaded should be(false)

    val line2 = addressDescriptor.members("line2")
    line2.scalaType.erasure == classOf[Option[String]] should be(true)
    line2.annotations.isEmpty should be(true)
    line2.isCascaded should be(false)

    val addressCity = addressDescriptor.members("city")
    addressCity.scalaType.erasure == classOf[String] should be(true)
    addressCity.annotations.isEmpty should be(true)
    addressCity.isCascaded should be(false)

    val addressState = addressDescriptor.members("state")
    addressState.scalaType.erasure == classOf[String] should be(true)
    addressState.annotations.length should equal(1)
    addressState.annotations.head.annotationType() should equal(classOf[StateConstraint])
    addressState.isCascaded should be(false)

    val zipcode = addressDescriptor.members("zipcode")
    zipcode.scalaType.erasure == classOf[String] should be(true)
    zipcode.annotations.isEmpty should be(true)
    zipcode.isCascaded should be(false)

    val additionalPostalCode = addressDescriptor.members("additionalPostalCode")
    additionalPostalCode.scalaType.erasure == classOf[Option[String]] should be(true)
    // not cascaded so no contained type
    additionalPostalCode.cascadedScalaType.isEmpty should be(true)
    additionalPostalCode.annotations.isEmpty should be(true)
    additionalPostalCode.isCascaded should be(false)

    personDescriptor.methods.length should equal(1)
    personDescriptor.methods.head.method.getName should equal("validateAddress")
    personDescriptor.methods.head.annotations.length should equal(1)
    personDescriptor.methods.head.annotations.head.annotationType() should equal(
      classOf[MethodValidation])
    personDescriptor.methods.head.members.size should equal(0)

    val car = driverCaseClassDescriptor.members("car")
    car.scalaType.erasure should equal(classOf[Car])
    car.cascadedScalaType.isDefined should be(true)
    car.cascadedScalaType.get.erasure should equal(classOf[Car])
    car.annotations.isEmpty should be(true)
    car.isCascaded should be(true)

    // car is cascaded, so let's look it up
    val carDescriptor = validator.getConstraintsForClass(classOf[Car])
    carDescriptor.members.size should equal(13)

    // lets just look at annotated and cascaded fields in car
    val year = carDescriptor.members("year")
    year.scalaType.erasure == classOf[Int] should be(true)
    year.annotations.length should equal(1)
    year.annotations.head.annotationType() should equal(classOf[Min])
    year.isCascaded should be(false)

    val owners = carDescriptor.members("owners")
    owners.scalaType.erasure == classOf[Seq[Person]] should be(true)
    owners.cascadedScalaType.isDefined should be(true)
    owners.cascadedScalaType.get.erasure should equal(classOf[Person])
    owners.annotations.isEmpty should be(true)
    owners.isCascaded should be(true)

    val licensePlate = carDescriptor.members("licensePlate")
    licensePlate.scalaType.erasure == classOf[String] should be(true)
    licensePlate.annotations.length should equal(2)
    licensePlate.annotations.head.annotationType() should equal(classOf[NotEmpty])
    licensePlate.annotations.last.annotationType() should equal(classOf[Size])
    licensePlate.isCascaded should be(false)

    val numDoors = carDescriptor.members("numDoors")
    numDoors.scalaType.erasure == classOf[Int] should be(true)
    numDoors.annotations.length should equal(1)
    numDoors.annotations.head.annotationType() should equal(classOf[Min])
    numDoors.isCascaded should be(false)

    val passengers = carDescriptor.members("passengers")
    passengers.scalaType.erasure == classOf[Seq[Person]] should be(true)
    passengers.cascadedScalaType.isDefined should be(true)
    passengers.cascadedScalaType.get.erasure should equal(classOf[Person])
    passengers.annotations.isEmpty should be(true)
    passengers.isCascaded should be(true)

    carDescriptor.methods.length should equal(4)
    val mappedCarMethods = carDescriptor.methods.map { methodDescriptor =>
      methodDescriptor.method.getName -> methodDescriptor
    }.toMap

    val validateId = mappedCarMethods("validateId")
    validateId.members.isEmpty should be(true)
    validateId.annotations.length should equal(1)
    validateId.annotations.head.annotationType() should equal(classOf[MethodValidation])
    validateId.members.size should equal(0)

    val validateYearBeforeNow = mappedCarMethods("validateYearBeforeNow")
    validateYearBeforeNow.members.isEmpty should be(true)
    validateYearBeforeNow.annotations.length should equal(1)
    validateYearBeforeNow.annotations.head.annotationType() should equal(classOf[MethodValidation])
    validateYearBeforeNow.members.size should equal(0)

    val ownershipTimesValid = mappedCarMethods("ownershipTimesValid")
    ownershipTimesValid.members.isEmpty should be(true)
    ownershipTimesValid.annotations.length should equal(1)
    ownershipTimesValid.annotations.head.annotationType() should equal(classOf[MethodValidation])
    ownershipTimesValid.members.size should equal(0)

    val warrantyTimeValid = mappedCarMethods("warrantyTimeValid")
    warrantyTimeValid.members.isEmpty should be(true)
    warrantyTimeValid.annotations.length should equal(1)
    warrantyTimeValid.annotations.head.annotationType() should equal(classOf[MethodValidation])
    warrantyTimeValid.members.size should equal(0)
  }

  test("ScalaValidator#class with incorrect method validation definition 1") {
    val value = WithIncorrectlyDefinedMethodValidation("abcd1234")

    val e = intercept[ConstraintDeclarationException] {
      validator.validate(value)
    }
    e.getMessage should equal(
      "Methods annotated with @MethodValidation must not declare any arguments")
  }

  test("ScalaValidator#class with incorrect method validation definition 2") {
    val value = AnotherIncorrectlyDefinedMethodValidation("abcd1234")

    val e = intercept[ConstraintDeclarationException] {
      validator.validate(value)
    }
    e.getMessage should equal(
      "Methods annotated with @MethodValidation must return a com.twitter.util.validation.engine.MethodValidationResult")
  }

  private[this] def getValidationAnnotations(
    clazz: Class[_],
    name: String
  ): Array[Annotation] = {
    val caseClassDescriptor = validator.descriptorFactory.describe(clazz)
    caseClassDescriptor.members.get(name) match {
      case Some(property: PropertyDescriptor) =>
        property.annotations
      case _ => Array.empty[Annotation]
    }
  }
}
