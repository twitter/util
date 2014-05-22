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
import org.scalatest.WordSpec

import com.twitter.conversions.string._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class FormatterTest extends WordSpec {
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
      assert(basicFormatter.formatPrefix(Level.ERROR, "20080329-05:53:16.722", "(root)") ===
        "ERR [20080329-05:53:16.722] (root): ")
      assert(basicFormatter.formatPrefix(Level.DEBUG, "20080329-05:53:16.722", "(root)") ===
        "DEB [20080329-05:53:16.722] (root): ")
      assert(basicFormatter.formatPrefix(Level.WARNING, "20080329-05:53:16.722", "(root)") ===
        "WAR [20080329-05:53:16.722] (root): ")
    }

    "format a log level name" in {
      assert(basicFormatter.formatLevelName(Level.ERROR) === "ERROR")
      assert(basicFormatter.formatLevelName(Level.DEBUG) === "DEBUG")
      assert(basicFormatter.formatLevelName(Level.WARNING) === "WARNING")
    }

    "format text" in {
      val record = new LogRecord(Level.ERROR, "error %s")
      assert(basicFormatter.formatText(record) === "error %s")
      record.setParameters(Array("123"))
      assert(basicFormatter.formatText(record) === "error 123")
    }

    "format a timestamp" in {
      assert(utcFormatter.format(record1) === "ERR [20080329-05:53:16.722] jobs: boo.\n")
    }

    "do lazy message evaluation" in {
      var callCount = 0
      def getSideEffect = {
        callCount += 1
        "ok"
      }

      val record = new LazyLogRecord(Level.DEBUG, "this is " + getSideEffect)

      assert(callCount === 0)
      assert(basicFormatter.formatText(record) === "this is ok")
      assert(callCount === 1)
      assert(basicFormatter.formatText(record) === "this is ok")
      assert(callCount === 1)
    }

    "format package names" in {
      assert(utcFormatter.format(record1) === "ERR [20080329-05:53:16.722] jobs: boo.\n")
      assert(fullPackageFormatter.format(record1) ===
        "ERR [20080329-05:53:16.722] com.example.jobs: boo.\n")
    }

    "handle other prefixes" in {
      assert(prefixFormatter.format(record2) === "jobs 05:53 DEBU useless info.\n")
    }

    "truncate line" in {
      assert(truncateFormatter.format(record3) ===
        "CRI [20080329-05:53:16.722] whiskey: Something terrible happened th...\n")
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
        in.regexSub("""FormatterTest.scala:\d+""".r) { m =>
          "FormatterTest.scala:NNN"
        }.regexSub("""FormatterTest\$[\w\\$]+""".r) { m =>
          "FormatterTest$$"
        }
      }

      "simple" in {
        val exception = try {
          ExceptionLooper.cycle(10)
          null
        } catch {
          case t: Throwable => t
        }
        assert(Formatter.formatStackTrace(exception, 5).map { scrub(_) } === List(
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    (...more...)"))
      }

      "nested" in {
        val exception = try {
          ExceptionLooper.cycle2(2)
          null
        } catch {
          case t: Throwable => t
        }
        assert(Formatter.formatStackTrace(exception, 2).map { scrub(_) } === List(
          "    at com.twitter.logging.FormatterTest$$.cycle2(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.apply$mcV$sp(FormatterTest.scala:NNN)",
          "    (...more...)",
          "Caused by java.lang.Exception: Aie!",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    at com.twitter.logging.FormatterTest$$.cycle(FormatterTest.scala:NNN)",
          "    (...more...)"))

      }
    }
  }
}
