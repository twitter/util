package com.twitter.util.logging

class Stock(symbol: String, price: BigDecimal) extends Logging {
  info(f"New stock with symbol = $symbol and price = ${price.toDouble}%1.2f")
  def quote: String = f"$symbol = ${price.toDouble}%1.2f"
}
