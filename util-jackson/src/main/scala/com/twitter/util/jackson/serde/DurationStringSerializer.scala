package com.twitter.util.jackson.serde

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.twitter.util.Duration

/**
 * A Jackson [[StdSerializer]] for [[com.twitter.util.Duration]].
 */
object DurationStringSerializer extends StdSerializer[Duration](classOf[Duration]) {

  override def serialize(
    value: Duration,
    jgen: JsonGenerator,
    provider: SerializerProvider
  ): Unit = {
    jgen.writeString(value.toString)
  }
}
