package com.twitter.util.logging

class Stock(symbol: String, price: Double) extends Logging {
  info(f"New stock with symbol = $symbol and price = $price%1.2f")
  def quote: String = f"$symbol = $price%1.2f"
}
