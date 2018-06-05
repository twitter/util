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

package com.twitter.jvm

import com.twitter.util.Duration

/**
 * Support for heapster profiling (google perftools compatible):
 *
 * https://github.com/mariusaeriksen/heapster
 */
class Heapster(klass: Class[_]) {
  private val startM = klass.getDeclaredMethod("start")
  private val stopM = klass.getDeclaredMethod("stop")
  private val dumpProfileM =
    klass.getDeclaredMethod("dumpProfile", classOf[java.lang.Boolean])
  private val clearProfileM = klass.getDeclaredMethod("clearProfile")
  private val setSamplingPeriodM =
    klass.getDeclaredMethod("setSamplingPeriod", classOf[java.lang.Integer])

  def start(): Unit = { startM.invoke(null) }
  def shutdown(): Unit = { stopM.invoke(null) }
  def setSamplingPeriod(period: java.lang.Integer): Unit = { setSamplingPeriodM.invoke(null, period) }
  def clearProfile(): Unit = { clearProfileM.invoke(null) }
  def dumpProfile(forceGC: java.lang.Boolean): Array[Byte] =
    dumpProfileM.invoke(null, forceGC).asInstanceOf[Array[Byte]]

  def profile(howlong: Duration, samplingPeriod: Int = 10 << 19, forceGC: Boolean = true) = {
    clearProfile()
    setSamplingPeriod(samplingPeriod)

    start()
    Thread.sleep(howlong.inMilliseconds)
    shutdown()
    dumpProfile(forceGC)
  }
}

object Heapster {
  val instance: Option[Heapster] = {
    val loader = ClassLoader.getSystemClassLoader()
    try {
      Some(new Heapster(loader.loadClass("Heapster")))
    } catch {
      case _: ClassNotFoundException =>
        None
    }
  }
}
