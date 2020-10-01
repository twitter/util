package com.twitter.util.logging

abstract class AbstractTraitWithLogging extends TraitWithLogging

trait TraitWithLogging extends Logging {

  def myMethod1: String = {
    info("In myMethod1")
    "Hello, World"
  }
}
