package com.twitter.util.logging

class Fruit(val name: String, val color: String, val price: Double) {
  val logger = Logger[Fruit]

  def description: String = {
    logger.info(s"Calculating description for fruit: $name")
    toString
  }

  override def toString: String = {
    s"A fruit with name = $name, color = $color and price = $price%1.2f"
  }
}
