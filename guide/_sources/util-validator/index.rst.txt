.. _util-validator-index:

util-validator Guide
====================

Preface
-------

Validating data is a common task that occurs throughout all application layers, from the
presentation to the persistence layer. Often the same validation logic is implemented in each
layer which is time consuming and error-prone. To avoid duplication of these validations,
developers often bundle validation logic directly into the domain model, cluttering domain classes
with validation code which is really metadata about the class itself.

The `Jakarta Bean Validation 2.0 <https://beanvalidation.org/2.0/>`__ - defines a metadata model and API
for entity and method validation. The default metadata source are Java annotations (with the ability
to override and extend the meta-data through the use of XML if desired). The API is not tied to a
specific application tier nor programming model and it is specifically not tied to either the web or
persistence tier, and is available for both server-side application programming or rich client
applications.

The `ScalaValidator` is a Scala wrapper around the Java `Hibernate Validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/>`__
which itself is the reference implementation of `Jakarta Bean Validation specification <https://beanvalidation.org/>`__.

Getting Started
---------------

The `ScalaValidator` depends only on the `Hibernate Validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/>`__,
the `Glassfish reference implementation <https://github.com/eclipse-ee4j/el-ri>`__ of the
`Jakarta Expression Language <https://projects.eclipse.org/projects/ee4j.el>`__ for message interpolation,
and `json4s-core <https://github.com/json4s/json4s#guide>`__ for Scala reflection.

We highly recommend reading through the Hibernate Validator `documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/>`__
as our guide is largely based on this information.

In order to use the `util-validator` add a dependency on the project, e.g. in `sbt <https://scala-sbt.org>`__:

.. code-block:: scala

    "com.twitter" %% "util-validator" % utilValidatorVersion

or with `Maven <https://maven.apache.org/>`__:

.. code-block:: xml

    <dependency>
        <groupId>com.twitter</groupId>
        <artifactId>util-validator_2.12</artifactId>
        <version>${util.validator.version}</version>
    </dependency>

where `utilValidatorVersion` and `util.validator.version` are the expected library version.

Applying constraints
~~~~~~~~~~~~~~~~~~~~

Let's start with a basic example to see how to apply constraints. Let's assume you have a
case class defined as such:

.. code-block:: scala

    package com.example

    import jakarta.validation.constraints.{Min, NotEmpty, Size}

    case class Car(
      @NotEmpty manufacturer: String,
      @NotEmpty @Size(min = 2, max = 14) licensePlate: String,
      @Min(2) seatCount: Int)

The `@NotEmpty`, `@Size` and `@Min` annotations are used to declare the constraints which should be
applied to the fields of a `Car` instance:

* `manufacturer` must never be an empty String (and thus also not `null`)
* `licensePlate` must never be an empty String (thus not also not `null`) and must be between 2 and 14 characters
* `seatCount` must be equal to at least 2

Validating constraints
~~~~~~~~~~~~~~~~~~~~~~

`ScalaValidator#validate`
^^^^^^^^^^^^^^^^^^^^^^^^^

To perform a validation of these constraints, you use an instance of the `ScalaValidator`. Let’s
take a look at a test for validating `Car` instances:

.. code-block:: scala
   :emphasize-lines: 15,25,35,45

    package com.example

    import com.twitter.util.validation.ScalaValidator
    import jakarta.validation.ConstraintViolation
    import org.scalatest.funsuite.AnyFunSuite
    import org.scalatest.matchers.should.Matchers

    class CarTest extends AnyFunSuite with Matchers {

      private[this] val validator: ScalaValidator = ScalaValidator()

      test("manufacturer is empty") {
        val car: Car = Car("", "DD-AB-123", 4)

        val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
        violations.size should equal(1)
        val violation = violations.head
        violation.getPropertyPath.toString should equal("manufacturer")
        violation.getMessage should be("must not be empty")
      }

      test("licensePlate is too short") {
        val car: Car = Car("Greenwich", "D", 4)

        val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
        violations.size should equal(1)
        val violation = violations.head
        violation.getPropertyPath.toString should equal("licensePlate")
        violation.getMessage should be("size must be between 2 and 14")
      }

      test("seatCount is too small") {
        val car: Car = Car("Greenwich", "DD-AB-123", 1)

        val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
        violations.size should equal(1)
        val violation = violations.head
        violation.getPropertyPath.toString should equal("seatCount")
        violation.getMessage should be("must be greater than or equal to 2")
      }

      test("car is valid") {
        val car: Car = Car("Greenwich", "DD-AB-123", 2)

        val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
        violations.isEmpty should be(true)
      }
    }

In the test constructor we instantiate an instance of a `ScalaValidator`. A `ScalaValidator` instance
is thread-safe and may be reused multiple times. It thus can safely be stored in a member variable
field and  be used in the test cases to validate the different `Car` instances.

The `ScalaValidator#validate` method returns a set of `ConstraintViolation` instances, which you can
iterate over in order to see which validation errors occurred. The first three tests show some
expected constraint violations:

* The `@NotEmpty` constraint on `manufacturer` is violated in `test("manufacturer is empty")`
* The `@Size` constraint on `licensePlate` is violated in `test("licensePlate is too short")`
* The `@Min` constraint on `seatCount` is violated in `test("seatCount is too small")`

If the object validates successfully, `ScalaValidator#validate` returns an empty set as you can see
in `test("car is valid")`.

Note that this method is recursive and will cascade validations as explained in the section on
`Object graphs <#object-graphs>`__.

`ScalaValidator#verify`
^^^^^^^^^^^^^^^^^^^^^^^

The `ScalaValidator` also has an API to throw constraint violations as a type of `ValidationException`
rather than returning a `Set[ConstraintViolation[_]`.

Instead of calling `ScalaValidator#validate`, use `ScalaValidator#verify`:

.. code-block:: scala
   :emphasize-lines: 16,28,40,52

    package com.example

    import com.twitter.util.validation.ScalaValidator
    import jakarta.validation.{ConstraintViolation, ConstraintViolationException}
    import org.scalatest.funsuite.AnyFunSuite
    import org.scalatest.matchers.should.Matchers

    class CarTest extends AnyFunSuite with Matchers {

      private[this] val validator: ScalaValidator = ScalaValidator()

      test("manufacturer is empty") {
        val car: Car = Car("", "DD-AB-123", 4)

        val e = intercept[ConstraintViolationException] {
          validator.verify(car)
        }
        e.getConstraintViolations.size should equal(1)
        val violation = e.getConstraintViolations.iterator.next
        violation.getPropertyPath.toString should equal("manufacturer")
        violation.getMessage should be("must not be empty")
      }

      test("licensePlate is too short") {
        val car: Car = Car("Greenwich", "D", 4)

        val e = intercept[ConstraintViolationException] {
          validator.verify(car)
        }
        e.getConstraintViolations.size should equal(1)
        val violation = e.getConstraintViolations.iterator.next
        violation.getPropertyPath.toString should equal("licensePlate")
        violation.getMessage should be("size must be between 2 and 14")
      }

      test("seatCount is too small") {
        val car: Car = Car("Greenwich", "DD-AB-123", 1)

        val e = intercept[ViolationException] {
          validator.verify(car)
        }
        e.isInstanceOf[ConstraintViolationException] should be(true)
        e.asInstanceOf[ConstraintViolationException].getConstraintViolations.size should equal(1)
        val violation = e.asInstanceOf[ConstraintViolationException].getConstraintViolations.iterator.next
        violation.getPropertyPath.toString should equal("seatCount")
        violation.getMessage should be("must be greater than or equal to 2")
      }

      test("car is valid") {
        val car: Car = Car("Greenwich", "DD-AB-123", 2)

        validator.verify(car)
      }
    }

Like `ScalaValidator#validate`, this method is recursive and will cascade validations as explained
in the section on `Object graphs <#object-graphs>`__.

Declaring and validating case class constraints
-----------------------------------------------

In this section we show how to declare (see `“Declaring case class constraints” <#declaring-case-class-constraints>`__)
and validate (see `“Validating bean constraints” <#validating-case-class-constraints>`__) case class
constraints.

You will likely want to review Hibernate's `“Built-in constraints” <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-builtin-constraints>`__
documentation which provides an overview of all built-in constraints coming with the Hibernate
Validator and thus supported here.

If you are interested in applying constraints to method parameters and return values, refer to
`Declaring and validating method constraints <#declaring-and-validating-method-constraints>`__.

Declaring case class constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Constraints in Jakarta Bean Validation are expressed via Java annotations. In this section we show
how to enhance an object model with these annotations. There are three types of bean constraints:

* field constraints
* property constraints
* container element constraints
* class constraints

When defining a class field in Scala the compiler creates up to four accessors for it: a getter,
a setter, and if the field is annotated with `@BeanProperty`, a bean getter and a bean setter
[`reference <https://www.scala-lang.org/api/current/scala/annotation/meta/index.html>`__].

Thus, if you had the following class definition:

.. code-block:: scala

    class C(@myAnnot var c: Int)

There are **six** entities which can carry the `@myAnnot` annotation: the constructor param, the
generated field and the four accessors. **By default, annotations on constructor parameters end up
only on the constructor parameter and not on any other entity**. Annotations on fields, by default,
only end up on the field.

If you wanted Scala to copy the annotation to the generated field, you would need to apply the
`scala.annotation.meta.field` meta annotation to the annotation.

For example, using `class C` again from above:

.. code-block:: scala

    class C(@(myAnnot @field) var c: Int)

Thus to use the Hibernate Validator Java library directly from Scala, you would need to always
annotate constraint annotations with a `scala.annotation.meta` annotation along with translating
between Scala and Java collection types.

The benefit of `util-validator` is that the library will work with simply annotated case class
constructor parameters (i.e., without needing to add a `scala.annotation.meta` annotation) with an
API that uses and expresses Scala collection types.

Constructor param constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Constraints can be expressed by annotating the constructor param of a case class. E.g.,

.. code-block:: scala

    package com.example

    import jakarta.validation.constraints.{Min, NotEmpty, Size}

    case class Car(
      @NotEmpty manufacturer: String,
      @NotEmpty @Size(min = 2, max = 14) licensePlate: String,
      @Min(2) seatCount: Int)

.. important::

    When using constructor parameter constraints, field access strategy is used to access the value
    to be validated. This means the validation logic directly accesses the instance variable and
    will never invoke the generated property accessor method.

Constraints can be applied to fields of any access type (public, private etc.), including inherited
members. Neither constraints on companion object fields, nor secondary companion object `#apply`
method constraints are supported.

Secondary constructors
++++++++++++++++++++++

The validation library is not used for case class construction but because of how annotations are
carried by default in Scala for classes, the constructor of a case class plays an important role
in case class validation. By default, the library only tracks constraint annotations for constructor
parameters which are also declared fields within the class. Thus, if you have a primary constructor
of `Int` types with a secondary constructor of constraint-annotated `String` types (which does some
translation into the required `Int` types), the secondary constructor constraints have no bearing on
validation.

Container element constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Note that due to a long-standing issue [`SCALA-9883 <https://github.com/scala/bug/issues/9883>`__]
which affects how to access Java annotations from the Scala compiler, that **container element**
**constraints are not supported**.

For examples of container element constraints, see the Hibernate `documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#container-element-constraints>`__.

Thus, take note that constraint annotations will apply only to the container and not the type(s) of
the container with the one caveat being a field of type `Option[_]` which is always unwrapped by
default and thus the annotation always pertains to the contained type.

With `Option[_]`
++++++++++++++++

When applying a constraint on the type argument of `Option[_]`, the `ScalaValidator` will
*automatically unwrap* the `Option[_]` type and validate the internal value. For example:

.. code-block:: bash
   :emphasize-lines: 21,22

   Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
   Type in expressions for evaluation. Or try :help.

   scala> import jakarta.validation.constraints._
   import jakarta.validation.constraints._

   scala> case class Car(@Min(1000) towingCapacity: Option[Int] = None)
   defined class Car

   scala> val car = Car(Some(100))
   car: Car = Car(Some(100))

   scala> import com.twitter.util.validation.ScalaValidator
   import com.twitter.util.validation.ScalaValidator

   scala> val validator = ScalaValidator()
   Mar 30, 2021 11:58:47 AM org.hibernate.validator.internal.util.Version <clinit>
   INFO: HV000001: Hibernate Validator 7.0.1.Final
   validator: com.twitter.util.validation.ScalaValidator = com.twitter.util.validation.ScalaValidator@574f9e36

   scala> val violations = validator.validate(car)
   violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must be greater than or equal to 1000', propertyPath=towingCapacity, rootBeanClass=class Car, messageTemplate='{jakarta.validation.constraints.Min.message}'})

   scala> val violation = violations.head
   violation: jakarta.validation.ConstraintViolation[Car] = ConstraintViolationImpl{interpolatedMessage='must be greater than or equal to 1000', propertyPath=towingCapacity, rootBeanClass=class Car, messageTemplate='{jakarta.validation.constraints.Min.message}'}

   scala> violation.getMessage
   res0: String = must be greater than or equal to 1000

   scala> violation.getPropertyPath.toString
   res1: String = towingCapacity

   scala>

.. note::

   The property path only contains the name of the property, in this way, `Options` are treated
   as a "transparent" container.

Class-level constraints
^^^^^^^^^^^^^^^^^^^^^^^

A constraint can also be placed on the class level. In this case not a single property is subject
of the validation but the complete object. Class-level constraints are useful if the validation
depends on a correlation between several properties of an object.

For instance, if we had a `Car` case class defined with two field `seatCount` and `passengers` and
we want to ensure that the list of `passengers` does not have more entries than available seats.
For that purpose the `@ValidPassengerCount` constraint is added on the class level. The validator of
that constraint has access to the complete `Car` object, allowing to compare the numbers of seats
and passengers.

.. code-block:: scala
   :emphasize-lines: 5

    package com.example

    import jakarta.validation.constraints.Min

    @ValidPassengerCount
    case class Car(
      @Min(2) seatCount: Int,
      passengers: Seq[Person])

See the corresponding section in `Creating custom constraints - Class-level constraints <#>`__ for
documentation on how to implement such a custom constraint.

Constraint inheritance
^^^^^^^^^^^^^^^^^^^^^^

When a class implements a trait or extends an abstract class, all constraint annotations declared
on the super-type apply in the same manner as the constraints specified in the class itself.
For example, if we had a trait, `Car` with a `RentalCar` implementation:

.. code-block:: scala

    trait Car {
      @NotEmpty def manufacturer: String
    }

    case class RentalCar(manufacturer: String, @NotEmpty rentalStation: String) extends Car

Here the case class `RentalCar` implements the `Car` trait and adds the property `rentalStation`. If
an instance of `RentalCar` is validated, not only the `@NotEmpty` constraint on `rentalStation` is
evaluated, but also the constraint on `manufacturer` from the `Car` trait.

.. code-block:: bash
   :emphasize-lines: 26,27,41,42,56,57,70,71

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import jakarta.validation.constraints._
    import jakarta.validation.constraints._

    scala> trait Car {
         |   @NotEmpty def manufacturer: String
         | }
    defined trait Car

    scala> case class RentalCar(manufacturer: String, @NotEmpty rentalStation: String) extends Car
    defined class RentalCar

    scala> import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.ScalaValidator

    scala> val validator = ScalaValidator()
    Mar 30, 2021 12:21:41 PM org.hibernate.validator.internal.util.Version <clinit>
    INFO: HV000001: Hibernate Validator 7.0.1.Final
    validator: com.twitter.util.validation.ScalaValidator = com.twitter.util.validation.ScalaValidator@64e89bb2

    scala> val rental = RentalCar("", "Hertz")
    rental: RentalCar = RentalCar(,Hertz)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=manufacturer, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.size
    res0: Int = 1

    scala> violations.head.getPropertyPath.toString
    res: String = manufacturer

    scala> violations.head.getMessage
    res: String = must not be empty

    scala> val rental = RentalCar("Renault", "")
    rental: RentalCar = RentalCar(Renault,)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=rentalStation, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.size
    res: Int = 1

    scala> violations.head.getPropertyPath.toString
    res: String = rentalStation

    scala> violations.head.getMessage
    res: String = must not be empty

    scala> val rental = RentalCar("", "")
    rental: RentalCar = RentalCar(,)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=manufacturer, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'}, ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=rentalStation, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.size
    res: Int = 2

    scala> violations.map(v => s"${v.getPropertyPath}: ${v.getMessage}").mkString("\n")
    res: String =
    manufacturer: must not be empty
    rentalStation: must not be empty

    scala> val rental = RentalCar("Renault", "Hertz")
    rental: RentalCar = RentalCar(Renault,Hertz)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set()

    scala>

The same would be true, if `Car` was not a trait but an abstract class implemented by `RentalCar`.

**Constraint annotations are aggregated if members are overridden**. So if the `RentalCar` case class
also specifies any constraints on the `manufacturer` field, it would be evaluated in addition to the
`@NotEmpty` constraint from the `Car` trait:

.. code-block:: bash
   :emphasize-lines: 7,8,22,23

    scala> case class RentalCar(@Size(min = 2, max = 14) manufacturer: String, @NotEmpty rentalStation: String) extends Car
    defined class RentalCar

    scala> val rental = RentalCar("A", "Hertz")
    rental: RentalCar = RentalCar(A,Hertz)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='size must be between 2 and 14', propertyPath=manufacturer, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.Size.message}'})

    scala> violations.size
    res: Int = 1

    scala> violations.head.getPropertyPath.toString
    res: String = manufacturer

    scala> violations.head.getMessage
    res: String = size must be between 2 and 14

    scala> val rental = RentalCar("", "Hertz")
    rental: RentalCar = RentalCar(,Hertz)

    scala> val violations = validator.validate(rental)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='size must be between 2 and 14', propertyPath=manufacturer, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.Size.message}'}, ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=manufacturer, rootBeanClass=class RentalCar, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.size
    res: Int = 2

    scala> violations.map(v => s"${v.getPropertyPath}: ${v.getMessage}").mkString("\n")
    res: String =
    manufacturer: size must be between 2 and 14
    manufacturer: must not be empty

    scala>

Object graphs
^^^^^^^^^^^^^

The Jakarta Bean Validation API does not only allow to validate single case class instances but also
complete object graphs via cascaded validation. To do so, just annotate a field or property
representing a reference to another case class with `@Valid`.

.. code-block:: scala

    case class Person(@NotEmpty name: String)

    case class Car(@NotEmpty manufacturer: String, @Valid driver: Person)

If an instance of `Car` is validated, the referenced `Person` case class will also be validated, as
the `driver` field is annotated with `@Valid`. Therefore the validation of a `Car` will fail if the
`name` field of the referenced Person instance is empty.

The validation of object graphs is recursive, i.e. if a reference marked for cascaded validation
points to an object which itself has properties annotated with `@Valid`, these references will be
followed up by the validation logic as well. The validation logic will ensure that no infinite
loops occur during cascaded validation, for example if two objects hold references to each other.

Note that `null` or `None` values are ignored during cascaded validation.

`Iterable[_]`
+++++++++++++

Unlike constraints, object graph validation works for some container elements (those which are
an `Iterable[_]`). That means the field of the container can be annotated with `@Valid`, which will
cause each contained element to be validated when the parent object is validated.

.. code-block:: scala

    case class Person(@NotEmpty name: String)

    case class Car(@NotEmpty manufacturer: String, @Valid drivers: Seq[Person])

If an instance of `Car` is validated, the referenced sequence of `Person` case classes will also
be validated, as the `drivers` field is annotated with `@Valid`. Therefore the validation of a `Car`
will fail if the `name` field of the referenced Person instance is empty.

.. code-block:: bash
   :emphasize-lines: 27,28,39,40

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import jakarta.validation.constraints._
    import jakarta.validation.constraints._

    scala> case class Person(@NotEmpty name: String)
    defined class Person

    scala> import jakarta.validation.Valid
    import jakarta.validation.Valid

    scala> case class Car(@NotEmpty manufacturer: String, @Valid drivers: Seq[Person])
    defined class Car

    scala> val car = Car("Renault", Seq(Person("")))
    car: Car = Car(Renault,List(Person()))

    scala> import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.ScalaValidator

    scala> val validator = ScalaValidator()
    Mar 30, 2021 3:59:53 PM org.hibernate.validator.internal.util.Version <clinit>
    INFO: HV000001: Hibernate Validator 7.0.1.Final
    validator: com.twitter.util.validation.ScalaValidator = com.twitter.util.validation.ScalaValidator@6dcc7696

    scala> val violations = validator.validate(car)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=drivers[0].name, rootBeanClass=class Car, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.head.getPropertyPath.toString
    res: String = drivers[0].name

    scala> violations.head.getMessage
    res: String = must not be empty

    scala> val car = Car("Renault", Seq(Person("Lupin"), Person("")))
    car: Car = Car(Renault,List(Person(Lupin), Person()))

    scala> val violations = validator.validate(car)
    violations: Set[jakarta.validation.ConstraintViolation[Car]] = Set(ConstraintViolationImpl{interpolatedMessage='must not be empty', propertyPath=drivers[1].name, rootBeanClass=class Car, messageTemplate='{jakarta.validation.constraints.NotEmpty.message}'})

    scala> violations.head.getPropertyPath.toString
    res: String = drivers[1].name

    scala> violations.head.getMessage
    res: String = must not be empty

    scala>

Validating case class constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The `ScalaValidator` does not directly implement the Jakarta Bean Validation interface but provides
the same methods (and a few more) with Scala collection types. Note, however, since the
`ScalaValidator` is a wrapper that the underlying Jakarta Bean Validation is available for direct
access for Java users or to use functionality not exposed in the `ScalaValidator`. Also note that
this is a Java library and bypassing the `ScalaValidator` methods will thus not support all Scala
types.

This section shows how to obtain a configured `ScalaValidator` instance and then will walk through
the different methods of the `ScalaValidator`.

Obtaining a Validator instance
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The `ScalaValidator` has an `apply()` function which will return an instance of a `ScalaValidator`
configured with defaults.

.. code-block:: scala

    val validator: ScalaValidator = ScalaValidator()

To apply custom configuration to create a `ScalaValidator`, there is a builder for constructing a
customized validator.

E.g., to configure a custom size for the descriptor cache:

.. code-block:: scala
   :emphasize-lines: 3

    val validator: ScalaValidator =
      ScalaValidator.builder
        .withDescriptorCacheSize(1024)
        .validator

Or to add custom validations:

.. code-block:: scala
   :emphasize-lines: 10

    import com.twitter.util.validation.cfg.ConstraintMapping

    val mapping = ConstraintMapping(
      annotationType = classOf[FooConstraintAnnotation],
      constraintValidator = classOf[FooConstraintValidator]
    )

    val validator: ScalaValidator =
      ScalaValidator.builder
        .withConstraintMapping(mapping)
        .validator

or to specify multiple constraint mappings:

.. code-block:: scala
   :emphasize-lines: 17

    import com.twitter.util.validation.cfg.ConstraintMapping

    val mappings: Set[ConstraintMapping] =
      Set(
        ConstraintMapping(
          annotationType = classOf[FooConstraintAnnotation],
          constraintValidator = classOf[FooConstraintValidator]
        ),
        ConstraintMapping(
          annotationType = classOf[BarConstraintAnnotation],
          constraintValidator = classOf[BarConstraintValidator]
        )
      )

    val validator: ScalaValidator =
      ScalaValidator.builder
        .withConstraintMappings(mappings)
        .validator

You are **not required** to add a constraint mapping if the constraint annotation specifies a
`validatedBy()` class.

.. important::

    Attempting to add multiple mappings for the same annotation will result in a `ValidationException <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ValidationException.html>`__.

ScalaValidator API
^^^^^^^^^^^^^^^^^^

The `ScalaValidator` contains methods that can be used to either validate entire entities or just
single properties of the entity. All methods but `ScalaValidator#verify` return a
`Set[ConstraintViolation[_]]`. The set is empty if the validation succeeds. Otherwise, a
`ConstraintViolation` instance is added for each violated constraint. In the case of
`ScalaValidator#verify`, when the returned `Set[ConstraintViolation[_]]` is non-empty a
`ConstraintViolationException <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintViolationException.html>`__
is thrown which wraps the `Set[ConstraintViolation[_]]`.

All the validation methods have a version which takes a `groups: Seq[Class[_]]` parameter that is
used to specify validation groups to be considered when performing the validation. If the parameter
is empty, the default validation group (`jakarta.validation.groups.Default`) is used.

The topic of validation groups is discussed in detail in Hibernate's `Chapter 5, Grouping constraints <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#chapter-groups>`__.

`ScalaValidator#validate`
+++++++++++++++++++++++++

Use `validate` to perform validation of all constraints of a given case class.

.. code-block:: scala
   :emphasize-lines: 8

    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{AssertTrue, NotEmpty}

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean)

    val car = Car("", false)
    
    val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
    assert(violations.size == 2)
    assert(violations.head.getMessage == "must not be empty")
    assert(violations.last.getMessage == "not true")

Using the given definition of a `Car`, the above example shows an instance which fails to satisfy
the `@NotEmpty` constraint on the `manufacturer` property. The validation call therefore returns
one `ConstraintViolation[Car]` object.

`ScalaValidator#verify`
+++++++++++++++++++++++

The `verify` method is the same as `validate` but instead of returning `Set[ConstraintViolation[T]]`
the method throws a `ConstraintViolationException <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintViolationException.html>`__.

.. code-block:: scala
   :emphasize-lines: 9

    import com.twitter.util.Try
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{AssertTrue, NotEmpty}

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean)

    val car = Car("", false)
    
    val result = Try(validator.verify(car))
    assert(result.isThrow)

`ScalaValidator#validateValue`
++++++++++++++++++++++++++++++

By using the `validateValue` method you can check whether a single field of a given case class can
be validated  successfully if the field had the given value:

.. code-block:: scala
   :emphasize-lines: 7,8,9,10

    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{AssertTrue, NotEmpty}

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean)

    val violations: Set[ConstraintViolation[Car]] = 
      validator.validateValue(
        classOf[Car],
        "manufacturer",
        "")
    assert(violations.size == 1)
    assert(violations.head.getMessage == "must not be empty")

There is also a version of the method which instead of accepting a `Class[T]`, takes a
`CaseClassDescriptor[T]` of a given class.

.. code-block:: scala
   :emphasize-lines: 6,9,10,11,12

    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{AssertTrue, NotEmpty}

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean)

    val carDescriptor: CaseClassDescriptor[Car] = validator.getConstraintsForClass(classOf[Car]) 

    val violations: Set[ConstraintViolation[Car]] = 
      validator.validateValue(
        carDescriptor,
        "manufacturer",
        "")
    assert(violations.size == 1)
    assert(violations.head.getMessage == "must not be empty")

`ScalaValidator#validateProperty`
+++++++++++++++++++++++++++++++++

With help of the `validateProperty` you can validate a single named field of a given case class
instance.

.. code :: scala
   :emphasize-lines: 9,10,11

   import jakarta.validation.ConstraintViolation
   import jakarta.validation.constraints.{AssertTrue, NotEmpty}

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean)

    val car = Car("", false) // isRegistered is false here which would normally fail `@AssertTrue` validation

    val violations: Set[ConstraintViolation[Car]] = 
      validator.validateProperty(
        car,
        "manufacturer")
    assert(violations.size == 1)
    assert(violations.head.getMessage == "must not be empty")

.. note::

    The difference between `validateValue` and `validateProperty` is that the former takes a class
    type, a field in the class, and a presumed value in order to perform validation. Thus, no actual
    instance of the class is required for performing validation.

    Whereas the latter performs validation of a given field on an instance of a case class.

.. important::

    The `@Valid` annotation for cascading validation is not honored by `validateValue` or
    `validateProperty`.

ConstraintViolation
^^^^^^^^^^^^^^^^^^^

As mentioned, all methods but `ScalaValidator#verify` return a `Set[ConstraintViolation[_]]`. The
`ConstraintViolation <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintViolation.html>`__
contains information about the cause and location of the validation failure.

See the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-constraint-violation-methods>`__ for details.

Built-in constraints
^^^^^^^^^^^^^^^^^^^^

In addition to the constraints defined by the `Jakarta Bean Validation API and the Hibernate Built-in constraints <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-builtin-constraints>`__
we provide several useful custom constraints which are listed below. These constraints all apply to
the field/property level.

* `@CountryCode`

  Checks that the annotated field represents a valid `ISO 3166 <https://www.iso.org/iso-3166-country-codes.html>`__ country code or a collection of `ISO 3166 <https://www.iso.org/iso-3166-country-codes.html>`__ country codes.

  - **Supported data types**

    `String`, `Array[String]`, `Iterable[String]`

* `@OneOf(value=)`

  Checks that the annotated field represents a value or a set of values from the given array of values. Note that the `#toString` representations are checked for equality.

  - **Supported data types**

    `Any`, `Array[Any]`, `Iterable[Any]`

* `@UUID`

  Checks if the annotated field is a valid `String` representation of a `java.util.UUID <https://docs.oracle.com/javase/8/docs/api/java/util/UUID.html>`__.

  - **Supported data types**

    `String`

Declaring and validating method constraints
-------------------------------------------

Constraints can not only be applied to case classes and their fields, but also to the parameters and
return values of methods within the case class. Following the Jakarta Bean Validation specification,
constraints can be used to specify

* the preconditions that must be satisfied by the caller before a method or constructor may be invoked (by applying constraints to the parameters of an executable)
* the postconditions that are guaranteed to the caller after a method or constructor invocation returns (by applying constraints to the return value of an executable)

.. important::

    In all cases **except when using** `@MethodValidation`, declaring method or constructor
    constraints itself does not automatically cause their validation upon validation of a case class
    instance. Instead, the `ScalaExecutableValidator` API must be used to perform the validation.

Declaring method constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

`@MethodValidation` constraint
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The `@MethodValidation` constraint annotation is a special method validation constraint provided by
the library.

Unlike other executable specific constraints, `@MethodValidation`-annotated methods **will be**
**validated along with all field-level and class-level validations** when calling
`ScalaValidate#validate <#id1>`__ or `ScalaValidator#verify <#id2>`__. Note, that the method
validation is strictly performed *after* all field level validations (and *before* class-level
validations). Annotated methods MUST have no specified parameters and MUST return a
`MethodValidationResult`.

To manually validate a given method or methods, you can use `ScalaExecutableValidator#validateMethod <#scalaexecutablevalidator-validatemethod>`__
or `ScalaExecutableValidator#validateMethods <#scalaexecutablevalidator-validatemethods>`__.

You can find details in `“Creating custom constraints - @MethodValidation constraints” <#methodvalidation-constraints>`__
which shows how to implement a `@MethodValidation` constraint.

Method Parameter constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can specify the preconditions of a method by adding constraint annotations to its parameters:

.. code-block:: scala

    import jakarta.validation.constraints.{Min, NotEmpty, NotNull}
    import java.time.LocalDate

    case class RentalStation(@NotEmpty name: String) {

      def rentCar(
        @NotNull customer: Customer,
        @NotNull @Future startDate: LocalDate,
        @Min(1) durationInDays: Int
      ): Unit = ???
    }

The following preconditions are specified here:

* The `name` passed to the `RentalCar` constructor must not be empty.
* When invoking the `rentCar()` method, the given customer must not be null, the rental’s start date must not be `null` as well as be in the future and finally the rental duration must be at least one day.

.. note::

    Constraints may only be applied to instance methods, i.e. declaring constraints on static
    methods is not supported.

Cross-parameter constraints
+++++++++++++++++++++++++++

Sometimes validation does not only depend on a single parameter but on several or even all
parameters of a method. This kind of requirement can be fulfilled with help of a cross-parameter
constraint.

Cross-parameter constraints can be considered as the method validation equivalent to class-level
constraints. Both can be used to implement validation requirements which are based on several
elements. While class-level constraints can apply to several fields of a case class, cross-parameter
constraints can apply to several parameters of a method.

In contrast to single-parameter constraints, cross-parameter constraints are declared on the method.
In the example below, the cross-parameter constraint `@LuggageCountMatchesPassengerCount` declared
on the `load()` method is used to ensure that no passenger has more than two pieces of luggage.

.. code-block:: scala

    case class Car(@NotEmpty manufacturer: String, @AssertTrue isRegistered: Boolean) {

      @LuggageCountMatchesPassengerCount(piecesOfLuggagePerPassenger = 2)
      def load(passengers: Seq[Person], luggage: Seq[PieceOfLuggage]): Unit = ???
    }

We'll see in the next section that return value constraints are also declared on the method level.
Thus, in order to distinguish cross-parameter constraints from return value constraints, the
constraint target is configured in the `ConstraintValidator` implementation using the `@SupportedValidationTarget <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/constraintvalidation/SupportedValidationTarget.html>`__
annotation.

More details in `“Creating custom constraints - Cross-parameter constraints” <#id4>`__ which shows
how to implement a cross-parameter constraint.

Method Return value constraints
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The postconditions of a method or constructor are declared by adding constraint annotations to the
executable:

.. code-block:: scala

    case class RentalStation @ValidRentalStation(@NotEmpty name: String) {

      @NotEmpty
      @Size(min = 1)
      def getCustomers: Seq[Customer] = ???
    }

In the example above, the following constraints apply to executables (constructor and a method in
this case) of `RentalStation`:
* A new `RentalStation` instance must satisfy the `@ValidRentalStation` constraint
* The `Seq[Customer]` returned by `getCustomers` must not be empty and must contain at least on element

Cascaded Validation
^^^^^^^^^^^^^^^^^^^

Like the cascaded validation of case class fields (see `“Object graphs” <#object-graphs>`__), the
`@Valid` annotation can be used to mark executable parameters and return values for cascaded
validation. When validating a parameter or return value annotated with `@Valid`, the constraints
declared on the parameter or return value object are also validated.

.. code-block:: scala

    case class Car(@NotEmpty manufacturer: String, @NotEmpty @Size(min = 2, max = 14) licensePlate: String)

    case class Garage @Valid (@NotEmpty name: String) {

      def checkCar(@NotNull @Valid car: Car): Boolean = ???
    }

Above, the `car` parameter of the method `Garage#checkCar` as well as the return value of the
`Garage` constructor are marked for cascaded validation.

When validating the arguments of the `checkCar()` method, the constraints on the properties of the
passed `Car` object are evaluated as well. Similarly, the `@NotEmpty` constraint on the name field
of `Garage` is checked when validating the return value of the `Garage` constructor.

Generally, the cascaded validation works for executables in the same way as it does for case class
fields.

In particular, `null` or `None` values are ignored during cascaded validation (which is impossible
for constructor return values) and cascaded validation is performed recursively, i.e. if a parameter
or return value object which is marked for cascaded validation itself has properties marked with
`@Valid`, the constraints declared on the referenced elements will be validated as well.

Also, the same issues with annotating container elements apply because of the Scala compiler
limitations but also note that more generally, the majority of executable methods proxy to the
underlying Hibernate Java library.

Method constraints in inheritance hierarchies
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Note there are some rules for method constraints and inheritance as outlined in the
`Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-method-constraints-inheritance-hierarchies>`__.

Validating method constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The validation of method constraints is done using the `ScalaExecutableValidator` interface.

Obtaining a `ScalaExecutableValidator` instance
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The `ScalaValidator` provides a `ScalaExecutableValidator` which implements a set of "executable"
methods that mirrors the bean validation `ExecutableValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/executable/ExecutableValidator.html>`__ API.

All of the corollary `ExecutableValidator` methods proxy directly to the underlying Hibernate
Validator for support since it properly handles annotations in these cases and no special logic is
needed.

The `ScalaExecutableValidator` can be obtained from the `ScalaValidator` by calling
`ScalaValidator#forExecutables`

.. code-block:: scala

    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator

    val validator: ScalaValidator = ScalaValidator()
    val executableValidator: ScalaExecutableValidator = validator.forExecutables

`ScalaExecutableValidator` methods
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

The `ScalaExecutableValidator` interface offers altogether six methods:

* `validateMethods()`, `validateMethod()`, `validateParameters()`, and `validateReturnValue()` for method validation
* `validateConstructorParameters()` and `validateConstructorReturnValue()` for constructor validation

`ScalaExecutableValidator#validateMethods`
++++++++++++++++++++++++++++++++++++++++++

Validates all `@MethodValidation`-annotated methods of a given object.

.. code-block:: scala
   :emphasize-lines: 27

    import com.twitter.util.validation.MethodValidation
    import com.twitter.util.validation.engine.MethodValidationResult
    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{Min, NotEmpty}
    import java.time.LocalDate

    case class RentalCar(
      @NotEmpty make: String,
      @NotEmpty model: String,
      @Min(2000) modelYear: Int) {

      @MethodValidation(fields = Array("modelYear"))
      def onlyNewerCars: MethodValidationResult = {
        // only want model years within the last 2 years
        val year: Int = LocalDate.now.getYear
        if ((year - modelYear) <= 2) MethodValidationResult.Valid()
        else MethodValidationResult.Invalid("model year must be within the last 2 years")
      }
   }

   val rental = RentalCar("Renault", "Ellypse", 2002)

   val validator: ScalaValidator = ScalaValidator()
   val executableValidator: ScalaExecutableValidator = validator.forExecutables

   val violations: Set[ConstraintViolation[RentalCar]] = executableValidator.validateMethods(rental)
   assert(violations.size == 1)
   assert(violations.head.getMessage == "model year must be within the last 2 years")
   assert(violations.head.getPropertyPath.toString == "onlyNewerCars.modelYear")
   assert(violations.head.getInvalidValue == rental)

`ScalaExecutableValidator#validateMethod`
+++++++++++++++++++++++++++++++++++++++++

Validates the given `@MethodValidation`-annotated method of an object.

.. code-block:: scala
   :emphasize-lines: 30

    import com.twitter.util.validation.MethodValidation
    import com.twitter.util.validation.engine.MethodValidationResult
    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{Min, NotEmpty}
    import java.lang.reflect.Method
    import java.time.LocalDate

    case class RentalCar(
      @NotEmpty make: String,
      @NotEmpty model: String,
      @Min(2000) modelYear: Int) {

      @MethodValidation(fields = Array("modelYear"))
      def onlyNewerCars: MethodValidationResult = {
        // only want model years within the last 2 years
        val year: Int = LocalDate.now.getYear
        if ((year - modelYear) <= 2) MethodValidationResult.Valid()
        else MethodValidationResult.Invalid("model year must be within the last 2 years")
      }
   }

   val rental = RentalCar("Renault", "Nepta", 2006)

   val validator: ScalaValidator = ScalaValidator()
   val executableValidator: ScalaExecutableValidator = validator.forExecutables

   val method = classOf[RentalCar].getDeclaredMethod("onlyNewerCars")

   val violations: Set[ConstraintViolation[RentalCar]] = executableValidator.validateMethod(rental, method)
   assert(violations.size == 1)
   assert(violations.head.getMessage == "model year must be within the last 2 years")
   assert(violations.head.getPropertyPath.toString == "onlyNewerCars.modelYear")
   assert(violations.head.getInvalidValue == rental)

`ScalaExecutableValidator#validateParameters`
+++++++++++++++++++++++++++++++++++++++++++++

Validates all constraints placed on the parameters of the given method. This method directly proxies
to the `underlying Hibernate validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_code_executablevalidator_validateparameters_code>`__.

.. code-block:: scala
   :emphasize-lines: 32,33,34,35

    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{Min, NotEmpty, NotNull, Size}
    import java.time.LocalDate

    case class RentalStation(@NotNull name: String) {
      def rentCar(
        @NotNull customer: Customer,
        @NotNull @Future start: LocalDate,
        @Min(1) duration: Int
      ): Unit = ???

      @NotEmpty
      @Size(min = 1)
      def getCustomers: Seq[Customer] = Seq.empty
    }

    val rentalStation = RentalStation("Hertz")

    val validator: ScalaValidator = ScalaValidator()
    val executableValidator: ScalaExecutableValidator = validator.forExecutables

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
        Array(customer, LocalDate.now(), Integer.valueOf(5)))
    assert(violations.size == 1)
    assert(violations.head.getMessage == "must be a future date")
    assert(violations.head.getPropertyPath.toString == "rentCar.start")

`ScalaExecutableValidator#validateReturnValue`
++++++++++++++++++++++++++++++++++++++++++++++

Validates return value constraints of the given method. This method directly proxies to the
`underlying Hibernate validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_code_executablevalidator_validatereturnvalue_code>`__.

.. code-block:: scala
   :emphasize-lines: 27,28,29,30

    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{Min, NotEmpty, NotNull, Size}
    import java.time.LocalDate

    case class RentalStation(@NotNull name: String) {
      def rentCar(
        @NotNull customer: Customer,
        @NotNull @Future start: LocalDate,
        @Min(1) duration: Int
      ): Unit = ???

      @NotEmpty
      @Size(min = 1)
      def getCustomers: Seq[Customer] = Seq.empty
    }

    val rentalStation = RentalStation("Hertz")

    val validator: ScalaValidator = ScalaValidator()
    val executableValidator: ScalaExecutableValidator = validator.forExecutables

    val method = classOf[RentalStation].getMethod("getCustomers")

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateReturnValue(
        rentalStation,
        method,
        Seq.empty[Customer])
    assert(violations.size == 2)
    assert(violations.head.getMessage == "must not be empty")
    assert(violations.head.getPropertyPath.toString == "getCustomers.<return value>")
    assert(violations.last.getMessage == "size must be between 1 and 2147483647")
    assert(violations.last.getPropertyPath.toString == "getCustomers.<return value>")

`ScalaExecutableValidator#validateConstructorParameters`
++++++++++++++++++++++++++++++++++++++++++++++++++++++++

Validates all constraints placed on the parameters of the given constructor. This method directly
proxies to the `underlying Hibernate validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_code_executablevalidator_validateconstructorparameters_code>`__.

.. code-block:: scala
   :emphasize-lines: 27,28,29

    package com.example

    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation
    import jakarta.validation.constraints.{Min, NotEmpty, NotNull, Size}
    import java.time.LocalDate

    case class RentalStation(@NotNull name: String) {
      def rentCar(
        @NotNull customer: Customer,
        @NotNull @Future start: LocalDate,
        @Min(1) duration: Int
      ): Unit = ???

      @NotEmpty
      @Size(min = 1)
      def getCustomers: Seq[Customer] = Seq.empty
    }

    val validator: ScalaValidator = ScalaValidator()
    val executableValidator: ScalaExecutableValidator = validator.forExecutables

    val constructor = classOf[RentalStation].getConstructor(classOf[String])

    val violations: Set[ConstraintViolation[RentalStation]] =
      executableValidator.validateConstructorParameters(
        constructor,
        Array(null))
    assert(violations.size == 1)
    assert(violations.head.getMessage == "must not be null")
    assert(violations.head.getPropertyPath.toString == "RentalStation.name")

`ScalaExecutableValidator#validateConstructorReturnValue`
+++++++++++++++++++++++++++++++++++++++++++++++++++++++++

Validates a constructor's return value. This method directly proxies to the
`underlying Hibernate validator <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_code_executablevalidator_validateconstructorreturnvalue_code>`__.

.. code-block:: scala
   :emphasize-lines: 25,26,27

    package com.example

    import com.twitter.util.validation.ScalaValidator
    import com.twitter.util.validation.executable.ScalaExecutableValidator
    import jakarta.validation.ConstraintViolation

    case class CarWithPassengerCount @ValidPassengerCountReturnValue(max = 1) (passengers: Seq[Person])

    val car = CarWithPassengerCount(
      Seq(
        Person("abcd1234", "J Doe", DefaultAddress),
        Person("abcd1235", "K Doe", DefaultAddress),
        Person("abcd1236", "L Doe", DefaultAddress),
        Person("abcd1237", "M Doe", DefaultAddress),
        Person("abcd1238", "N Doe", DefaultAddress)
      )
    )

    val validator: ScalaValidator = ScalaValidator()
    val executableValidator: ScalaExecutableValidator = validator.forExecutables

    val constructor = classOf[CarWithPassengerCount].getConstructor(classOf[Seq[Person]])

    val violations: Set[ConstraintViolation[CarWithPassengerCount]] =
      executableValidator.validateConstructorReturnValue(
        constructor,
        car)
    assert(violations.size == 1)
    assert(violations.head.getMessage == "invalid number of passengers")
    assert(violations.head.getPropertyPath.toString == "CarWithPassengerCount.<return value>")

Built-in method constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~

In addition to the built-in case class and field-level constraints discussed in
`“Built-in constraints” <#built-in-constraints>`__, this library provides one method-level
constraint, `@MethodValidation`. This is a generic constraint which allows for implementation of
validation of the (possibly even internal) state of a case class instance.

This is different from a class-level constraint since being implemented as a method within the case
class means that the method implementation potentially has access to private or internal state for
performing validation whereas a class-level constraint only has access to publicly exposed fields
and methods.

* `@MethodValidation`

  Used to validate the (possibly internal) state of an entire object. It is important to note that any
  annotated method **will be invoked when performing validation** and should thus be side-effect free.

  - **Supported data types**

    No-arg case class method that returns a `MethodValidationResult`.

Grouping constraints
--------------------

See the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#chapter-groups>`__
which explains constraint grouping in detail.

Creating custom constraints
---------------------------

In cases where none of the `built-in constraints <#built-in-constraints>`__ suffice, you can create
custom constraints to implement your specific validation requirements.

Creating a simple constraint
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To create a custom constraint, the following three steps are required:

* Create a constraint annotation
* Implement a `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__
* Define a default error message

The constraint annotation
^^^^^^^^^^^^^^^^^^^^^^^^^

This is detailed in the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#validator-customconstraints-constraintannotation>`__
and all of the steps here are the same for use with this library. The linked documentation creates a
`CheckCase` annotation with two modes for validating a `String` is either uppercase or lowercase.
We'll assume the `CaseMode` enum and the `CheckCase` constraint annotation exists.

The constraint validator
^^^^^^^^^^^^^^^^^^^^^^^^

Having defined the annotation, we now need to create an implementation of a `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__,
which is able to validate fields with a `@CheckCase` annotation. To do so, we implement the Jakarta Bean Validation interface `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__:

.. code-block:: scala

    package com.example.constraint

    import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

    class CheckCaseConstraintValidator extends ConstraintValidator[CheckCase, String] {
      @volatile private[this] var caseMode: CaseMode = _

      override def initialize(constraintAnnotation: CheckCase): Unit = {
        this.caseMode = constraintAnnotation.value()
      }

      def isValid(
        obj: String,
        constraintContext: ConstraintValidatorContext): Boolean = {
        if (obj == null) true
        else {
          caseMode match {
            case UPPER =>
              obj == obj.toUpperCase
            case LOWER =>
              obj == obj.toLowerCase
          }
        }
      }
    }

The `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__
interface defines two type parameters which are set in the implementation. The first one specifies
the annotation type to be validated (in this case `CheckCase`), the second one the type of elements
the validator can handle (in this case, `String`).

In case a constraint supports several data types, you can create a `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__
implementation over the `Any` or `AnyRef` types and pattern-match within the implementation. If the
implementation receives a type that is unsupported you can throw an `UnexpectedTypeException <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/index.html?jakarta/validation/UnexpectedTypeException.html>`__.

The implementation of the validator is straightforward: the `initialize` method gives you access
to the attribute values of the validated constraint and allows you to store them in a field of the
validator as shown in the example.

The `isValid` method contains the validator logic. For `@CheckCase` this is the check as to whether
a given string is either completely lower case or upper case, depending on the `caseMode` set from
the constraint annotation value in `initialize`.

.. important::

    Note that the Jakarta Bean Validation specification recommends consideration of `null` values as
    being valid. If `null` is not a valid value for an element, it should be **explicitly** annotated
    with `@NotNull`.

    This library also extends the same consideration for `None` values during validation as
    detailed in `With Option[_] <#with-option>`__.

Thread-safety
+++++++++++++

As noted in the `javadocs <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html#isValid-T-jakarta.validation.ConstraintValidatorContext->`__:
access to `isValid` can happen concurrently and thus thread-safety must be ensured by the
implementation. Also note that `isValid` implementations **should not** alter the state of the value
to validate.

The error message
^^^^^^^^^^^^^^^^^

The last piece is an error message which should be used when a `@CheckCase` constraint is violated.
You have a few options. You can specify the default error message directly within the `message()`
attribute of the constraint annotation, e.g.,

.. code-block:: java

    String message() default "Case mode must be {value}";

However, if you want to be able to `internationalize <https://docs.oracle.com/javase/tutorial/i18n/intro/steps.html>`__
an error message, it is generally recommended to place the value in a `Resource Bundle <https://beanvalidation.org/2.0/spec/#constraintsdefinitionimplementation-constraintdefinition-properties-message>`__,
specifying the resource key as the `message()` attribute in the constraint annotation.

.. code-block:: java

    String message() default "{com.example.constraint.CheckCase.message}";

Create a `properties <https://en.wikipedia.org/wiki/.properties>`__ file,
*ValidationMessages.properties* with the following contents:

.. code-block:: text

    com.example.constraint.CheckCase.message=Case mode must be {value}

Make sure the *ValidationMessages.properties* is packaged on your classpath to be picked up by the
library.

For details, see the Hibernate documentation: `"Default message interpolation" <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-message-interpolation>`__.

The `TwitterConstraintValidatorContext`
+++++++++++++++++++++++++++++++++++++++

First, see the documentation about the `ConstraintValidatorContext <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_the_code_constraintvalidatorcontext_code>`__
for background on its usage within a `ConstraintValidator <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/ConstraintValidator.html>`__
implementation.

This library provides a utility for customizing any returned `ConstraintViolation` from a
`ConstraintValidator` by using the `c.t.util.validation.constraintvalidation.TwitterConstraintValidatorContext`.

The `TwitterConstraintValidatorContext` uses the `HibernateConstraintValidatorContext <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-hibernateconstraintvalidatorcontext>`__
described in the `Custom contexts <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#_custom_contexts>`__
Hibernate documentation. See the referenced documentation for details.

The `TwitterConstraintValidatorContext` has a simple DSL to create custom constraint violations. The
DSL allows you to set a dynamic `Payload <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/jakarta/validation/Payload.html>`__,
set message parameters or expression variables for an error message, or specify a fully custom error
message template (that is a message template different than the default specified by the constraint
annotation).

.. note::

    As mentioned in the Hibernate `documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-hibernateconstraintvalidatorcontext>`__:

    *[T]he main difference between message parameters and expression variables is that message parameters*
    *are simply interpolated whereas expression variables are interpreted using the* `Expression Language engine <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#el-features>`__.

    *In practice, use message parameters if you do not need the advanced features of an Expression Language.*

By default, Expression Language support **is not enabled** for custom violations created via the
`ConstraintValidatorContext`. However, for some advanced requirements, using Expression Language may
be necessary. If you add an expression variable via the `TwitterConstraintValidatorContext`,
Expression Language support will be enabled when adding a constraint violation.

.. code-block:: scala
   :emphasize-lines: 25,26,27,28,29

    import com.twitter.util.validation.constraintvalidation.TwitterConstraintValidatorContext
    import jakarta.validation.{ConstraintValidator, ConstraintValidatorContext}

    class CheckCaseConstraintValidator extends ConstraintValidator[CheckCase, String] {
      @volatile private[this] var caseMode: CaseMode = _

      override def initialize(constraintAnnotation: CheckCase): Unit = {
        this.caseMode = constraintAnnotation.value()
      }

      def isValid(
        obj: String,
        constraintContext: ConstraintValidatorContext): Boolean = {
        val valid = if (obj == null) true
        else {
          caseMode match {
            case UPPER =>
              obj == obj.toUpperCase
            case LOWER =>
              obj == obj.toLowerCase
          }
        }

        if (!valid) {
          TwitterConstraintValidatorContext
            .withDynamicPayload(???)
            .addMessageParameter("foo", "snafu")
            .withMessageTemplate("{foo}") // will be interpolated into the string 'snafu'
            .addConstraintViolation(constraintContext)
        }

        valid
      }
    }

Using the constraint
^^^^^^^^^^^^^^^^^^^^

To use the custom `@CheckCase` constraint, annotate a case class field:

.. code-block:: scala
   :emphasize-lines: 7

    package com.example

    import jakarta.validation.constraints.{Min, NotEmpty, Size}

    case class Car(
      @NotEmpty manufacturer: String,
      @NotEmpty @Size(min = 2, max = 14) @CheckCase(CaseMode.UPPER) licensePlate: String,
      @Min(2) seatCount: Int)

During validation, a `licensePlate` field with an invalid case will now cause validation to fail.

.. code-block:: scala

    // invalid license plate
    val car: Car = Car("Morris", "dd-ab-123", 4)

    val violations: Set[ConstraintViolation[Car]] = validator.validate(car)
    assert(violations.size == 1)
    assert(violations.head.getMessage == "Case mode must be UPPER")

    // valid license plate
    val car: Car = Car("Morris", "DD-AB-123", 4)
    assert(validator.validate(car).size == 0)

Class-level constraints
~~~~~~~~~~~~~~~~~~~~~~~

As mentioned previously, constraints can also be applied on the class-level to validate the state of
the entire case class instance. These constraints are defined in the same way as field constraints.

Here we'll see how to implement the `@ValidPassengerCount` annotation seen in an earlier example:

.. code-block:: scala
   :emphasize-lines: 6

    package com.example

    import com.example.constraint.ValidPassengerCount
    import jakarta.validation.constraints.Min

    @ValidPassengerCount
    case class Car(
      @Min(2) seatCount: Int,
      passengers: Seq[Person])

First the annotation definition could be written as:

.. code-block:: java

    package com.example.constraint;

    @Target({ TYPE, ANNOTATION_TYPE })
    @Retention(RUNTIME)
    @Constraint(validatedBy = { ValidPassengerCountValidator.class })
    @Documented
    public @interface ValidPassengerCount {

        String message() default "{com.example.constraint.ValidPassengerCount.message}";

        Class<?>[] groups() default { };

        Class<? extends Payload>[] payload() default { };
    }

We define an error message in a *Validation.properties* file:

.. code-block:: text

    com.example.constraint.ValidPassengerCount.message=invalid number of passengers

Then the `ConstraintValidator` implementation which is specified to a `Car` type:

.. code-block:: scala

    package com.example

    import com.example.constraint.ValidPassengerCount
    import jakarta.validation.{ConstraintValidator

    class ValidPassengerCountValidator extends ConstraintValidator[ValidPassengerCount, Car] {

      def isValid(
        car: Car,
        constraintContext: ConstraintValidatorContext
      ): Boolean =
        if (car == null) true
        else car.passengers.size <= car.seatCount
    }

Note as the example shows, you need to use the element type `TYPE` in the `@Target <https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Target.html>`__
annotation on the constraint annotation. This allows the constraint to be put on type definitions.
The `ValidPassengerCountValidator` receives an instance of a `Car` in the `isValid()` method and can
access the complete object state to decide whether the given instance is valid or not.

During validation, an instance which does not satisfy the constraint will fail the validation.

.. code-block:: scala

    package com.example

    import com.twitter.util.validation.ScalaValidator
    import jakarta.validation.ConstraintViolation

    val car = Car(
      seatCount = 2,
      passengers = Seq(
        Person("abcd1234", "J Doe", DefaultAddress),
        Person("abcd1235", "K Doe", DefaultAddress),
        Person("abcd1236", "L Doe", DefaultAddress),
        Person("abcd1237", "M Doe", DefaultAddress),
        Person("abcd1238", "N Doe", DefaultAddress)
      )
    )

    val validator: ScalaValidator = ScalaValidator()

    val violations: Set[ConstraintViolation[Car]] =
      validator.validate(car)
    assert(violations.size == 1)
    assert(violations.head.getMessage == "invalid number of passengers")
    assert(violations.head.getPropertyPath.toString == "Car")

Custom property paths
^^^^^^^^^^^^^^^^^^^^^

By default for a class-level constraint, any constraint violation is reported on the level of the
annotated type, e.g. in this case the `Car` type. If the class is the top-level, the property path
will be empty, otherwise it will be the field name referencing the class type.

See the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-custom-property-paths>`__
for details on how to set custom property paths when implementing a class-level constraint.

Cross-parameter constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Bean validation specification distinguishes two types of constraints: generic constraints which
apply to the annotated element (e.g., a type, field, container element, method parameter or return
value, etc.), and *cross-parameter* constraints. Cross-parameter constraints by contrast, apply to
an array of parameters of a method or constructor and can be used to express validation logic which
depends on several parameter values.

See the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-cross-parameter-constraints>`__
for details on implementing cross-parameter constraints.

Constraint composition
~~~~~~~~~~~~~~~~~~~~~~

To keep the annotation of case class fields from becoming unwieldy or confusing, you can use a
composing constraint. See the `Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#section-constraint-composition>`__
for details on creating a composing constraint.

`@MethodValidation` constraints
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. important::

    The `@MethodValidation` constraint annotation MUST be applied to a no-arg method that returns a
    `com.twitter.util.validation.engine.MethodValidationResult`.

    These annotated methods **will be invoked** during validation and should be side-effect free.

In a case class, define a no-arg method that returns a `MethodValidationResult`. Annotate the method
with `@MethodValidation`, optionally specifying validated fields of the case class for
error-reporting.

.. code-block:: scala
   :emphasize-lines: 11,12

    import com.twitter.util.validation.MethodValidation
    import com.twitter.util.validation.engine.MethodValidationResult
    import jakarta.validation.constraints.{Min, NotEmpty}
    import java.time.LocalDate

    case class RentalCar(
      @NotEmpty make: String,
      @NotEmpty model: String,
      @Min(2000) modelYear: Int) {

      @MethodValidation(fields = Array("modelYear"))
      def onlyNewerCars: MethodValidationResult = {
        // only want model years within the last 2 years
        val year: Int = LocalDate.now.getYear
        if ((year - modelYear) <= 2) MethodValidationResult.Valid()
        else MethodValidationResult.Invalid("model year must be within the last 2 years")
      }
   }

Value Extraction
----------------

Value extraction is the process of extracting values from a container so that they can be validated.

It is used when dealing with `container element constraints <#container-element-constraints>`__ and
`cascaded validation <#object-graphs>`__ inside containers.

Built-in value extractors
~~~~~~~~~~~~~~~~~~~~~~~~~

This library provides built-in value extraction for Scala `Iterable[_] <#iterable>`__ types. See the
`Hibernate documentation <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#chapter-valueextraction>`__
for more details on value extraction including how to define and register a new value extractor.

Best Practices
--------------

- Case classes used for validation should be side-effect free under property access. That is, accessing a
  case class field should be able to be done eagerly and without side-effects.
- Case class methods for `@MethodValidation` validation should be side-effect free under method access. That is, accessing a
  `@MethodValidation`-annotated method should be able to be done eagerly and without side-effects.
- Case classes with generic type params should be considered **not supported** for validation. E.g.,

  .. code-block:: scala

      case class GenericCaseClass[T](@NotEmpty @Valid data: T)

  This may appear to work for some category of data but in practice the way the library caches reflection data does not 
  discriminate on the type binding and thus validation of genericized case classes is not fully guaranteed to be successful for 
  differing type bindings of a given genericized class.
- While `Iterable` collections are supported for cascaded validation when annotated with `@Valid`, this does
  not include `Map` types. Annotated `Map` types will be ignored during validation.
- More generally, types with multiple type params, e.g. `Either[T, U]`, are not supported for validation
  of contents when annotated with `@Valid`. Annotated unsupported types will be **ignored** during validation.

More resources
--------------

* `Hibernate Validator Specifics <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/#validator-specifics>`__ (from reference guide)
* `Hibernate Validator Reference Guide <https://docs.jboss.org/hibernate/stable/validator/reference/en-US/html_single/>`__
* `Jakarta Bean Validation API 3.0.0 Javadocs <https://javadoc.io/static/jakarta.validation/jakarta.validation-api/3.0.0/index.html>`__
* `Hibernate Validator API Javadocs <https://docs.jboss.org/hibernate/stable/validator/api/index.html>`__
* `Jakarta Bean Validation <https://beanvalidation.org/>`__
