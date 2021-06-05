package com.twitter.util.jackson.serde

import com.fasterxml.jackson.databind.module.SimpleModule
import com.twitter.{util => ctu}

private[jackson] object DefaultSerdeModule extends SimpleModule {
  addSerializer(WrappedValueSerializer)
  addSerializer(DurationStringSerializer)
  addSerializer(TimeStringSerializer())
  addDeserializer(classOf[ctu.Duration], DurationStringDeserializer)
  addDeserializer(classOf[ctu.Time], TimeStringDeserializer())
}
