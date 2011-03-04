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
import org.specs.Specification
import com.twitter.conversions.string._
import config._

class FormatterSpec extends Specification {
  val utcConfig = new FormatterConfig {
    timezone = "UTC"
  }
  val fullPackageConfig = new FormatterConfig {
    timezone = "UTC"
    useFullPackageNames = true
  }
  val prefixConfig = new FormatterConfig {
    timezone = "UTC"
    prefix = "%2$s <HH:mm> %1$.4s "
  }
  val truncateConfig = new FormatterConfig {
    timezone = "UTC"
    truncateAt = 30
  }

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
      BasicFormatter.formatPrefix(Level.ERROR, "20080329-05:53:16.722", "(root)") mustEqual
        "ERR [20080329-05:53:16.722] (root): "
      BasicFormatter.formatPrefix(Level.DEBUG, "20080329-05:53:16.722", "(root)") mustEqual
        "DEB [20080329-05:53:16.722] (root): "
      BasicFormatter.formatPrefix(Level.WARNING, "20080329-05:53:16.722", "(root)") mustEqual
        "WAR [20080329-05:53:16.722] (root): "
    }

    "format text" in {
      val record = new javalog.LogRecord(Level.ERROR, "error %s")
      BasicFormatter.formatText(record) mustEqual "error %s"
      record.setParameters(Array("123"))
      BasicFormatter.formatText(record) mustEqual "error 123"
    }

    "format a timestamp" in {
      val formatter = utcConfig()
      formatter.format(record1) mustEqual "ERR [20080329-05:53:16.722] jobs: boo.\n"
    }

    "do lazy message evaluation" in {
      var callCount = 0
      def getSideEffect = {
        callCount += 1
        "ok"
      }

      val record = new LazyLogRecord(Level.DEBUG, "this is " + getSideEffect)

      callCount mustEqual 0
      BasicFormatter.formatText(record) mustEqual "this is ok"
      callCount mustEqual 1
      BasicFormatter.formatText(record) mustEqual "this is ok"
      callCount mustEqual 1
    }

    "format package names" in {
      utcConfig().format(record1) mustEqual "ERR [20080329-05:53:16.722] jobs: boo.\n"
      fullPackageConfig().format(record1) mustEqual
        "ERR [20080329-05:53:16.722] com.example.jobs: boo.\n"
    }

    "handle other prefixes" in {
      prefixConfig().format(record2) mustEqual "jobs 05:53 DEBU useless info.\n"
    }

    "truncate line" in {
      truncateConfig().format(record3) mustEqual
        "CRI [20080329-05:53:16.722] whiskey: Something terrible happened th...\n"
    }

    "write stack traces" in {
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
        Formatter.formatStackTrace(exception, 5).map { scrub(_) } mustEqual List(
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
        Formatter.formatStackTrace(exception, 2).map { scrub(_) } mustEqual List(
          "    at com.twitter.logging.FormatterSpec$$.cycle2(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.apply(FormatterSpec.scala:NNN)",
          "    (...more...)",
          "Caused by java.lang.Exception: Aie!",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    at com.twitter.logging.FormatterSpec$$.cycle(FormatterSpec.scala:NNN)",
          "    (...more...)")
      }
    }
  }
}
