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

import java.io._
import java.util.{Calendar, Date}
import java.util.{logging => javalog}
import com.twitter.conversions.storage._
import com.twitter.conversions.string._
import com.twitter.conversions.time._
import com.twitter.util.{TempFolder, Time}
import org.specs.SpecificationWithJUnit
import config._

class FileHandlerSpec extends SpecificationWithJUnit with TempFolder {
  def config(_filename: String, _policy: Policy, _append: Boolean, _rotateCount: Int,
             _formatter: FormatterConfig): FileHandlerConfig = {
    new FileHandlerConfig {
      filename = folderName + "/" + _filename
      formatter = _formatter
      roll = _policy
      append = _append
      rotateCount = _rotateCount
    }
  }

  def reader(filename: String) = {
    new BufferedReader(new InputStreamReader(new FileInputStream(new File(folderName, filename))))
  }

  def writer(filename: String) = {
    new OutputStreamWriter(new FileOutputStream(new File(folderName, filename)), "UTF-8")
  }

  "FileHandler" should {
    val record1 = new javalog.LogRecord(Level.INFO, "first post!")
    val record2 = new javalog.LogRecord(Level.INFO, "second post")

    "honor append setting on logfiles" in {
      withTempFolder {
        val f = writer("test.log")
        f.write("hello!\n")
        f.close

        val handler = config("test.log", Policy.Hourly, true, -1, BareFormatterConfig)()
        handler.publish(record1)

        val f2 = reader("test.log")
        f2.readLine mustEqual "hello!"
      }

      withTempFolder {
        val f = writer("test.log")
        f.write("hello!\n")
        f.close

        val handler = config("test.log", Policy.Hourly, false, -1, BareFormatterConfig)()
        handler.publish(record1)

        val f2 = reader("test.log")
        f2.readLine mustEqual "first post!"
      }
    }

    "respond to a sighup to reopen a logfile with sun.misc" in {
      try {
        val signalClass = Class.forName("sun.misc.Signal")
        val sighup = signalClass.getConstructor(classOf[String]).newInstance("HUP").asInstanceOf[Object]
        val raiseMethod = signalClass.getMethod("raise", signalClass)

        withTempFolder {
          val handler = config("new.log", Policy.SigHup, true, -1, BareFormatterConfig)()

          val logFile = new File(folderName, "new.log")
          logFile.renameTo(new File(folderName, "old.log"))
          handler.publish(record1)

          raiseMethod.invoke(null, sighup)

          val newLogFile = new File(folderName, "new.log")
          newLogFile.exists() must eventually(be_==(true))

          handler.publish(record2)

          val oldReader = reader("old.log")
          oldReader.readLine mustEqual "first post!"
          val newReader = reader("new.log")
          newReader.readLine mustEqual "second post"
        }
      } catch {
        case ex: ClassNotFoundException =>
      }
    }

    "roll logs on time" in {
      "hourly" in {
        withTempFolder {
          val handler = config("test.log", Policy.Hourly, true, -1, BareFormatterConfig)()
          handler.computeNextRollTime(1206769996722L) mustEqual Some(1206770400000L)
          handler.computeNextRollTime(1206770400000L) mustEqual Some(1206774000000L)
          handler.computeNextRollTime(1206774000001L) mustEqual Some(1206777600000L)
        }
      }

      "weekly" in {
        withTempFolder {
          val formatter = new FormatterConfig { timezone = "GMT-7:00" }
          val handler = config("test.log", Policy.Weekly(Calendar.SUNDAY), true, -1, formatter)()
          handler.computeNextRollTime(1250354734000L) mustEqual Some(1250406000000L)
          handler.computeNextRollTime(1250404734000L) mustEqual Some(1250406000000L)
          handler.computeNextRollTime(1250406001000L) mustEqual Some(1251010800000L)
          handler.computeNextRollTime(1250486000000L) mustEqual Some(1251010800000L)
          handler.computeNextRollTime(1250496000000L) mustEqual Some(1251010800000L)
        }
      }
    }

    // verify that at the proper time, the log file rolls and resets.
    "roll logs into new files" in {
      withTempFolder {
        val handler = new FileHandler(folderName + "/test.log", Policy.Hourly, true, -1, BareFormatter, None)
        Time.withCurrentTimeFrozen { time =>
          handler.publish(record1)
          val date = new Date(Time.now.inMilliseconds)

          time.advance(1.hour)

          handler.publish(record2)
          handler.close()

          reader("test-" + handler.timeSuffix(date) + ".log").readLine mustEqual "first post!"
          reader("test.log").readLine mustEqual "second post"
        }
      }
    }

    "keep no more than N log files around" in {
      withTempFolder {
        new File(folderName).list().length mustEqual 0

        val handler = config("test.log", Policy.Hourly, true, 2, BareFormatterConfig)()
        handler.publish(record1)
        new File(folderName).list().length mustEqual 1
        handler.roll()

        handler.publish(record1)
        new File(folderName).list().length mustEqual 2
        handler.roll()

        handler.publish(record1)
        new File(folderName).list().length mustEqual 2
        handler.close()
      }
    }

    "roll log files based on max size" in {
      withTempFolder {
        // roll the log on the 3rd write.
        val maxSize = record1.getMessage.length * 3 - 1

        new File(folderName).list().length mustEqual 0

        val handler = config("test.log", Policy.MaxSize(maxSize.bytes), true, -1, BareFormatterConfig)()

        // move time forward so the rotated logfiles will have distinct names.
        Time.withCurrentTimeFrozen { time =>
          time.advance(1.second)
          handler.publish(record1)
          new File(folderName).list().length mustEqual 1
          time.advance(1.second)
          handler.publish(record1)
          new File(folderName).list().length mustEqual 1

          time.advance(1.second)
          handler.publish(record1)
          new File(folderName).list().length mustEqual 2
          time.advance(1.second)
          handler.publish(record1)
          new File(folderName).list().length mustEqual 2

          time.advance(1.second)
          handler.publish(record1)
          new File(folderName).list().length mustEqual 3
        }

        handler.close()
      }
    }
  }
}
