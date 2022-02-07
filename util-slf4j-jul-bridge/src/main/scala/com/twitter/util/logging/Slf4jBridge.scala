package com.twitter.util.logging

import com.twitter.app.App

/**
 * Installing the `SLF4JBridgeHandler` should be done as early as possible in order to capture any
 * log statements emitted to JUL. Generally it is best to install the handler in the constructor of
 * the main class of your application.
 *
 * This trait can be mixed into a [[com.twitter.app.App]] type and will attempt to install the
 * `SLF4JBridgeHandler` via the [[Slf4jBridgeUtility]] as part of the constructor of the resultant
 * class.
 * @see [[Slf4jBridgeUtility]]
 * @see [[org.slf4j.bridge.SLF4JBridgeHandler]]
 * @see [[https://www.slf4j.org/legacy.html Bridging legacy APIs]]
 */
trait Slf4jBridge { self: App =>

  /** Attempt Slf4jBridgeHandler installation */
  Slf4jBridgeUtility.attemptSlf4jBridgeHandlerInstallation()
}
