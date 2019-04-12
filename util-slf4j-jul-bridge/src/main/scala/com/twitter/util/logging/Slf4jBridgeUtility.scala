package com.twitter.util.logging

import com.twitter.concurrent.Once
import org.slf4j.LoggerFactory
import org.slf4j.bridge.SLF4JBridgeHandler

/**
 * A utility to safely install the [[https://www.slf4j.org/ slf4j-api]] [[https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html SLF4JBridgeHandler]].
 * The utility attempts to detect if the `slf4j-jdk14` dependency is present on
 * the classpath as it is unwise to install the `jul-to-slf4j` bridge with the `slf4j-jdk14`
 * dependency present as it may cause an [[https://www.slf4j.org/legacy.html#julRecursion infinite loop]].
 *
 * If the [[https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html SLF4JBridgeHandler]] is already installed we do not try to install
 * it again.
 *
 * Additionally note there is a performance impact for bridging jul log messages in this
 * manner. However, using the [[https://logback.qos.ch/manual/configuration.html#LevelChangePropagator Logback LevelChangePropagator]]
 * (> 0.9.25) can eliminate this performance impact.
 *
 * @see [[https://www.slf4j.org/legacy.html#jul-to-slf4j SLF4J: jul-to-slf4j bridge]]
 */
object Slf4jBridgeUtility extends Logging {

  /**
   * Attempt installation of the [[https://www.slf4j.org/ slf4j-api]] [[https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html SLF4JBridgeHandler]].
   * The [[https://www.slf4j.org/api/org/slf4j/bridge/SLF4JBridgeHandler.html SLF4JBridgeHandler]] will not be installed if already installed or if the
   * `slf4j-jdk14` dependency is detected on the classpath.
   */
  def attemptSlf4jBridgeHandlerInstallation(): Unit = install()

  /* Private */

  private val install = Once {
    if (!SLF4JBridgeHandler.isInstalled && canInstallBridgeHandler) {
      SLF4JBridgeHandler.removeHandlersForRootLogger()
      SLF4JBridgeHandler.install()
      info("org.slf4j.bridge.SLF4JBridgeHandler installed.")
    }
  }

  /**
   * We do not want to install the bridge handler if the
   * `org.slf4j.impl.JDK14LoggerFactory` exists on the classpath.
   */
  private def canInstallBridgeHandler: Boolean = {
    try {
      Class.forName("org.slf4j.impl.JDK14LoggerFactory", false, this.getClass.getClassLoader)
      LoggerFactory
        .getLogger(this.getClass)
        .warn(
          "Detected [org.slf4j.impl.JDK14LoggerFactory] on classpath. " +
            "SLF4JBridgeHandler will not be installed, see: https://www.slf4j.org/legacy.html#julRecursion"
        )
      false
    } catch {
      case e: ClassNotFoundException =>
        true
    }
  }
}
