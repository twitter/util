package com.twitter.util.validation

import jakarta.validation.MessageInterpolator
import java.util.Locale

class WrongMessageInterpolator extends MessageInterpolator {
  override def interpolate(
    s: String,
    context: MessageInterpolator.Context
  ): String = "Whatever you entered was wrong"

  override def interpolate(
    s: String,
    context: MessageInterpolator.Context,
    locale: Locale
  ): String = "Whatever you entered was wrong"
}
