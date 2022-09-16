package com.twitter.util

/**
 * Work for a `Fiber` to do.
 *
 * This is indistinguishable from a `Runnable` other than it is an abstract class.
 * We do this so that in the case of the JVM not being able to optimize away the
 * dynamic invocation we get v-table invocations which are constant time instead
 * of i-table invocations which require a linear search through the dynamic types
 * interfaces.
 */
private[twitter] abstract class FiberTask extends Runnable {
  // From the `Runnable` interface
  final def run(): Unit = doRun()

  // make this an abstract class method so we get v-table performance
  // when invoked directly.
  def doRun(): Unit
}
