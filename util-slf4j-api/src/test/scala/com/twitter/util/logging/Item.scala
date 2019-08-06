package com.twitter.util.logging

object Item extends Logging {

  def baz: String = {
    info("In the baz method")
    "This is a baz."
  }
}

class Item(val name: String, val description: String, size: Int) extends Logging {
  import Item._
  info(s"New item: name = $name, description = $description, size  = $size")

  def dimension: Int = {
    info(s"Size = $size")
    if (size > 0) size else 0
  }

  def foo: String = {
    debug(s"name = $name, description = $description")
    // call baz method from companion
    baz
    s"$name: $description."
  }

  def bar: String = {
    if (size <= 0) {
      warn(s"Small size warning, size = $size")
    }
    s"The size is $size."
  }
}
