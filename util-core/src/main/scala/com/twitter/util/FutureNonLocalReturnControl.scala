package com.twitter.util

import scala.runtime.NonLocalReturnControl

class FutureNonLocalReturnControl(cause: NonLocalReturnControl[_]) extends Exception(cause) {
  override def getMessage: String = "Invalid use of `return` in closure passed to a Future"
}
