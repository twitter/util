package com.twitter.util.validation

import com.twitter.util.validation.AssertViolationTest._
import com.twitter.util.validation.caseclasses._

/**
 * Options are "unwrapped" by default for validation, meaning any supplied constraint
 * annotation is applied to the contained type/value of the Option. The unwrapping
 * happens through the Validator and not in the individual ConstraintValidator thus we
 * test though a Validator here and why these tests are not in the individual ConstraintValidator
 * tests.
 */
class OptionUnwrappingTest extends AssertViolationTest {

  override protected val validator: ScalaValidator = ScalaValidator.builder.validator

  test("CountryCode") {
    assertViolations(
      obj = CountryCodeOptionExample(Some("11")),
      withViolations = Seq(
        WithViolation(
          "countryCode",
          "11 not a valid country code",
          "11",
          classOf[CountryCodeOptionExample],
          CountryCodeOptionExample(Some("11")))
      )
    )
    assertViolations(obj = CountryCodeOptionExample(None))

    assertViolations(
      obj = CountryCodeOptionSeqExample(Some(Seq("11", "22"))),
      withViolations = Seq(
        WithViolation(
          "countryCode",
          "[11, 22] not a valid country code",
          Seq("11", "22"),
          classOf[CountryCodeOptionSeqExample],
          CountryCodeOptionSeqExample(Some(Seq("11", "22")))
        )
      )
    )
    assertViolations(obj = CountryCodeOptionSeqExample(None))

    assertViolations(
      obj = CountryCodeOptionArrayExample(Some(Array("11", "22"))),
      withViolations = Seq(
        WithViolation(
          "countryCode",
          "[11, 22] not a valid country code",
          Array("11", "22"),
          classOf[CountryCodeOptionArrayExample],
          CountryCodeOptionArrayExample(Some(Array("11", "22")))
        )
      )
    )
    assertViolations(obj = CountryCodeOptionArrayExample(None))

    assertViolations(
      obj = CountryCodeOptionInvalidTypeExample(Some(12345L)),
      withViolations = Seq(
        WithViolation(
          "countryCode",
          "12345 not a valid country code",
          12345L,
          classOf[CountryCodeOptionInvalidTypeExample],
          CountryCodeOptionInvalidTypeExample(Some(12345L)))
      )
    )
    assertViolations(obj = CountryCodeOptionInvalidTypeExample(None))
  }

  test("OneOf") {
    assertViolations(
      obj = OneOfOptionExample(Some("g")),
      withViolations = Seq(
        WithViolation(
          "enumValue",
          "g not one of [a, B, c]",
          "g",
          classOf[OneOfOptionExample],
          OneOfOptionExample(Some("g")))
      )
    )
    assertViolations(OneOfOptionExample(None))

    assertViolations(
      obj = OneOfOptionSeqExample(Some(Seq("g", "h", "i"))),
      withViolations = Seq(
        WithViolation(
          "enumValue",
          "[g, h, i] not one of [a, B, c]",
          Seq("g", "h", "i"),
          classOf[OneOfOptionSeqExample],
          OneOfOptionSeqExample(Some(Seq("g", "h", "i"))))
      )
    )
    assertViolations(OneOfOptionSeqExample(None))

    assertViolations(
      obj = OneOfOptionInvalidTypeExample(Some(10L)),
      withViolations = Seq(
        WithViolation(
          "enumValue",
          "10 not one of [a, B, c]",
          10L,
          classOf[OneOfOptionInvalidTypeExample],
          OneOfOptionInvalidTypeExample(Some(10L)))
      )
    )
    assertViolations(OneOfOptionSeqExample(None))
    assertViolations(OneOfOptionInvalidTypeExample(None))
  }

  test("UUID") {
    assertViolations(
      obj = UUIDOptionExample(Some("abcd1234")),
      withViolations = Seq(
        WithViolation(
          "uuid",
          "must be a valid UUID",
          "abcd1234",
          classOf[UUIDOptionExample],
          UUIDOptionExample(Some("abcd1234")))
      )
    )
    assertViolations(obj = UUIDOptionExample(None))
  }

  test("NestedOptionExample") {
    assertViolations(
      obj = NestedOptionExample(
        "abcd1234",
        "Foo Var",
        None
      ),
    )

    val value = NestedOptionExample(
      "",
      "Foo Var",
      Some(
        User(
          "",
          "Bar Var",
          "H"
        )
      )
    )
    assertViolations(
      obj = value,
      withViolations = Seq(
        WithViolation(
          "id",
          "must not be empty",
          "",
          classOf[NestedOptionExample],
          value
        ),
        WithViolation(
          "user.gender",
          "H not one of [F, M, Other]",
          "H",
          classOf[NestedOptionExample],
          value),
        WithViolation("user.id", "must not be empty", "", classOf[NestedOptionExample], value)
      )
    )
  }
}
