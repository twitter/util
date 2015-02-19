package com.twitter.registry

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NaiveRegistryTest extends RegistryTest {
  def mkRegistry(): Registry = new NaiveRegistry()
  def name: String = "NaiveRegistry"
}
