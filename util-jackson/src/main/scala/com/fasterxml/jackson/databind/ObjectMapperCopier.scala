package com.fasterxml.jackson.databind

import com.fasterxml.jackson.module.scala.ScalaObjectMapper

/** INTERNAL API ONLY */
object ObjectMapperCopier {
  // Workaround since calling objectMapper.copy on "ObjectMapper with ScalaObjectMapper" fails the _checkInvalidCopy check
  def copy(objectMapper: ObjectMapper): ObjectMapper with ScalaObjectMapper = {
    new ObjectMapper(objectMapper) with ScalaObjectMapper
  }
}
