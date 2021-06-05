package com.twitter.util.jackson.serde

import com.fasterxml.jackson.core.{JsonParser, JsonToken}
import com.fasterxml.jackson.databind.{
  DeserializationContext,
  JsonDeserializer,
  JsonMappingException
}
import com.twitter.util.Duration
import scala.util.control.NonFatal

/**
 * A Jackson [[JsonDeserializer]] for [[com.twitter.util.Duration]].
 */
object DurationStringDeserializer extends JsonDeserializer[Duration] {

  override final def deserialize(jp: JsonParser, ctxt: DeserializationContext): Duration =
    jp.currentToken match {
      case JsonToken.NOT_AVAILABLE | JsonToken.START_OBJECT =>
        handleToken(jp.nextToken, jp, ctxt)
      case _ =>
        handleToken(jp.currentToken, jp, ctxt)
    }

  private[this] def handleToken(
    token: JsonToken,
    jp: JsonParser,
    ctxt: DeserializationContext
  ): Duration = {
    val value = jp.getText.trim
    if (value.isEmpty) {
      throw JsonMappingException.from(ctxt, "field cannot be empty")
    } else {
      try {
        Duration.parse(value)
      } catch {
        case NonFatal(e) =>
          throw JsonMappingException.from(ctxt, e.getMessage, e)
      }
    }
  }

}
