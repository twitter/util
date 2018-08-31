package com.twitter.logging

import com.twitter.finagle.stats.NullStatsReceiver
import com.twitter.logging.ScribeHandler._

object ScribeHandlers {
  /**
   * For Java compatibility. Calls [[ScribeHandler.apply()]] method with
   * the provided arguments, so Java users don't need to provide the
   * other default arguments in the call.
   */
  def apply(
    category: String,
    formatter: Formatter
  ): () => ScribeHandler =
    ScribeHandler.apply(
      hostname = DefaultHostname,
      port = DefaultPort,
      category = category,
      bufferTime = DefaultBufferTime,
      connectBackoff = DefaultConnectBackoff,
      maxMessagesPerTransaction = DefaultMaxMessagesPerTransaction,
      maxMessagesToBuffer = DefaultMaxMessagesToBuffer,
      formatter = formatter,
      level = None,
      statsReceiver = NullStatsReceiver
    )
}
