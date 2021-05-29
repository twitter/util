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

package com.twitter.logging

import java.net.{DatagramPacket, DatagramSocket}
import java.util.{logging => javalog}

import org.scalatest.wordspec.AnyWordSpec

class SyslogHandlerTest extends AnyWordSpec {
  val record1 = new javalog.LogRecord(Level.FATAL, "fatal message!")
  record1.setLoggerName("net.lag.whiskey.Train")
  record1.setMillis(1206769996722L)
  val record2 = new javalog.LogRecord(Level.ERROR, "error message!")
  record2.setLoggerName("net.lag.whiskey.Train")
  record2.setMillis(1206769996722L)

  "SyslogHandler" should {
    "write syslog entries" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = SyslogHandler(
        port = serverPort,
        formatter = new SyslogFormatter(
          timezone = Some("UTC"),
          hostname = "raccoon.local"
        )
      ).apply()
      syslog.publish(record1)
      syslog.publish(record2)

      SyslogFuture.sync()
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      assert(
        new String(
          p.getData,
          0,
          p.getLength) == "<9>2008-03-29T05:53:16 raccoon.local whiskey: fatal message!"
      )
      serverSocket.receive(p)
      assert(
        new String(
          p.getData,
          0,
          p.getLength) == "<11>2008-03-29T05:53:16 raccoon.local whiskey: error message!"
      )
    }

    "with server name" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = SyslogHandler(
        port = serverPort,
        formatter = new SyslogFormatter(
          serverName = Some("pingd"),
          timezone = Some("UTC"),
          hostname = "raccoon.local"
        )
      ).apply()
      syslog.publish(record1)

      SyslogFuture.sync()
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      assert(
        new String(
          p.getData,
          0,
          p.getLength) == "<9>2008-03-29T05:53:16 raccoon.local [pingd] whiskey: fatal message!"
      )
    }

    "with BSD time format" in {
      // start up new syslog listener
      val serverSocket = new DatagramSocket
      val serverPort = serverSocket.getLocalPort

      var syslog = SyslogHandler(
        port = serverPort,
        formatter = new SyslogFormatter(
          useIsoDateFormat = false,
          timezone = Some("UTC"),
          hostname = "raccoon.local"
        )
      ).apply()
      syslog.publish(record1)

      SyslogFuture.sync()
      val p = new DatagramPacket(new Array[Byte](1024), 1024)
      serverSocket.receive(p)
      assert(
        new String(
          p.getData,
          0,
          p.getLength) == "<9>Mar 29 05:53:16 raccoon.local whiskey: fatal message!"
      )
    }
  }
}
