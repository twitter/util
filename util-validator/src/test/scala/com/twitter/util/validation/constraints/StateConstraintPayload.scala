package com.twitter.util.validation.constraints

import jakarta.validation.Payload

case class StateConstraintPayload(
  invalid: String,
  expected: String)
    extends Payload
