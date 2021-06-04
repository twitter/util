/*
 * Copyright 2010 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.util

import java.lang.reflect.{InvocationHandler, Method, Proxy}

import scala.collection.{Map, Set, mutable}

object SignalHandlerFactory {
  def apply(): Option[SunSignalHandler] = {
    // only one actual implementation for now
    SunSignalHandler.instantiate()
  }
}

trait SignalHandler {
  def handle(signal: String, handlers: Map[String, Set[String => Unit]]): Unit
}

object SunSignalHandler {
  def instantiate(): Option[SunSignalHandler] = {
    try {
      Class.forName("sun.misc.Signal")
      Some(new SunSignalHandler())
    } catch {
      case ex: ClassNotFoundException => None
    }
  }
}

class SunSignalHandler extends SignalHandler {
  private val signalHandlerClass = Class.forName("sun.misc.SignalHandler")
  private val signalClass = Class.forName("sun.misc.Signal")
  private val handleMethod = signalClass.getMethod("handle", signalClass, signalHandlerClass)
  private val nameMethod = signalClass.getMethod("getName")

  def handle(signal: String, handlers: Map[String, Set[String => Unit]]): Unit = {
    val sunSignal =
      signalClass.getConstructor(classOf[String]).newInstance(signal).asInstanceOf[Object]
    val proxy = Proxy
      .newProxyInstance(
        signalHandlerClass.getClassLoader,
        Array[Class[_]](signalHandlerClass),
        new InvocationHandler {
          def invoke(proxy: Object, method: Method, args: Array[Object]) = {
            if (method.getName() == "handle") {
              handlers(signal).foreach { x => x(nameMethod.invoke(args(0)).asInstanceOf[String]) }
            }
            null
          }
        }
      )
      .asInstanceOf[Object]

    handleMethod.invoke(null, sunSignal, proxy)
  }
}

object HandleSignal {
  private val handlers = new mutable.HashMap[String, mutable.Set[String => Unit]]()

  /**
   * Set the callback function for a named unix signal.
   * For now, this only works on the Sun^H^H^HOracle JVM.
   */
  def apply(posixSignal: String)(f: String => Unit): Unit = {
    if (!handlers.contains(posixSignal)) {
      handlers.synchronized {
        SignalHandlerFactory().foreach { _.handle(posixSignal, handlers) }
        handlers(posixSignal) = mutable.HashSet[String => Unit]()
      }
    }

    handlers.synchronized {
      handlers(posixSignal) += f
    }
  }

  def clear(posixSignal: String): Unit = {
    handlers.synchronized {
      handlers(posixSignal).clear()
    }
  }
}
