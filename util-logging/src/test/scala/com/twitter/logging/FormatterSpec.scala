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

import java.util.{logging => javalog}
import org.scalatest.{WordSpec, Matchers}
import com.twitter.conversions.string._

class FormatterSpec extends WordSpec with Matchers {
  val basicFormatter = new Formatter

  val utcFormatter = new Formatter(
    timezone = Some("UTC")
  )

  val fullPackageFormatter = new Formatter(
    timezone = Some("UTC"),
    useFullPackageNames = true
  )

  val prefixFormatter = new Formatter(
    timezone = Some("UTC"),
    prefix = "%2$s <HH:mm> %1$.4s "
  )

  val truncateFormatter = new Formatter(
    timezone = Some("UTC"),
    truncateAt = 30
  )

  val record1 = new javalog.LogRecord(Level.ERROR, "boo.")
  record1.setLoggerName("com.example.jobs.BadJob")
  record1.setMillis(1206769996722L)

  val record2 = new javalog.LogRecord(Level.DEBUG, "useless info.")
  record2.setLoggerName("com.example.jobs.BadJob")
  record2.setMillis(1206769996722L)

  val record3 = new javalog.LogRecord(Level.CRITICAL,
    "Something terrible happened that may take a very long time to explain because I write crappy log messages.")
  record3.setLoggerName("net.lag.whiskey.Train")
  record3.setMillis(1206769996722L)

  "Formatter" should {
    "create a prefix" in {
      basicFormatter.formatPrefix(Level.ERROR, "20080329-05:53:16.722", "(root)") shouldEqual
        "ERR [20080329-05:53:16.722] (root): "
      basicFormatter.formatPrefix(Level.DEBUG, "20080329-05:53:16.722", "(root)") shouldEqual
        "DEB [20080329-05:53:16.722] (root): "
      basicFormatter.formatPrefix(Level.WARNING, "20080329-05:53:16.722", "(root)") shouldEqual
        "WAR [20080329-05:53:16.722] (root): "
    }

    "format a log level name" in {
      basicFormatter.formatLevelName(Level.ERROR) shouldEqual "ERROR"
      basicFormatter.formatLevelName(Level.DEBUG) shouldEqual "DEBUG"
      basicFormatter.formatLevelName(Level.WARNING) shouldEqual "WARNING"
    }

    "format text" in {
      val record = new LogRecord(Level.ERROR, "error %s")
      basicFormatter.formatText(record) shouldEqual "error %s"
      record.setParameters(Array("123"))
      basicFormatter.formatText(record) shouldEqual "error 123"
    }

    "format a timestamp" in {
      utcFormatter.format(record1) shouldEqual "ERR [20080329-05:53:16.722] jobs: boo.\n"
    }

    "do lazy message evaluation" in {
      var callCount = 0
      def getSideEffect = {
        callCount += 1
        "ok"
      }

      val record = new LazyLogRecord(Level.DEBUG, "this is " + getSideEffect)

      callCount shouldEqual 0
      basicFormatter.formatText(record) shouldEqual "this is ok"
      callCount shouldEqual 1
      basicFormatter.formatText(record) shouldEqual "this is ok"
      callCount shouldEqual 1
    }

    "format package names" in {
      utcFormatter.format(record1) shouldEqual "ERR [20080329-05:53:16.722] jobs: boo.\n"
      fullPackageFormatter.format(record1) shouldEqual
        "ERR [20080329-05:53:16.722] com.example.jobs: boo.\n"
    }

    "handle other prefixes" in {
      prefixFormatter.format(record2) shouldEqual "jobs 05:53 DEBU useless info.\n"
    }

    "truncate line" in {
      truncateFormatter.format(record3) shouldEqual
        "CRI [20080329-05:53:16.722] whiskey: Something terrible happened th...\n"
    }

    "write stack traces" should {
      object ExceptionLooper {
        def cycle(n: Int) {
          if (n == 0) {
            throw new Exception("Aie!")
          } else {
            cycle(n - 1)
            throw new Exception("this is just here to fool the tail recursion optimizer")
          }
        }

        def cycle2(n: Int) {
          try {
            cycle(n)
          } catch {
            case t: Throwable =>
              throw new Exception("grrrr", t)
          }
        }
      }

      def scrub(in: String) = {
        in.regexSub("""FormatterSpec.scala:\d+""".r) { m =>
          "FormatterSpec.scala:NNN"
        }.regexSub("""FormatterSpec\$[\w\\$]+""".r) { m =>
          "FormatterSpec$$"
        }
      }

      "simple" in {
        val exception = try {
          ExceptionLooper.cycle(10)
          null
        } catch {
          case t: Throwable => t
        }
        Formatter.formatStackTrace(exception, 5).map { scrub(_) } shouldEqual List(
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    (...more...)")
      }

      "nested" in {
        val exception = try {
          ExceptionLooper.cycle2(2)
          null
        } catch {
          case t: Throwable => t
        }
        Formatter.formatStackTrace(exception, 2).map { scrub(_) } shouldEqual List(
          "    at com.twitter.logging.FormatterSpec$$.cycle2(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.apply$mcV$sp(FormatterSpec.scala:NNN)",
          "    (...more...)",
          "Caused by java.lang.Exception: Aie!",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    (...more...)")

      }
    }
  }
}
