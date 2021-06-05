package com.twitter.util.jackson.serde

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.twitter.util.WrappedValue

private[jackson] object WrappedValueSerializer
    extends StdSerializer[WrappedValue[_]](classOf[WrappedValue[_]]) {

  override def serialize(
    wrappedValue: WrappedValue[_],
    jgen: JsonGenerator,
    provider: SerializerProvider
  ): Unit = {
    jgen.writeObject(wrappedValue.onlyValue)
  }
}
