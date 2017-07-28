package com.twitter.util.logging

class SecondItem(name: String, description: String, size: Int, count: Int)
    extends Item(name, description, size) {

  info(
    s"Creating new SecondItem: name = $name, description = $description, size = $size, count = $count"
  )
}
