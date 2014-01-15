package com.twitter.util

class HandledPromise[A] extends Promise[A] {
  @volatile var _handled: Option[Throwable] = None
  def handled: Option[Throwable] = _handled
  setInterruptHandler { case e => _handled = Some(e) }
}
