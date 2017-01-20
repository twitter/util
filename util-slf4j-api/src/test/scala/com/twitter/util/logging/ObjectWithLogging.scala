package com.twitter.util.logging

object ObjectWithLogging extends Logging {

  def interesting: String = {
    info("Very interesting")
    "This is an interesting method"
  }

}
