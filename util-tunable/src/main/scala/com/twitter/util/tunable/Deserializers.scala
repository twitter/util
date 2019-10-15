package com.twitter.util.tunable

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.twitter.util

/**
 * Deserializers for Deserializing a JSON [[com.twitter.util.tunable.Tunable]] value
 */
private[twitter] object json {

  /**
   * Deserialize a String representation of a [[com.twitter.util.Duration]] using
   * [[com.twitter.util.Duration.parse]]
   */
  val DurationFromString: StdDeserializer[util.Duration] =
    new StdDeserializer[util.Duration](classOf[util.Duration]) {
      override def deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
      ): util.Duration = util.Duration.parse(jsonParser.getText)
    }

  val StorageUnitFromString: StdDeserializer[util.StorageUnit] =
    new StdDeserializer[util.StorageUnit](classOf[util.StorageUnit]) {
      override def deserialize(
        jsonParser: JsonParser,
        deserializationContext: DeserializationContext
      ): util.StorageUnit = util.StorageUnit.parse(jsonParser.getText)
    }
}
