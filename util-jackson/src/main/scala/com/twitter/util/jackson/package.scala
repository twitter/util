package com.twitter.util

import com.fasterxml.jackson.databind.{ObjectMapper => JacksonObjectMapper}
import com.fasterxml.jackson.module.scala.{ScalaObjectMapper => JacksonScalaObjectMapper}

package object jackson {
  type JacksonScalaObjectMapperType = JacksonObjectMapper with JacksonScalaObjectMapper
}
