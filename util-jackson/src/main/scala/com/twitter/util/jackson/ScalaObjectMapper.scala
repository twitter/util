package com.twitter.util.jackson

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.TSFBuilder
import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import com.fasterxml.jackson.databind.{ObjectMapper => JacksonObjectMapper, _}
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLFactoryBuilder
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}
import com.twitter.io.Buf
import com.twitter.util.jackson.caseclass.CaseClassJacksonModule
import com.twitter.util.jackson.serde.DefaultSerdeModule
import com.twitter.util.jackson.serde.LongKeyDeserializers
import com.twitter.util.validation.ScalaValidator
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

object ScalaObjectMapper {

  /** The default [[ScalaValidator]] for a [[ScalaObjectMapper]] */
  private[twitter] val DefaultValidator: ScalaValidator = ScalaValidator()

  /** The default [[JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS]] setting */
  private[twitter] val DefaultNumbersAsStrings: Boolean = false

  /** Framework modules need to be added 'last' so they can override existing ser/des */
  private[twitter] val DefaultJacksonModules: Seq[Module] =
    Seq(DefaultScalaModule, new JavaTimeModule, LongKeyDeserializers, DefaultSerdeModule)

  /** The default [[PropertyNamingStrategy]] for a [[ScalaObjectMapper]] */
  private[twitter] val DefaultPropertyNamingStrategy: PropertyNamingStrategy =
    PropertyNamingStrategies.SNAKE_CASE

  /** The default [[JsonInclude.Include]] for serialization for a [[ScalaObjectMapper]] */
  private[twitter] val DefaultSerializationInclude: JsonInclude.Include =
    JsonInclude.Include.NON_ABSENT

  /** The default configuration for serialization as a `Map[SerializationFeature, Boolean]` */
  private[twitter] val DefaultSerializationConfig: Map[SerializationFeature, Boolean] =
    Map(
      SerializationFeature.WRITE_DATES_AS_TIMESTAMPS -> false,
      SerializationFeature.WRITE_ENUMS_USING_TO_STRING -> true)

  /** The default configuration for deserialization as a `Map[DeserializationFeature, Boolean]` */
  private[twitter] val DefaultDeserializationConfig: Map[DeserializationFeature, Boolean] =
    Map(
      DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES -> true,
      DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES -> false,
      DeserializationFeature.READ_ENUMS_USING_TO_STRING -> true,
      DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY -> true,
      DeserializationFeature.USE_JAVA_ARRAY_FOR_JSON_ARRAY -> true /* see jackson-module-scala/issues/148 */
    )

  /** The default for configuring additional modules on the underlying [[JacksonScalaObjectMapperType]] */
  private[twitter] val DefaultAdditionalJacksonModules: Seq[Module] = Seq.empty[Module]

  /** The default setting to enable case class validation during case class deserialization */
  private[twitter] val DefaultValidation: Boolean = true

  /**
   * Returns a new [[ScalaObjectMapper]] configured with [[Builder]] defaults.
   *
   * @return a new [[ScalaObjectMapper]] instance.
   *
   * @see [[com.fasterxml.jackson.databind.InjectableValues]]
   */
  def apply(): ScalaObjectMapper =
    ScalaObjectMapper.builder.objectMapper

  /**
   * Creates a new [[ScalaObjectMapper]] from an underlying [[JacksonScalaObjectMapperType]].
   *
   * @note this mutates the underlying mapper to configure it with the [[ScalaObjectMapper]] defaults.
   *
   * @param underlying the [[JacksonScalaObjectMapperType]] to wrap.
   *
   * @return a new [[ScalaObjectMapper]]
   */
  def apply(underlying: JacksonScalaObjectMapperType): ScalaObjectMapper = {
    new ScalaObjectMapper(
      ScalaObjectMapper.builder
        .configureJacksonScalaObjectMapper(underlying)
    )
  }

  /**
   * Utility to create a new [[ScalaObjectMapper]] which simply wraps the given
   * [[JacksonScalaObjectMapperType]].
   *
   * @note the `underlying` mapper is not mutated to produce the new [[ScalaObjectMapper]]
   */
  def objectMapper(underlying: JacksonScalaObjectMapperType): ScalaObjectMapper = {
    val objectMapperCopy = ObjectMapperCopier.copy(underlying)
    new ScalaObjectMapper(objectMapperCopy)
  }

  /**
   * Utility to create a new [[ScalaObjectMapper]] explicitly configured to serialize and deserialize
   * YAML using the given [[JacksonScalaObjectMapperType]]. The resultant mapper [[PropertyNamingStrategy]]
   * will be that configured on the `underlying` mapper.
   *
   * @note the `underlying` mapper is copied (not mutated) to produce the new [[ScalaObjectMapper]]
   *       to negotiate YAML serialization and deserialization.
   */
  def yamlObjectMapper(underlying: JacksonScalaObjectMapperType): ScalaObjectMapper =
    underlying.getFactory match {
      case _: YAMLFactory => // correct
        val objectMapperCopy = ObjectMapperCopier.copy(underlying)
        new ScalaObjectMapper(objectMapperCopy)
      case _ => // incorrect
        throw new IllegalArgumentException(
          s"The underlying mapper is not properly configured with a YAMLFactory")
    }

  /**
   * Utility to create a new [[ScalaObjectMapper]] explicitly configured with
   * [[PropertyNamingStrategies.LOWER_CAMEL_CASE]] as a `PropertyNamingStrategy` wrapping the
   * given [[JacksonScalaObjectMapperType]].
   *
   * @note the `underlying` mapper is copied (not mutated) to produce the new [[ScalaObjectMapper]]
   *       with a [[PropertyNamingStrategies.LOWER_CAMEL_CASE]] PropertyNamingStrategy.
   */
  def camelCaseObjectMapper(underlying: JacksonScalaObjectMapperType): ScalaObjectMapper = {
    val objectMapperCopy = ObjectMapperCopier.copy(underlying)
    objectMapperCopy.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
    new ScalaObjectMapper(objectMapperCopy)
  }

  /**
   * Utility to create a new [[ScalaObjectMapper]] explicitly configured with
   * [[PropertyNamingStrategies.SNAKE_CASE]] as a `PropertyNamingStrategy` wrapping the
   * given [[JacksonScalaObjectMapperType]].
   *
   * @note the `underlying` mapper is copied (not mutated) to produce the new [[ScalaObjectMapper]]
   *       with a [[PropertyNamingStrategies.SNAKE_CASE]] PropertyNamingStrategy.
   */
  def snakeCaseObjectMapper(underlying: JacksonScalaObjectMapperType): ScalaObjectMapper = {
    val objectMapperCopy = ObjectMapperCopier.copy(underlying)
    objectMapperCopy.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    new ScalaObjectMapper(objectMapperCopy)
  }

  /**
   *
   * Build a new instance of a [[ScalaObjectMapper]].
   *
   * For example,
   * {{{
   *   ScalaObjectMapper.builder
   *    .withPropertyNamingStrategy(new PropertyNamingStrategies.UpperCamelCaseStrategy)
   *    .withNumbersAsStrings(true)
   *    .withAdditionalJacksonModules(...)
   *    .objectMapper
   * }}}
   *
   * or
   *
   * {{{
   *   val builder =
   *    ScalaObjectMapper.builder
   *      .withPropertyNamingStrategy(new PropertyNamingStrategies.UpperCamelCaseStrategy)
   *      .withNumbersAsStrings(true)
   *      .withAdditionalJacksonModules(...)
   *
   *   val mapper = builder.objectMapper
   *   val camelCaseMapper = builder.camelCaseObjectMapper
   * }}}
   *
   */
  def builder: ScalaObjectMapper.Builder = Builder()

  /**
   * A Builder for creating a new [[ScalaObjectMapper]]. E.g., to build a new instance of
   * a [[ScalaObjectMapper]].
   *
   * For example,
   * {{{
   *   ScalaObjectMapper.builder
   *     .withPropertyNamingStrategy(new PropertyNamingStrategies.UpperCamelCaseStrategy)
   *     .withNumbersAsStrings(true)
   *     .withAdditionalJacksonModules(...)
   *     .objectMapper
   * }}}
   *
   * or
   *
   * {{{
   *   val builder =
   *     ScalaObjectMapper.builder
   *       .withPropertyNamingStrategy(new PropertyNamingStrategies.UpperCamelCaseStrategy)
   *       .withNumbersAsStrings(true)
   *       .withAdditionalJacksonModules(...)
   *
   *     val mapper = builder.objectMapper
   *     val camelCaseMapper = builder.camelCaseObjectMapper
   * }}}
   */
  case class Builder private[jackson] (
    propertyNamingStrategy: PropertyNamingStrategy = DefaultPropertyNamingStrategy,
    numbersAsStrings: Boolean = DefaultNumbersAsStrings,
    serializationInclude: Include = DefaultSerializationInclude,
    serializationConfig: Map[SerializationFeature, Boolean] = DefaultSerializationConfig,
    deserializationConfig: Map[DeserializationFeature, Boolean] = DefaultDeserializationConfig,
    defaultJacksonModules: Seq[Module] = DefaultJacksonModules,
    validator: Option[ScalaValidator] = Some(DefaultValidator),
    additionalJacksonModules: Seq[Module] = DefaultAdditionalJacksonModules,
    additionalMapperConfigurationFns: Seq[JacksonObjectMapper => Unit] = Seq.empty,
    validation: Boolean = DefaultValidation) {

    /* Public */

    /** Create a new [[ScalaObjectMapper]] from this [[Builder]]. */
    final def objectMapper: ScalaObjectMapper =
      new ScalaObjectMapper(jacksonScalaObjectMapper)

    /** Create a new [[ScalaObjectMapper]] from this [[Builder]] using the given [[JsonFactory]]. */
    final def objectMapper[F <: JsonFactory](factory: F): ScalaObjectMapper =
      new ScalaObjectMapper(jacksonScalaObjectMapper(factory))

    /**
     * Create a new [[ScalaObjectMapper]] explicitly configured to serialize and deserialize
     * YAML from this [[Builder]].
     *
     * @note the used [[PropertyNamingStrategy]] is defined by the current [[Builder]] configuration.
     */
    final def yamlObjectMapper: ScalaObjectMapper =
      new ScalaObjectMapper(
        configureJacksonScalaObjectMapper(new YAMLFactoryBuilder(new YAMLFactory())))

    /**
     * Creates a new [[ScalaObjectMapper]] explicitly configured with
     * [[PropertyNamingStrategies.LOWER_CAMEL_CASE]] as a `PropertyNamingStrategy`.
     */
    final def camelCaseObjectMapper: ScalaObjectMapper =
      ScalaObjectMapper.camelCaseObjectMapper(jacksonScalaObjectMapper)

    /**
     * Creates a new [[ScalaObjectMapper]] explicitly configured with
     * [[PropertyNamingStrategies.SNAKE_CASE]] as a `PropertyNamingStrategy`.
     */
    final def snakeCaseObjectMapper: ScalaObjectMapper =
      ScalaObjectMapper.snakeCaseObjectMapper(jacksonScalaObjectMapper)

    /* Builder Methods */

    /**
     * Configure a [[PropertyNamingStrategy]] for this [[Builder]].
     * @note the default is [[PropertyNamingStrategies.SNAKE_CASE]]
     * @see [[ScalaObjectMapper.DefaultPropertyNamingStrategy]]
     */
    final def withPropertyNamingStrategy(propertyNamingStrategy: PropertyNamingStrategy): Builder =
      this.copy(propertyNamingStrategy = propertyNamingStrategy)

    /**
     * Enable the [[JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS]] for this [[Builder]].
     * @note the default is false.
     */
    final def withNumbersAsStrings(numbersAsStrings: Boolean): Builder =
      this.copy(numbersAsStrings = numbersAsStrings)

    /**
     * Configure a [[JsonInclude.Include]] for serialization for this [[Builder]].
     * @note the default is [[JsonInclude.Include.NON_ABSENT]]
     * @see [[ScalaObjectMapper.DefaultSerializationInclude]]
     */
    final def withSerializationInclude(serializationInclude: Include): Builder =
      this.copy(serializationInclude = serializationInclude)

    /**
     * Set the serialization configuration for this [[Builder]] as a `Map` of `SerializationFeature`
     * to `Boolean` (enabled).
     * @note the default is described by [[ScalaObjectMapper.DefaultSerializationConfig]].
     * @see [[ScalaObjectMapper.DefaultSerializationConfig]]
     */
    final def withSerializationConfig(
      serializationConfig: Map[SerializationFeature, Boolean]
    ): Builder =
      this.copy(serializationConfig = serializationConfig)

    /**
     * Set the deserialization configuration for this [[Builder]] as a `Map` of `DeserializationFeature`
     * to `Boolean` (enabled).
     * @note this overwrites the default deserialization configuration of this [[Builder]].
     * @note the default is described by [[ScalaObjectMapper.DefaultDeserializationConfig]].
     * @see [[ScalaObjectMapper.DefaultDeserializationConfig]]
     */
    final def withDeserializationConfig(
      deserializationConfig: Map[DeserializationFeature, Boolean]
    ): Builder =
      this.copy(deserializationConfig = deserializationConfig)

    /**
     * Configure a [[ScalaValidator]] for this [[Builder]]
     * @see [[ScalaObjectMapper.DefaultValidator]]
     *
     * @note If you pass `withNoValidation` to the builder all case class validations will be
     *       bypassed, regardless of the `withValidator` configuration.
     */
    final def withValidator(validator: ScalaValidator): Builder =
      this.copy(validator = Some(validator))

    /**
     * Configure the list of additional Jackson [[Module]]s for this [[Builder]].
     * @note this will overwrite (not append) the list additional Jackson [[Module]]s of this [[Builder]].
     */
    final def withAdditionalJacksonModules(additionalJacksonModules: Seq[Module]): Builder =
      this.copy(additionalJacksonModules = additionalJacksonModules)

    /**
     * Configure additional [[JacksonObjectMapper]] functionality for the underlying mapper of this [[Builder]].
     * @note this will overwrite any previously set function.
     */
    final def withAdditionalMapperConfigurationFn(mapperFn: JacksonObjectMapper => Unit): Builder =
      this
        .copy(additionalMapperConfigurationFns = this.additionalMapperConfigurationFns :+ mapperFn)

    /** Method to allow changing of the default Jackson Modules for use from the `ScalaObjectMapperModule` */
    private[twitter] final def withDefaultJacksonModules(
      defaultJacksonModules: Seq[Module]
    ): Builder =
      this.copy(defaultJacksonModules = defaultJacksonModules)

    /**
     * Disable case class validation during case class deserialization
     *
     * @see [[ScalaObjectMapper.DefaultValidation]]
     * @note If you pass `withNoValidation` to the builder all case class validations will be
     *       bypassed, regardless of the `withValidator` configuration.
     */
    final def withNoValidation: Builder =
      this.copy(validation = false)

    /* Private */

    private[this] def defaultMapperConfiguration(mapper: JacksonObjectMapper): Unit = {
      /* Serialization Config */
      mapper.setDefaultPropertyInclusion(
        JsonInclude.Value.construct(serializationInclude, serializationInclude))
      mapper
        .configOverride(classOf[Option[_]])
        .setIncludeAsProperty(JsonInclude.Value.construct(serializationInclude, Include.ALWAYS))
      for ((feature, state) <- serializationConfig) {
        mapper.configure(feature, state)
      }

      /* Deserialization Config */
      for ((feature, state) <- deserializationConfig) {
        mapper.configure(feature, state)
      }
    }

    /** Order is important: default + case class module + any additional */
    private[this] def jacksonModules: Seq[Module] = {
      this.defaultJacksonModules ++
        Seq(new CaseClassJacksonModule(if (this.validation) this.validator else None)) ++
        this.additionalJacksonModules
    }

    private[this] final def jacksonScalaObjectMapper: JacksonScalaObjectMapperType =
      configureJacksonScalaObjectMapper(new JsonFactoryBuilder)

    private[this] final def jacksonScalaObjectMapper[F <: JsonFactory](
      jsonFactory: F
    ): JacksonScalaObjectMapperType = configureJacksonScalaObjectMapper(jsonFactory)

    private[this] final def configureJacksonScalaObjectMapper[
      F <: JsonFactory,
      B <: TSFBuilder[F, B]
    ](
      builder: TSFBuilder[F, B]
    ): JacksonScalaObjectMapperType = configureJacksonScalaObjectMapper(builder.build())

    private[jackson] final def configureJacksonScalaObjectMapper(
      factory: JsonFactory
    ): JacksonScalaObjectMapperType =
      configureJacksonScalaObjectMapper(
        new JacksonObjectMapper(factory) with JacksonScalaObjectMapper)

    private[jackson] final def configureJacksonScalaObjectMapper(
      underlying: JacksonScalaObjectMapperType
    ): JacksonScalaObjectMapperType = {
      if (this.numbersAsStrings) {
        underlying.enable(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.mappedFeature())
      }

      this.defaultMapperConfiguration(underlying)
      this.additionalMapperConfigurationFns.foreach(_(underlying))

      underlying.setPropertyNamingStrategy(this.propertyNamingStrategy)
      // Block use of a set of "unsafe" base types such as java.lang.Object
      // to prevent exploitation of Remote Code Execution (RCE) vulnerability
      // This line can be removed when this feature is enabled by default in Jackson 3
      underlying.enable(MapperFeature.BLOCK_UNSAFE_POLYMORPHIC_BASE_TYPES)

      this.jacksonModules.foreach(underlying.registerModule)

      underlying
    }
  }
}

private[jackson] object ArrayElementsOnNewLinesPrettyPrinter extends DefaultPrettyPrinter {
  _arrayIndenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE
  override def createInstance(): DefaultPrettyPrinter = new DefaultPrettyPrinter(this)
}

/**
 * A thin wrapper over a [[https://github.com/FasterXML/jackson-module-scala jackson-module-scala]]
 * [[com.fasterxml.jackson.module.scala.ScalaObjectMapper]]
 *
 * @note this API is inspired by the [[https://github.com/codahale/jerkson Jerkson]]
 *       [[https://github.com/codahale/jerkson/blob/master/src/main/scala/com/codahale/jerkson/Parser.scala Parser]]
 *
 * @param underlying a configured [[JacksonScalaObjectMapperType]]
 */
class ScalaObjectMapper(val underlying: JacksonScalaObjectMapperType) {
  assert(underlying != null, "Underlying ScalaObjectMapper cannot be null.")

  /**
   * Constructed [[ObjectWriter]] that will serialize objects using specified pretty printer
   * for indentation (or if null, no pretty printer).
   */
  lazy val prettyObjectMapper: ObjectWriter =
    underlying.writer(ArrayElementsOnNewLinesPrettyPrinter)

  /** Returns the currently configured [[PropertyNamingStrategy]] */
  def propertyNamingStrategy: PropertyNamingStrategy =
    underlying.getPropertyNamingStrategy

  /**
   * Factory method for constructing a [[com.fasterxml.jackson.databind.ObjectReader]] that will
   * read or update instances of specified type, `T`.
   * @tparam T the type for which to create a [[com.fasterxml.jackson.databind.ObjectReader]]
   * @return the created [[com.fasterxml.jackson.databind.ObjectReader]].
   */
  def reader[T: Manifest]: ObjectReader =
    underlying.readerFor[T]

  /** Read a value from a [[Buf]] into a type `T`. */
  def parse[T: Manifest](buf: Buf): T =
    parse[T](Buf.ByteBuffer.Shared.extract(buf))

  /** Read a value from a [[ByteBuffer]] into a type `T`. */
  def parse[T: Manifest](byteBuffer: ByteBuffer): T = {
    val is = new ByteBufferBackedInputStream(byteBuffer)
    underlying.readValue[T](is)
  }

  /** Convert from a [[JsonNode]] into a type `T`. */
  def parse[T: Manifest](jsonNode: JsonNode): T =
    convert[T](jsonNode)

  /** Read a value from an [[InputStream]] (caller is responsible for closing the stream) into a type `T`. */
  def parse[T: Manifest](inputStream: InputStream): T =
    underlying.readValue[T](inputStream)

  /** Read a value from an Array[Byte] into a type `T`. */
  def parse[T: Manifest](bytes: Array[Byte]): T =
    underlying.readValue[T](bytes)

  /** Read a value from a String into a type `T`. */
  def parse[T: Manifest](string: String): T =
    underlying.readValue[T](string)

  /** Read a value from a [[JsonParser]] into a type `T`. */
  def parse[T: Manifest](jsonParser: JsonParser): T =
    underlying.readValue[T](jsonParser)

  /**
   * Convenience method for doing two-step conversion from given value, into an instance of given
   * value type, [[JavaType]] if (but only if!) conversion is needed. If given value is already of
   * requested type, the value is returned as is.
   *
   * This method is functionally similar to first serializing a given value into JSON, and then
   * binding JSON data into a value of the given type, but should be more efficient since full
   * serialization does not (need to) occur. However, the same converters (serializers,
   * deserializers) will be used for data binding, meaning the same object mapper configuration
   * works.
   *
   * Note: it is possible that in some cases behavior does differ from full
   * serialize-then-deserialize cycle. It is not guaranteed, however, that the behavior is 100%
   * the same -- the goal is just to allow efficient value conversions for structurally compatible
   * Objects, according to standard Jackson configuration.
   *
   * Further note that this functionality is not designed to support "advanced" use cases, such as
   * conversion of polymorphic values, or cases where Object Identity is used.
   *
   * @param from value from which to convert.
   * @param toValueType type to be converted into.
   * @return a new instance of type [[JavaType]] converted from the given [[Any]] type.
   */
  def convert(from: Any, toValueType: JavaType): AnyRef = {
    try {
      underlying.convertValue(from, toValueType)
    } catch {
      case e: IllegalArgumentException if e.getCause != null =>
        throw e.getCause
    }
  }

  /**
   * Convenience method for doing two-step conversion from a given value, into an instance of a given
   * type, `T`. This is functionality equivalent to first serializing the given value into JSON,
   * then binding JSON data into a value of the given type, but may be executed without fully
   * serializing into JSON. The same converters (serializers, deserializers) will be used for
   * data binding, meaning the same object mapper configuration works.
   *
   * Note: when a [[com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException]]
   * is thrown inside of the the `ObjectMapper#convertValue` method, Jackson wraps the exception
   * inside of an [[IllegalArgumentException]]. As such we unwrap to restore the original
   * exception here. The wrapping occurs because the [[com.twitter.util.jackson.caseclass.exceptions.CaseClassMappingException]]
   * is a sub-type of [[java.io.IOException]] (through extension of [[com.fasterxml.jackson.databind.JsonMappingException]]
   * --> [[com.fasterxml.jackson.core.JsonProcessingException]] --> [[java.io.IOException]].
   *
   * @param any the value to be converted.
   * @tparam T the type to which to be converted.
   * @return a new instance of type `T` converted from the given [[Any]] type.
   *
   * @see [[https://github.com/FasterXML/jackson-databind/blob/d70b9e65c5e089094ec7583fa6a38b2f484a96da/src/main/java/com/fasterxml/jackson/databind/ObjectMapper.java#L2167]]
   */
  def convert[T: Manifest](any: Any): T = {
    try {
      underlying.convertValue[T](any)
    } catch {
      case e: IllegalArgumentException if e.getCause != null =>
        throw e.getCause
    }
  }

  /**
   * Method that can be used to serialize any value as JSON output, using the output stream
   * provided (using an encoding of [[com.fasterxml.jackson.core.JsonEncoding#UTF8]].
   *
   * Note: this method does not close the underlying stream explicitly here; however, the
   * [[com.fasterxml.jackson.core.JsonFactory]] this mapper uses may choose to close the stream
   * depending on its settings (by default, it will try to close it when
   * [[com.fasterxml.jackson.core.JsonGenerator]] constructed is closed).
   *
   * @param any the value to serialize.
   * @param outputStream the [[OutputStream]] to which to serialize.
   */
  def writeValue(any: Any, outputStream: OutputStream): Unit =
    underlying.writeValue(outputStream, any)

  /**
   * Method that can be used to serialize any value as a `Array[Byte]`. Functionally equivalent
   * to calling [[JacksonObjectMapper#writeValue(Writer,Object)]] with a [[java.io.ByteArrayOutputStream]] and
   * getting bytes, but more efficient. Encoding used will be UTF-8.
   *
   * @param any the value to serialize.
   * @return the `Array[Byte]` representing the serialized value.
   */
  def writeValueAsBytes(any: Any): Array[Byte] =
    underlying.writeValueAsBytes(any)

  /**
   * Method that can be used to serialize any value as a String. Functionally equivalent to calling
   * [[JacksonObjectMapper#writeValue(Writer,Object)]] with a [[java.io.StringWriter]]
   * and constructing String, but more efficient.
   *
   * @param any the value to serialize.
   * @return the String representing the serialized value.
   */
  def writeValueAsString(any: Any): String =
    underlying.writeValueAsString(any)

  /**
   * Method that can be used to serialize any value as a pretty printed String. Uses the
   * [[prettyObjectMapper]] and calls [[writeValueAsString(any: Any)]] on the given value.
   *
   * @param any the value to serialize.
   * @return the pretty printed String representing the serialized value.
   *
   * @see [[prettyObjectMapper]]
   * @see [[writeValueAsString(any: Any)]]
   */
  def writePrettyString(any: Any): String = any match {
    case str: String =>
      val jsonNode = underlying.readValue[JsonNode](str)
      prettyObjectMapper.writeValueAsString(jsonNode)
    case _ =>
      prettyObjectMapper.writeValueAsString(any)
  }

  /**
   * Method that can be used to serialize any value as a [[Buf]]. Functionally equivalent
   * to calling [[writeValueAsBytes(any: Any)]] and then wrapping the results in an
   * "owned" [[Buf.ByteArray]].
   *
   * @param any the value to serialize.
   * @return the [[Buf.ByteArray.Owned]] representing the serialized value.
   *
   * @see [[writeValueAsBytes(any: Any)]]
   * @see [[Buf.ByteArray.Owned]]
   */
  def writeValueAsBuf(any: Any): Buf =
    Buf.ByteArray.Owned(underlying.writeValueAsBytes(any))

  /**
   * Convenience method for doing the multi-step process of serializing a `Map[String, String]`
   * to a [[Buf]].
   *
   * @param stringMap the `Map[String, String]` to convert.
   * @return the [[Buf.ByteArray.Owned]] representing the serialized value.
   */
  // optimized
  def writeStringMapAsBuf(stringMap: Map[String, String]): Buf = {
    val os = new ByteArrayOutputStream()

    val jsonGenerator = underlying.getFactory.createGenerator(os)
    try {
      jsonGenerator.writeStartObject()
      for ((key, value) <- stringMap) {
        jsonGenerator.writeStringField(key, value)
      }
      jsonGenerator.writeEndObject()
      jsonGenerator.flush()

      Buf.ByteArray.Owned(os.toByteArray)
    } finally {
      jsonGenerator.close()
    }
  }

  /**
   * Method for registering a module that can extend functionality provided by this mapper; for
   * example, by adding providers for custom serializers and deserializers.
   *
   * @note this mutates the [[underlying]] [[com.fasterxml.jackson.databind.ObjectMapper]] of
   *       this [[ScalaObjectMapper]].
   *
   * @param module [[com.fasterxml.jackson.databind.Module]] to register.
   */
  def registerModule(module: Module): JacksonObjectMapper =
    underlying.registerModule(module)
}
