package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * "Serde" (technically "SerDe") is a portmanteau of "Serialization and Deserialization"
 *
 * Mix this trait into a case class to get helpful logging methods.
 * This trait adds a `@JsonIgnoreProperties` for fields which are
 * defined in the [[com.twitter.util.logging.Logging]] trait so that
 * they are not included in JSON serde operations.
 *
 */
@JsonIgnoreProperties(
  Array(
    "logger_name",
    "trace_enabled",
    "debug_enabled",
    "error_enabled",
    "info_enabled",
    "warn_enabled"
  )
)
trait SerdeLogging extends com.twitter.util.logging.Logging
