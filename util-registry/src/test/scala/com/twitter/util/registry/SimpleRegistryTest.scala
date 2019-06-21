package com.twitter.util.registry


class SimpleRegistryTest extends RegistryTest {
  def mkRegistry(): Registry = new SimpleRegistry
  def name: String = "SimpleRegistry"
}
