.. _util-jackson-index:

util-jackson Guide
==================

The library builds upon the excellent |jackson-module-scala|_ for `JSON <https://en.wikipedia.org/wiki/JSON>`_
support by wrapping the `Jackson ScalaObjectMapper <https://github.com/FasterXML/jackson-module-scala/blob/master/src/main/scala/com/fasterxml/jackson/module/scala/ScalaObjectMapper.scala>`__
to provide an API very similar to the `Jerkson <https://github.com/codahale/jerkson>`__ `Parser <https://github.com/codahale/jerkson/blob/master/src/main/scala/com/codahale/jerkson/Parser.scala>`__.

Additionally, the library provides a default improved `case class deserializer <#improved-case-class-deserializer>`_
which *accumulates* deserialization errors while parsing JSON into a `case class` instead of failing-fast,
such that all errors can be reported at once.

.. admonition :: 🚨 This documentation assumes some level of familiarity with `Jackson JSON Processing <https://github.com/FasterXML/jackson>`__

    Specifically Jackson `databinding <https://github.com/FasterXML/jackson-databind#1-minute-tutorial-pojos-to-json-and-back>`__
    and the Jackson `ObjectMapper <http://fasterxml.github.io/jackson-databind/javadoc/2.10/com/fasterxml/jackson/databind/ObjectMapper.html>`__.

    There are several `tutorials <https://github.com/FasterXML/jackson-docs#tutorials>`__ (and `external documentation <https://github.com/FasterXML/jackson-docs#external-off-github-documentation>`__)
    which may be useful if you are unfamiliar with Jackson.

    Additionally, you may want to familiarize yourself with the `Jackson Annotations <https://github.com/FasterXML/jackson-docs#annotations>`_
    as they allow for finer-grain customization of Jackson databinding.

Case Classes
------------

As mentioned, `Jackson <https://github.com/FasterXML/jackson>`__ is a JSON processing library. We
generally use Jackson for `databinding <https://www.tutorialspoint.com/jackson/jackson_data_binding.htm>`__,
or more specifically:

- object serialization: converting an object **into a JSON String** and
- object deserialization: converting a JSON String **into an object**

This library integration is primarily centered around serializing and deserializing Scala
`case classes <https://docs.scala-lang.org/tour/case-classes.html>`__. This is because Scala
`case classes <https://docs.scala-lang.org/tour/case-classes.html>`__ map well to the two JSON
structures [`reference <https://www.json.org/json-en.html>`__]:

- A collection of name/value pairs. Generally termed an *object*. Here, the name/value pairs are the case field name to field value but can also be an actual Scala `Map[T, U]` as well.
- An ordered list of values. Typically an *array*, *vector*, *list*, or *sequence*, which for case classes can be represented by a Scala `Iterable`.

Library Features
----------------

-  Usable as a replacement for the |jackson-module-scala|_ or `Jerkson <https://github.com/codahale/jerkson>`__ in many cases.
-  A `c.t.util.jackson.ScalaObjectMapper <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/ScalaObjectMapper.scala>`__ which provides additional Scala friendly methods not found in the |jackson-module-scala|_ `ScalaObjectMapper`.
-  A custom `case class deserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/CaseClassDeserializer.scala>`__ which overcomes some of the limitations in |jackson-module-scala|_.
-  Integration with the `util-validator <https://twitter.github.io/util/guide/util-validator/index.html>`__ Bean Validation 2.0 (`beanvalidation.org <https://beanvalidation.org/>`_) style validations during JSON deserialization.
-  Utility for quick JSON parsing with `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__
-  Utility for easily comparing JSON strings.

Basic Usage
-----------

Let's assume we have these two case classes:

.. code:: scala

    case class Bar(d: String)
    case class Foo(a: String, b: Int, c: Bar)

To **serialize** a case class into a JSON string, use

.. code:: scala

    ScalaObjectMapper#writeValueAsString(any: Any): String // or
    JSON#write(any: Any)

For example:

.. code:: scala
   :emphasize-lines: 10, 15, 30, 33, 38

    Welcome to Scala 2.12.12 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> val foo = Foo("Hello, World", 42, Bar("Goodbye, World"))
    foo: Foo = Foo(Hello, World,42,Bar(Goodbye, World))

    scala> import com.twitter.util.jackson.JSON
    import com.twitter.util.jackson.JSON

    scala> JSON.write(foo)
    res3: String = {"a":"Hello, World","b":42,"c":{"d":"Goodbye, World"}}

    scala> // or use the configured "pretty print mapper"

    scala> JSON.prettyPrint(foo)
    res4: String =
    {
      "a" : "Hello, World",
      "b" : 42,
      "c" : {
        "d" : "Goodbye, World"
      }
    }

    scala> // or use a configured ScalaObjectMapper

    scala> import com.twitter.util.jackson.ScalaObjectMapper
    import com.twitter.util.jackson.ScalaObjectMapper

    scala> val mapper = ScalaObjectMapper()
    mapper: com.twitter.util.jackson.ScalaObjectMapper = com.twitter.util.jackson.ScalaObjectMapper@490d9c41

    scala> mapper.writeValueAsString(foo)
    res0: String = {"a":"Hello, World","b":42,"c":{"d":"Goodbye, World"}}

    scala> // or use the configured "pretty print mapper"

    scala> mapper.writePrettyString(foo)
    res1: String =
    {
      "a" : "Hello, World",
      "b" : 42,
      "c" : {
        "d" : "Goodbye, World"
      }
    }

To **deserialize** a JSON string into a case class, use

.. code:: scala

    ScalaObjectMapper#parse[T](s: String): T // or
    JSON#parse[T](s: String): T

For example, assuming the same `Bar` and `Foo` case classes defined above:

.. code:: scala
   :emphasize-lines: 10, 21

    Welcome to Scala 2.12.12 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> val s = """{"a": "Hello, World", "b": 42, "c": {"d": "Goodbye, World"}}"""
    s: String = {"a": "Hello, World", "b": 42, "c": {"d": "Goodbye, World"}}

    scala> import com.twitter.util.jackson.JSON
    import com.twitter.util.jackson.JSON

    scala> val foo = JSON.parse[Foo](s)
    foo: Option[Foo] = Some(Foo(Hello, World,42,Bar(Goodbye, World)))

    scala> // or use a configured ScalaObjectMapper

    scala> import com.twitter.util.jackson.ScalaObjectMapper
    import com.twitter.util.jackson.ScalaObjectMapper

    scala> val mapper = ScalaObjectMapper()
    mapper: com.twitter.util.jackson.ScalaObjectMapper = com.twitter.util.jackson.ScalaObjectMapper@3b64f131

    scala> val foo = mapper.parse[Foo](s)
    foo: Foo = Foo(Hello, World,42,Bar(Goodbye, World))

.. tip::

    As seen above you can use the `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__ utility for
    general JSON serde operations with a default configured `ScalaObjectMapper <#defaults>`__.

    This can be useful when you do not need to use a specifically configured `ScalaObjectMapper` or
    do not wish to perform any Bean Validation 2.0 style validations during JSON deserialization since
    the `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__ utility **specifically disables**
    validation support on its underlying `c.t.util.jackson.ScalaObjectMapper`.

    See the `documentation <#c-t-util-jackson-json>`__ for more information.

You can find many examples of using the `ScalaObjectMapper` in the various framework tests:

- Scala `example <https://github.com/twitter/util/blob/develop/util-jackson/src/test/scala/com/twitter/util/jackson/ScalaObjectMapperTest.scala>`__.
- Java `example <https://github.com/twitter/util/blob/develop/util-jackson/src/test/java/com/twitter/util/jackson/tests/ScalaObjectMapperJavaTest.java>`__.

As mentioned above, there is also a plethora of Jackson `tutorials <https://github.com/FasterXML/jackson-docs#tutorials>`__ and `HOW-TOs <https://github.com/FasterXML/jackson-docs#external-off-github-documentation>`__
available online which provide more in-depth examples of how to use a Jackson `ObjectMapper <http://fasterxml.github.io/jackson-databind/javadoc/2.10/com/fasterxml/jackson/databind/ObjectMapper.html>`__.

`ScalaObjectMapper`
-------------------

The |ScalaObjectMapper|_ is a thin wrapper around a configured |jackson-module-scala|_ `ScalaObjectMapper`.
However, the util-jackson |ScalaObjectMapper|_ comes configured with several defaults when instantiated.

Defaults
~~~~~~~~

The following integrations are provided by default when using the |ScalaObjectMapper|_:

-  The Jackson `DefaultScalaModule <https://github.com/FasterXML/jackson-module-scala/blob/master/src/main/scala/com/fasterxml/jackson/module/scala/DefaultScalaModule.scala>`__.
-  The Jackson `JSR310 <https://jcp.org/aboutJava/communityprocess/pfd/jsr310/JSR-310-guide.html>`__ `c.f.jackson.datatype.jsr310.JavaTimeModule <https://fasterxml.github.io/jackson-modules-java8/javadoc/datetime/2.12/com/fasterxml/jackson/datatype/jsr310/JavaTimeModule.html>`__.
-  A `LongKeyDeserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/util-jackson/serde/LongKeyDeserializer.scala>`__ which allows for deserializing Scala Maps with Long keys.
-  A `WrappedValueSerializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/util-jackson/caseclass/wrapped/WrappedValueSerializer.scala>`__ (more information on "WrappedValues" `here <https://docs.scala-lang.org/overviews/core/value-classes.html>`__).
-  Twitter `c.t.util.Time <https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/util/Time.scala>`_ and `c.t.util.Duration <https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/util/Duration.scala>`_ serializers [`1 <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/serde/TimeStringSerializer.scala>`__, `2 <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/serde/DurationStringSerializer.scala>`__] and deserializers [`1 <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/serde/TimeStringDeserializer.scala>`__, `2 <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/serde/DurationStringDeserializer.scala>`__].
-  An improved `CaseClassDeserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/jackson/CaseClassDeserializer.scala>`__: see details `below <#improved-case-class-deserializer>`__.
-  Integration with the `util-validator <https://twitter.github.io/util/guide/util-validator/index.html>`__ Bean Validation 2.0 style validations during JSON deserialization.

Instantiation
~~~~~~~~~~~~~

Instantiation of a new |ScalaObjectMapper|_ can done either via a companion object method or via
the `ScalaObject#Builder` to specify custom configuration.

`ScalaObjectMapper#apply`
^^^^^^^^^^^^^^^^^^^^^^^^^

The companion object defines `apply` methods for creation of a |ScalaObjectMapper|_ configured
with the defaults listed above:

.. code:: scala

    import com.twitter.util.jackson.ScalaObjectMapper
    import com.fasterxml.jackson.databind.{ObjectMapper => JacksonObjectMapper}
    import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}

    val objectMapper: ScalaObjectMapper = ScalaObjectMapper()

    val underlying: JacksonObjectMapper with JacksonScalaObjectMapper = ???
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper(underlying)

.. important::

    The above `#apply` which takes an underlying Jackson `ObjectMapper` will **mutate the configuration**
    of the underlying Jackson `ObjectMapper` to apply the default configuration to the given Jackson
    `ObjectMapper`. Thus it is not expected that this underlying Jackson `ObjectMapper` be a shared
    resource.

Companion Object Wrappers
^^^^^^^^^^^^^^^^^^^^^^^^^

The companion object also defines other methods to easily obtain some specifically configured
|ScalaObjectMapper|_ which wraps an *already configured* Jackson `ObjectMapper`:

.. code:: scala

    import com.twitter.util.jackson.ScalaObjectMapper
    import com.fasterxml.jackson.databind.{ObjectMapper => JacksonObjectMapper}
    import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}

    val underlying: JacksonObjectMapper with JacksonScalaObjectMapper = ???

    // different from `#apply(underlying)`. wraps a copy of the given Jackson mapper
    // and does not apply any configuration.
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.objectMapper(underlying)

    // merely wraps a copy of the given Jackson mapper that is expected to be configured
    // with a YAMLFactory, does not apply any configuration.
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.yamlObjectMapper(underlying)

    // only sets the PropertyNamingStrategy to be PropertyNamingStrategy.LOWER_CAMEL_CASE
    // to a copy of the given Jackson mapper, does not apply any other configuration.
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.camelCaseObjectMapper(underlying)

    // only sets the PropertyNamingStrategy to be PropertyNamingStrategy.SNAKE_CASE
    // to a copy of the given Jackson mapper, does not apply any other configuration.
    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.snakeCaseObjectMapper(underlying)

.. admonition:: These methods clone the underlying mapper, they do not mutate the given Jaskson mapper.

    Note that these methods will *copy* the underlying Jackson mapper (not mutate it) to apply any
    necessary configuration to produce a new |ScalaObjectMapper|_, which in this case is only to
    change the `PropertyNamingStrategy` accordingly. No other configuration changes are made
    to the copy of the underlying Jackson mapper.

    Specifically, note that the `ScalaObjectMapper.objectMapper(underlying)` **wraps a copy but does not mutate the original**
    configuration of the given underlying Jackson `ObjectMapper`. This is different from the
    `ScalaObjectMapper(underlying)` which **mutates** the given underlying Jackson `ObjectMapper` to
    apply all of the default configuration to produce a `ScalaObjectMapper`.

`ScalaObjectMapper#Builder`
^^^^^^^^^^^^^^^^^^^^^^^^^^^

You can use the `ScalaObjectMapper#Builder` for more advanced control over configuration options for
producing a configured `ScalaObjectMapper`. For example, to create an instance of a |ScalaObjectMapper|_
with case class validation via the util-validator `ScalaValidator` **disabled**:

.. code :: scala

    import com.twitter.util.jackson.ScalaObjectMapper

    val objectMapper: ScalaObjectMapper = ScalaObjectMapper.builder.withNoValidation.objectMapper

See the `Advanced Configuration <#advanced-configuration>`__ section for more information.

Advanced Configuration
~~~~~~~~~~~~~~~~~~~~~~

To apply more custom configuration to create a |ScalaObjectMapper|_, there is a builder for
constructing a customized mapper.

E.g., to set a `PropertyNamingStrategy` different than the default:

.. code:: scala
   :emphasize-lines: 3

    val objectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withPropertyNamingStrategy(PropertyNamingStrategy.KebabCaseStrategy)
        .objectMapper

Or to set additional modules or configuration:

.. code:: scala
   :emphasize-lines: 4, 5, 6, 7

    val objectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withPropertyNamingStrategy(PropertyNamingStrategy.KebabCaseStrategy)
        .withAdditionalJacksonModules(Seq(MySimpleJacksonModule))
        .withAdditionalMapperConfigurationFn(
          _.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        )
        .objectMapper

You can also get a `camelCase`, `snake_case`, or even a YAML configured mapper.

.. code:: scala
   :emphasize-lines: 7, 15, 23

    val camelCaseObjectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withAdditionalJacksonModules(Seq(MySimpleJacksonModule))
        .withAdditionalMapperConfigurationFn(
          _.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        )
        .camelCaseObjectMapper

    val snakeCaseObjectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withAdditionalJacksonModules(Seq(MySimpleJacksonModule))
        .withAdditionalMapperConfigurationFn(
          _.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        )
        .snakeCaseObjectMapper

    val yamlObjectMapper: ScalaObjectMapper =
      ScalaObjectMapper.builder
        .withAdditionalJacksonModules(Seq(MySimpleJacksonModule))
        .withAdditionalMapperConfigurationFn(
          _.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
        )
        .yamlObjectMapper

Access to the underlying Jackson Object Mapper
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The |ScalaObjectMapper|_ is a thin wrapper around a configured Jackson |jackson-module-scala|_
`com.fasterxml.jackson.module.scala.ScalaObjectMapper`, thus you can always access the underlying
Jackson object mapper by calling `underlying`:

.. code:: scala
   :emphasize-lines: 7

    import com.fasterxml.jackson.databind.ObjectMapper
    import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}
    import com.twitter.util.jackson.ScalaObjectMapper

    val objectMapper: ScalaObjectMapper = ???

    val jacksonObjectMapper: ObjectMapper with JacksonScalaObjectMapper = objectMapper.underlying

Adding a Custom Serializer or Deserializer
------------------------------------------

For more information see the Jackson documentation for
`Custom Serializers <https://github.com/FasterXML/jackson-docs/wiki/JacksonHowToCustomSerializers>`__.

Add a Jackson Module to a |ScalaObjectMapper|_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Follow the steps to create a Jackson Module for the custom serializer or deserializer then register
the module to the underlying Jackson mapper from the |ScalaObjectMapper|_ instance:

.. code:: scala
   :emphasize-lines: 13, 14, 15, 43, 44, 45, 46, 47, 48, 52, 53

    import com.fasterxml.jackson.databind.JsonDeserializer
    import com.fasterxml.jackson.databind.deser.Deserializers
    import com.fasterxml.jackson.databind.module.SimpleModule
    import com.fasterxml.jackson.module.scala.JacksonModule
    import com.twitter.util.jackson.ScalaObjectMapper

    // custom deserializer
    class FooDeserializer extends JsonDeserializer[Foo] {
      override def deserialize(...)
    }

    // Jackson SimpleModule for custom deserializer
    class FooDeserializerModule extends SimpleModule {
      addDeserializer(FooDeserializer)
    }

    // custom parameterized deserializer
    class MapIntIntDeserializer extends JsonDeserializer[Map[Int, Int]] {
      override def deserialize(...)
    }

    // custom parameterized deserializer resolver
    class MapIntIntDeserializerResolver extends Deserializers.Base {
      override def findBeanDeserializer(
        javaType: JavaType,
        config: DeserializationConfig,
        beanDesc: BeanDescription
      ): MapIntIntDeserializer = {
        if (javaType.isMapLikeType && javaType.hasGenericTypes && hasIntTypes(javaType)) {
          new MapIntIntDeserializer
        } else null
      }

      private[this] def hasIntTypes(javaType: JavaType): Boolean = {
        val k = javaType.containedType(0)
        val v = javaType.containedType(1)
        k.isPrimitive && k.getRawClass == classOf[Integer] &&
          v.isPrimitive && v.getRawClass == classOf[Integer]
      }
    }

    // Jackson SimpleModule for custom deserializer
    class MapIntIntDeserializerModule extends JacksonModule {
      override def getModuleName: String = this.getClass.getName
      this += {
        _.addDeserializers(new MapIntIntDeserializerResolver)
      }
    }

    ...

    val mapper: ScalaObjectMapper = ???
    mapper.registerModules(new FooDeserializerModule, new MapIntIntDeserializerModule)

Improved `case class` deserializer
----------------------------------

The library provides a `case class deserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/CaseClassDeserializer.scala>`__
which overcomes some limitations in |jackson-module-scala|_:

-  Throws a `JsonMappingException` when required fields are missing from the parsed JSON.
-  Uses specified `case class` default values when fields are missing in the incoming JSON.
-  Properly deserializes a `Seq[Long]` (see: https://github.com/FasterXML/jackson-module-scala/issues/62).
-  Supports `"wrapped values" <https://docs.scala-lang.org/overviews/core/value-classes.html>`__ using `c.t.util.jackson.WrappedValue <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/WrappedValue.scala>`_.
-  Support for field and method level validations via integration with the `util-validator <https://twitter.github.io/util/guide/util-validator/index.html>`__ Bean Validation 2.0 style validations during JSON deserialization.
-  Accumulates all JSON deserialization errors (instead of failing fast) in a returned sub-class of `JsonMappingException` (see: `CaseClassMappingException <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/exceptions/CaseClassMappingException.scala>`_).

The `case class deserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/CaseClassDeserializer.scala>`__
is added by default when constructing a new |ScalaObjectMapper|_.

.. tip::

  Note: with the |CaseClassDeserializer|_, non-option fields without default values are
  **considered required**. If a required field is missing, a `CaseClassMappingException` is thrown.

  JSON `null` values are **not allowed and will be treated as a "missing" value**. If necessary, users
  can specify a custom deserializer for a field if they want to be able to parse a JSON `null` into
  a Scala `null` type for a field. Define your deserializer, `NullAllowedDeserializer` then annotate
  the field with `@JsonDeserialize(using = classOf[NullAllowedDeserializer])`.

`@JsonCreator` Support
~~~~~~~~~~~~~~~~~~~~~~

The |CaseClassDeserializer|_ supports specification of a constructor or static factory
method annotated with the Jackson Annotation, `@JsonCreator <https://github.com/FasterXML/jackson-annotations/wiki/Jackson-Annotations#deserialization-details>`_
(an annotation for indicating a specific constructor or static factory method to use for
instantiation of the case class during deserialization).

For example, you can annotate a method on the companion object for the case class as a static
factory for instantiation. Any static factory method to use for instantiation **MUST** be specified
on the companion object for case class:

.. code:: scala
   :emphasize-lines: 4

    case class MySimpleCaseClass(int: Int)

    object MySimpleCaseClass {
      @JsonCreator
      def apply(s: String): MySimpleCaseClass = MySimpleCaseClass(s.toInt)
    }

Or to specify a secondary constructor to use for case class instantiation:

.. code:: scala
   :emphasize-lines: 2

    case class MyCaseClassWithMultipleConstructors(number1: Long, number2: Long, number3: Long) {
      @JsonCreator
      def this(numberAsString1: String, numberAsString2: String, numberAsString3: String) {
        this(numberAsString1.toLong, numberAsString2.toLong, numberAsString3.toLong)
      }
    }

.. note::

    If you define multiple constructors on a case class, it is **required** to annotate one of the
    constructors with `@JsonCreator`.

    To annotate the primary constructor (as the syntax can seem non-intuitive because the `()` is
    required):

    .. code:: scala
       :emphasize-lines: 1

        case class MyCaseClassWithMultipleConstructors @JsonCreator()(number1: Long, number2: Long, number3: Long) {
          def this(numberAsString1: String, numberAsString2: String, numberAsString3: String) {
            this(numberAsString1.toLong, numberAsString2.toLong, numberAsString3.toLong)
          }
        }

    The parens are needed because the Scala class constructor syntax requires constructor
    annotations to have exactly one parameter list, possibly empty.

    If you define multiple case class constructors with no visible `@JsonCreator` constructor or
    static factory method via a companion, deserialization will error.

`@JsonFormat` Support
~~~~~~~~~~~~~~~~~~~~~

The |CaseClassDeserializer|_ supports `@JsonFormat`-annotated case class fields to properly
contextualize deserialization based on the values in the annotation.

A common use case is to be able to support deserializing a JSON string into a "time" representation
class based on a specific pattern independent of the time format configured on the `ObjectMapper` or
even the default format for a given deserializer for the type.

For instance, the library provides a `specific deserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/serde/TimeStringDeserializer.scala>`_
for the `com.twitter.util.Time <https://github.com/twitter/util/blob/develop/util-core/src/main/scala/com/twitter/util/Time.scala>`_
class. This deserializer is a Jackson `ContextualDeserializer <https://fasterxml.github.io/jackson-databind/javadoc/2.9/com/fasterxml/jackson/databind/deser/ContextualDeserializer.html>`_
and will properly take into account a `@JsonFormat`-annotated field.

However, the |CaseClassDeserializer|_ is invoked first and acts as a proxy for deserializing the time
value. The case class deserializer properly contextualizes the field for correct deserialization by
the `TimeStringDeserializer`.

Thus if you had a case class defined:

.. code:: scala
   :emphasize-lines: 7

    import com.fasterxml.jackson.annotation.JsonFormat
    import com.twitter.util.Time

    case class Event(
      id: Long,
      description: String,
      @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") when: Time
    )

The following JSON:

.. code:: json

    {
      "id": 42,
      "description": "Something happened.",
      "when": "2018-09-14T23:20:08.000-07:00"
    }

Will always deserialize properly into the case class regardless of the pattern configured on the
`ObjectMapper` or as the default of a contextualized deserializer:

.. code:: scala

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import com.fasterxml.jackson.annotation.JsonFormat
    import com.fasterxml.jackson.annotation.JsonFormat

    scala> import com.twitter.util.Time
    import com.twitter.util.Time

    scala> case class Event(
         |   id: Long,
         |   description: String,
         |   @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") when: Time
         | )
    defined class Event

    scala> val json = """
         | {
         |   "id": 42,
         |   "description": "Something happened.",
         |   "when": "2018-09-14T23:20:08.000-07:00"
         | }""".stripMargin
    json: String =
    "
    {
      "id": 42,
      "description": "Something happened.",
      "when": "2018-09-14T23:20:08.000-07:00"
    }"

    scala> import com.twitter.util.jackson.ScalaObjectMapper
    import com.twitter.util.jackson.ScalaObjectMapper

    scala> val mapper = ScalaObjectMapper()
    mapper: com.twitter.util.jackson.ScalaObjectMapper = com.twitter.util.jackson.ScalaObjectMapper@52dc71b2

    scala> val event: Event = mapper.parse[Event](json)
    event: Event = Event(42,Something happened.,2018-09-15 06:20:08 +0000)

Jackson InjectableValues Support
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

By default, the library does not configure any `com.fasterxml.jackson.databind.InjectableValues <https://fasterxml.github.io/jackson-databind/javadoc/2.11/com/fasterxml/jackson/databind/InjectableValues.html>`__
implementation.

`@InjectableValue`
^^^^^^^^^^^^^^^^^^

It does however provide the `c.t.util.jackson.annotation.InjectableValue <https://github.com/twitter/util/blob/develop/util-jackson-annotations/src/main/java/com/twitter/util/jackson/annotation/InjectableValue.java>`__ annotation which can be used to mark *other* `java.lang.annotation.Annotation` interfaces as annotations
which support case class field injection via Jackson `com.fasterxml.jackson.databind.InjectableValues`.

That is, users can create custom annotations and annotate them with `@InjectableValue`. This
then allows for a configured Jackson `com.fasterxml.jackson.databind.InjectableValues` implementation
to be able to treat these annotations similar to the `@JacksonInject <https://fasterxml.github.io/jackson-annotations/javadoc/2.11/com/fasterxml/jackson/annotation/JacksonInject.html>`__.

This means a custom Jackson `InjectableValues` implementation can use the `@InjectableValue` marker
annotation to resolve fields annotated with annotations that have the `@InjectableValue` marker
annotation as injectable fields.

For more information on the Jackson `@JacksonInject` or `c.f.databind.InjectableValues` support see the
tutorial `here <https://www.baeldung.com/jackson-annotations#2-jacksoninject>`__.

`Mix-in Annotations <https://github.com/FasterXML/jackson-docs/wiki/JacksonMixInAnnotations>`_
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The Jackson `Mix-in Annotations <https://github.com/FasterXML/jackson-docs/wiki/JacksonMixInAnnotations>`_
provide a way to associate annotations to classes without needing to modify the target classes
themselves. It is intended to help support 3rd party datatypes where the user cannot modify the
sources to add annotations.

The |CaseClassDeserializer|_ supports Jackson `Mix-in Annotations <https://github.com/FasterXML/jackson-docs/wiki/JacksonMixInAnnotations>`_
for specifying field annotations during deserialization with the `case class deserializer <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/CaseClassDeserializer.scala>`_.

For example, to deserialize JSON into the following classes that are not yours to annotate:

.. code:: scala

    case class Point(x: Int, y: Int) {
      def area: Int = x * y
    }

    case class Points(points: Seq[Point])

However, you want to enforce field constraints with `validations <../util-validator/index.html>`_
during deserialization. You can define a `Mix-in`,

.. code:: scala

    import com.fasterxml.jackson.annotation.JsonIgnore
    import jakarta.validation.constraints.{Max, Min}

    trait PointMixIn {
      @Min(0) @Max(100) def x: Int
      @Min(0) @Max(100) def y: Int
      @JsonIgnore def area: Int
    }

Then register this `Mix-in` for the `Point` class type. There are several ways to do this:

Follow the steps to create a Jackson Module for the `Mix-in` then register the module to the
underlying Jackson mapper from the |ScalaObjectMapper|_ instance:

.. code:: scala

    import com.fasterxml.jackson.databind.module.SimpleModule
    import com.twitter.util.jackson.ScalaObjectMapper

    object PointMixInModule extends SimpleModule {
        setMixInAnnotation(classOf[Point], classOf[PointMixIn]);
    }

    ...

    val objectMapper: ScalaObjectMapper = ???
    objectMapper.registerModule(PointMixInModule)

Or register the `Mix-in` for the class type directly on the underlying Jackson mapper (without a Jackson Module):

.. code:: scala

    import com.twitter.util.jackson.ScalaObjectMapper

    val objectMapper: ScalaObjectMapper = ???
    objectMapper.underlying.addMixin[Point, PointMixIn]

Deserializing this JSON would then error with failed validations:

.. code:: json

    {
      "points": [
        {"x": -1, "y": 120},
        {"x": 4, "y": 99}
      ]
    }

.. code:: scala
   :emphasize-lines: 51, 52, 53

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> case class Point(x: Int, y: Int) {
         |       def area: Int = x * y
         |     }
    defined class Point

    scala> case class Points(points: Seq[Point])
    defined class Points

    scala> import com.fasterxml.jackson.annotation.JsonIgnore
    import com.fasterxml.jackson.annotation.JsonIgnore

    scala> import jakarta.validation.constraints.{Max, Min}
    import jakarta.validation.constraints.{Max, Min}

    scala> trait PointMixIn {
         |       @Min(0) @Max(100) def x: Int
         |       @Min(0) @Max(100) def y: Int
         |       @JsonIgnore def area: Int
         |     }
    defined trait PointMixIn

    scala> import com.twitter.util.jackson.ScalaObjectMapper
    import com.twitter.util.jackson.ScalaObjectMapper

    scala> val objectMapper: ScalaObjectMapper = ScalaObjectMapper()
    objectMapper: com.twitter.util.jackson.ScalaObjectMapper = com.twitter.util.jackson.ScalaObjectMapper@2389f546

    scala> objectMapper.underlying.addMixin[Point, PointMixIn]
    res0: com.fasterxml.jackson.databind.ObjectMapper = com.twitter.util.jackson.ScalaObjectMapper$Builder$$anon$1@5ae22651

    scala> val json = """
         | {
         |       "points": [
         |         {"x": -1, "y": 120},
         |         {"x": 4, "y": 99}
         |       ]
         |     }""".stripMargin
    json: String =
    "
    {
          "points": [
            {"x": -1, "y": 120},
            {"x": 4, "y": 99}
          ]
        }"

    scala> val points = objectMapper.parse[Points](json)
    com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException: 2 errors encountered during deserialization.
            Errors: com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException: points.x: must be greater than or equal to 0
                    com.twitter.util.jackson.caseclass.exceptions.CaseClassFieldMappingException: points.y: must be less than or equal to 100
      at com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException$.apply(CaseClassMappingException.scala:21)
      at com.twitter.util.jackson.caseclass.CaseClassDeserializer.deserialize(CaseClassDeserializer.scala:431)
      at com.twitter.util.jackson.caseclass.CaseClassDeserializer.deserializeNonWrapperClass(CaseClassDeserializer.scala:408)
      at com.twitter.util.jackson.caseclass.CaseClassDeserializer.deserialize(CaseClassDeserializer.scala:373)
      at com.fasterxml.jackson.databind.ObjectMapper._readMapAndClose(ObjectMapper.java:4524)
      at com.fasterxml.jackson.databind.ObjectMapper.readValue(ObjectMapper.java:3466)
      at com.fasterxml.jackson.module.scala.ScalaObjectMapper.readValue(ScalaObjectMapper.scala:191)
      at com.fasterxml.jackson.module.scala.ScalaObjectMapper.readValue$(ScalaObjectMapper.scala:190)
      at com.twitter.util.jackson.ScalaObjectMapper$Builder$$anon$1.readValue(ScalaObjectMapper.scala:382)
      at com.twitter.util.jackson.ScalaObjectMapper.parse(ScalaObjectMapper.scala:463)
      ... 34 elided

As the first `Point` instance has an x-value less than the minimum of 0 and a y-value greater than
the maximum of 100.

Known `CaseClassDeserializer` Limitations
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The |CaseClassDeserializer|_ provides a fair amount of utility but can not and does not
support all Jackson Annotations. The behavior of supporting a Jackson Annotation can at times be
ambiguous (or even nonsensical), especially when it comes to combining Jackson Annotations and
injectable field annotations.

Java Enums
~~~~~~~~~~

We recommend the use of `Java Enums <https://docs.oracle.com/javase/tutorial/java/javaOO/enum.html>`__
for representing enumerations since they integrate well with Jackson's ObjectMapper and have
exhaustiveness checking as of Scala 2.10.

The following `Jackson annotations <https://github.com/FasterXML/jackson-annotations>`__ may be
useful when working with Enums:

- `@JsonValue`: can be used for an overridden `toString` method.
- `@JsonEnumDefaultValue`: can be used for defining a default value when deserializing unknown Enum values. Note that this requires `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE <https://github.com/FasterXML/jackson-databind/wiki/Deserialization-Features#value-conversions-coercion>`_ feature to be enabled.

`c.t.util.jackson.JSON`
-----------------------

The library provides a utility for default mapping of JSON to an object or writing an object as a JSON
string. This is largely inspired by the `scala.util.parsing.json.JSON <https://www.scala-lang.org/api/2.12.6/scala-parser-combinators/scala/util/parsing/json/JSON$.html>`__
from the Scala Parser Combinators library.

However, the `c.t.util.jackson.JSON <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/JSON.scala>`__
utility uses a default configured `ScalaObjectMapper <#defaults>`__ and is thus more full featured
than the `scala.util.parsing.json.JSON` utility.

.. important::

    The `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__ API does not return an exception when
    parsing but rather returns an `Option[T]` result. When parsing is successful, this is a `Some(T)`,
    otherwise it is a `None`. But note that the specifics of any failure are lost.

    It is thus also important to note that for this reason that the `c.t.util.jackson.JSON` uses a
    `default configured ScalaObjectMapper <#defaults>`__ **with validation specifically disabled**,
    such that no `Bean Validation 2.0 <../util-validator>`__ style validations are performed when
    parsing with `c.t.util.jackson.JSON`.

    Users should prefer using a configured |ScalaObjectMapper|_ to perform validations in order to
    be able to properly handle validation exceptions.

.. code:: scala
   :emphasize-lines: 7, 13, 22, 25

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import com.twitter.util.jackson.JSON
    import com.twitter.util.jackson.JSON

    scala> val result = JSON.parse[Map[String, Int]]("""{"a": 1, "b": 2}""")
    result: Option[Map[String,Int]] = Some(Map(a -> 1, b -> 2))

    scala> case class FooClass(id: String)
    defined class FooClass

    scala> val result = JSON.parse[FooClass]("""{"id": "abcd1234"}""")
    result: Option[FooClass] = Some(FooClass(abcd1234))

    scala> result.get
    res0: FooClass = FooClass(abcd1234)

    scala> val f = FooClass("99999999")
    f: FooClass = FooClass(99999999)

    scala> JSON.write(f)
    res1: String = {"id":"99999999"}

    scala> JSON.prettyPrint(f)
    res2: String =
    {
      "id" : "99999999"
    }

    scala>


`c.t.util.jackson.YAML`
-----------------------

Similarly, there is also a utility for `YAML <https://yaml.org/>`__ serde operations with many of
the same methods as `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__ using a default |ScalaObjectMapper|_
configured with a `YAMLFactory`.

.. code:: scala
   :emphasize-lines: 8, 9, 10, 11, 12, 18, 19, 20, 29

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import com.twitter.util.jackson.YAML
    import com.twitter.util.jackson.YAML

    scala> val result =
         |   YAML.parse[Map[String, Int]]("""
         |     |---
         |     |a: 1
         |     |b: 2
         |     |c: 3""".stripMargin)
    result: Option[Map[String,Int]] = Some(Map(a -> 1, b -> 2, c -> 3))

    scala> case class FooClass(id: String)
    defined class FooClass

    scala> val result = YAML.parse[FooClass]("""
         |   |---
         |   |id: abcde1234""".stripMargin)
    result: Option[FooClass] = Some(FooClass(abcde1234))

    scala> result.get
    res0: FooClass = FooClass(abcde1234)

    scala> val f = FooClass("99999999")
    f: FooClass = FooClass(99999999)

    scala> YAML.write(f)
    res1: String =
    "---
    id: "99999999"
    "

    scala>

.. important::

    Like with `c.t.util.jackson.JSON <#c-t-util-jackson-json>`__, the `c.t.util.jackson.YAML <#c-t-util-jackson-yaml>`__
    API does not return an exception when parsing but rather returns an `Option[T]` result.

    Users should prefer using a configured |ScalaObjectMapper|_ to perform validations in order to be
    able to properly handle validation exceptions.

`c.t.util.jackson.JsonDiff`
---------------------------

The library provides a utility for comparing JSON strings, or structures that can be serialized as
JSON strings, via the `c.t.util.jackson.JsonDiff <https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/JsonDiff.scala>`__ utility.

`JsonDiff` provides two functions: `diff` and `assertDiff`. The `diff` method allows the user to
decide how to handle JSON differences by returning an `Option[JsonDiff.Result]` while `assertDiff`
throws an `AssertionError` when a difference is encountered.

The `JsonDiff.Result#toString` contains a textual representation meant to indicate where the
`expected` and `actual` differ semantically.  For this representation, both `expected` and `actual`
are transformed to eliminate insigificant lexical diffences such whitespace, object key ordering,
and escape sequences.  Only the first difference in this representation is indicated.
When an `AssertError` is thrown, the `JsonDiff.Result#toString` is used to
populate the exception message.

For example:

.. code:: scala
   :emphasize-lines: 13, 19, 54

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import com.twitter.util.jackson.JsonDiff
    import com.twitter.util.jackson.JsonDiff

    scala> val a ="""{"a":1,"b":2}"""
    a: String = {"a":1,"b":2}

    scala> val b ="""{"b": 2,"a": 1}"""
    b: String = {"b": 2,"a": 1}

    scala> val result = JsonDiff.diff(a, b) // no difference
    result: Option[com.twitter.util.jackson.JsonDiff.Result] = None

    scala> val b ="""{"b": 3,"a": 1}"""
    b: String = {"b": 3,"a": 1}

    scala> val result = JsonDiff.diff(a, b) // b-values are different
    result: Option[com.twitter.util.jackson.JsonDiff.Result] =
    Some(                     *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3})

    scala> result.get.toString
    res1: String =
    "                     *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}"

    scala> result.get.expected
    res3: com.fasterxml.jackson.databind.JsonNode = {"a":1,"b":2}

    scala> result.get.actual
    res4: com.fasterxml.jackson.databind.JsonNode = {"b":3,"a":1}

    scala> result.get.expectedPrettyString
    res5: String =
    {
      "a" : 1,
      "b" : 2
    }

    scala> result.get.actualPrettyString
    res6: String =
    {
      "b" : 3,
      "a" : 1
    }

    scala> import com.twitter.util.Try
    import com.twitter.util.Try

    scala> val t = Try(JsonDiff.assertDiff(a, b)) // throws an AssertionError
    JSON DIFF FAILED!
                         *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}

    scala> t.isThrow
    res0: Boolean = true

    scala> t.throwable.getMessage
    res1: String =
    com.twitter.util.jackson.JsonDiff$ failure
                         *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}

    scala> val expected = """{"t1": "24\u00B0C"}"""
    expected: String = {"t1": "24\u00B0C"}

    scala> val actual = """{"t1": "24°F", "t2": null}"""
    actual: String = {"t1": "24°F", "t2": null}

    scala> val t = Try(JsonDiff.assertDiff(expected, actual))  // throws an AssertionError
    JSON DIFF FAILED!
                        *
    Expected: {"t1":"24°C"}
    Actual:   {"t1":"24°F","t2":null}

    scala> t.isThrow
    res0: Boolean = true

    scala> t.throwable.getMessage
    res1: String =
    com.twitter.util.jackson.JsonDiff$ failure
                        *
    Expected: {"t1":"24°C"}
    Actual:   {"t1":"24°F","t2":null}


Normalization
~~~~~~~~~~~~~

Both API methods accept a "normalize function" which is a function to apply on the *actual* to "normalize" any
fields -- such as a timestamp -- before comparing to the *expected*.

.. code:: scala
   :emphasize-lines: 43, 44, 45, 46, 49, 52

    Welcome to Scala 2.12.13 (JDK 64-Bit Server VM, Java 1.8.0_242).
    Type in expressions for evaluation. Or try :help.

    scala> import com.twitter.util.jackson.JsonDiff
    import com.twitter.util.jackson.JsonDiff

    scala> val a ="""{"a":1,"b":2}"""
    a: String = {"a":1,"b":2}

    scala> val b ="""{"b": 3,"a": 1}"""
    b: String = {"b": 3,"a": 1}

    scala> val result = JsonDiff.diff(expected = a, actual = b) // b-values are different
    result: Option[com.twitter.util.jackson.JsonDiff.Result] =
    Some(                     *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3})

    scala> result.get.toString
    res1: String =
    "                     *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}"

    scala> JsonDiff.assertDiff(expected = a, actual = b) // throws an AssertionError
    JSON DIFF FAILED!
                         *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}
    java.lang.AssertionError: com.twitter.util.jackson.JsonDiff$ failure
                         *
    Expected: {"a":1,"b":2}
    Actual:   {"a":1,"b":3}
      at com.twitter.util.jackson.JsonDiff$.assert(JsonDiff.scala:206)
      at com.twitter.util.jackson.JsonDiff$.assertDiff(JsonDiff.scala:114)
      ... 34 elided

    scala> import com.fasterxml.jackson.databind.JsonNode
    import com.fasterxml.jackson.databind.JsonNode

    scala> import com.fasterxml.jackson.databind.node.ObjectNode
    import com.fasterxml.jackson.databind.node.ObjectNode

    scala> val normalizeFn: JsonNode => JsonNode = { jsonNode: JsonNode =>
         |   jsonNode.asInstanceOf[ObjectNode].put("b", 2)
         | }
    normalizeFn: com.fasterxml.jackson.databind.JsonNode => com.fasterxml.jackson.databind.JsonNode = $Lambda$1162/1826212603@3da20c42

    scala> val result = JsonDiff.diff(expected = a, actual = b, normalizeFn) // normalize fn updates b-value of 'actual' to match the 'expected'
    result: Option[com.twitter.util.jackson.JsonDiff.Result] = None

    scala> JsonDiff.assertDiff(expected = a, actual = b, normalizeFn) // no exception when 'actual' is normalized

    scala>

.. |ScalaObjectMapper| replace:: `ScalaObjectMapper`
.. _ScalaObjectMapper: https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/ScalaObjectMapper.scala

.. |CaseClassDeserializer| replace:: `util-jackson case class deserializer`
.. _CaseClassDeserializer: https://github.com/twitter/util/blob/develop/util-jackson/src/main/scala/com/twitter/util/jackson/caseclass/CaseClassDeserializer.scala

.. |jackson-module-scala| replace:: `jackson-module-scala`
.. _jackson-module-scala: https://github.com/FasterXML/jackson-module-scala


