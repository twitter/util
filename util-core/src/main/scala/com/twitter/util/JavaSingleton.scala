package com.twitter.util

/**
 * A mixin to allow scala object's to be used from java.
 */
trait JavaSingleton {
  def get = this
}