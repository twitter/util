package com.twitter.util.logging

case class CaseClassWithLogging(length: Double, width: Double, height: Double) extends Logging {

  def area: Double = {
    debugResult(s"Calculating the area. Length = $length, width = $width, height = $height. Area = %s") {
      length * width * height
    }
  }

  def logNulls: Double = {
    info(null)
    info(Option(null))
    logger.infoWith("{}", None)
    infoResult("Calculated and area = %s1.2f") { area }
  }
}
