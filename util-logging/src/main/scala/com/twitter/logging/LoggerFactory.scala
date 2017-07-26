/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.logging

/**
 * A factory to configure a Logger.  Note that because Loggers are global, executing this
 * factory has side-effects.
 *
 * @param node
 * Name of the logging node. The default ("") is the top-level logger.
 *
 * @param level
 * Log level for this node. Leaving it None is java's secret signal to use the parent logger's
 * level.
 *
 * @param handlers
 * Where to send log messages.
 *
 * @param useParents
 * Override to have log messages stop at this node. Otherwise they are passed up to parent
 * nodes.
 */
case class LoggerFactory(
  node: String = "",
  level: Option[Level] = None,
  handlers: List[HandlerFactory] = Nil,
  useParents: Boolean = true
) extends (() => Logger) {

  /**
   * Registers new handlers and setting with the logger at `node`
   * @note It clears all the existing handlers for the node
   */
  def apply(): Logger = {
    val logger = Logger.get(node)
    logger.clearHandlers()
    level.foreach { x =>
      logger.setLevel(x)
    }
    handlers.foreach { h =>
      logger.addHandler(h())
    }
    logger.setUseParentHandlers(useParents)
    logger
  }
}

/**
 * Shim for java compatibility.  Make a new LoggerFactoryBuilder with `LoggerFactory#newBuilder()`.
 */
class LoggerFactoryBuilder private[logging] (factory: LoggerFactory) {
  def node(_node: String): LoggerFactoryBuilder =
    new LoggerFactoryBuilder(factory.copy(node = _node))

  def level(_level: Level): LoggerFactoryBuilder =
    new LoggerFactoryBuilder(factory.copy(level = Some(_level)))

  def parentLevel(): LoggerFactoryBuilder = new LoggerFactoryBuilder(factory.copy(level = None))

  def addHandler[T <: Handler](handler: () => T): LoggerFactoryBuilder =
    new LoggerFactoryBuilder(factory.copy(handlers = handler :: factory.handlers))

  def unhandled(): LoggerFactoryBuilder = new LoggerFactoryBuilder(factory.copy(handlers = Nil))

  def useParents(): LoggerFactoryBuilder = new LoggerFactoryBuilder(factory.copy(useParents = true))

  def ignoreParents(): LoggerFactoryBuilder =
    new LoggerFactoryBuilder(factory.copy(useParents = false))

  def build(): LoggerFactory = factory
}

object LoggerFactory {
  def newBuilder(): LoggerFactoryBuilder = new LoggerFactoryBuilder(LoggerFactory())
}
