package com.twitter.util.logging

class ExtendedItem(name: String, description: String, size: Int)
    extends Item(name, description, size)
    with Logging {

  info(s"My name = $name, description = $description, size = $size")
}
