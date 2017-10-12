package com.twitter.jvm

import com.twitter.app.GlobalFlag

/**
 * Flag for the number of available processors
 *
 * If unset, the default is obtained from the JVM runtime.
 * If set to -1.0, the result is forced to the value provided by the JVM runtime.
 */
object numProcs
    extends GlobalFlag[Double](
      Runtime.getRuntime.availableProcessors().toDouble,
      "number of logical cores"
    ) {

  // We allow -1.0 to be a sentinel value to explicitly signal to
  // use the value of Runtime.getRuntime.availableProcessors()
  protected override def getValue: Option[Double] = super.getValue match {
    case Some(value) if approxEqual(value, -1.0) =>
      Some(Runtime.getRuntime.availableProcessors().toDouble)

    case other => other
  }

  private[this] def approxEqual(a: Double, b: Double): Boolean =
    math.abs(a - b) < 0.01
}
