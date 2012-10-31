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

class FileHandlerSpec extends SpecificationWithJUnit with TempFolder {
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

        val handler = FileHandler(
          filename = folderName + "/test.log",
          rollPolicy = Policy.Hourly,
          append = true,
          formatter = BareFormatter
        ).apply()

        handler.publish(record1)

        val f2 = reader("test.log")
        f2.readLine mustEqual "hello!"
      }

      withTempFolder {
        val f = writer("test.log")
        f.write("hello!\n")
        f.close

        val handler = FileHandler(
          filename = folderName + "/test.log",
          rollPolicy = Policy.Hourly,
          append = false,
          formatter = BareFormatter
        ).apply()

        handler.publish(record1)

        val f2 = reader("test.log")
        f2.readLine mustEqual "first post!"
      }
    }

    // /* Test is commented out according to http://jira.local.twitter.com/browse/REPLA-618 */
    //
    // "respond to a sighup to reopen a logfile with sun.misc" in {
    //   try {
    //     val signalClass = Class.forName("sun.misc.Signal")
    //     val sighup = signalClass.getConstructor(classOf[String]).newInstance("HUP").asInstanceOf[Object]
    //     val raiseMethod = signalClass.getMethod("raise", signalClass)

    //     withTempFolder {
    //       val handler = FileHandler(
    //         filename = folderName + "/new.log",
    //         rollPolicy = Policy.SigHup,
    //         append = true,
    //         formatter = BareFormatter
    //       ).apply()

    //       val logFile = new File(folderName, "new.log")
    //       logFile.renameTo(new File(folderName, "old.log"))
    //       handler.publish(record1)

    //       raiseMethod.invoke(null, sighup)

    //       val newLogFile = new File(folderName, "new.log")
    //       newLogFile.exists() must eventually(be_==(true))

    //       handler.publish(record2)

    //       val oldReader = reader("old.log")
    //       oldReader.readLine mustEqual "first post!"
    //       val newReader = reader("new.log")
    //       newReader.readLine mustEqual "second post"
    //     }
    //   } catch {
    //     case ex: ClassNotFoundException =>
    //   }
    // }

    "roll logs on time" in {
      "hourly" in {
        withTempFolder {
          val handler = FileHandler(
            filename = folderName + "/test.log",
            rollPolicy = Policy.Hourly,
            append = true,
            formatter = BareFormatter
          ).apply()
          handler.computeNextRollTime(1206769996722L) mustEqual Some(1206770400000L)
          handler.computeNextRollTime(1206770400000L) mustEqual Some(1206774000000L)
          handler.computeNextRollTime(1206774000001L) mustEqual Some(1206777600000L)
        }
      }

      "weekly" in {
        withTempFolder {
          val handler = FileHandler(
            filename = folderName + "/test.log",
            rollPolicy = Policy.Weekly(Calendar.SUNDAY),
            append = true,
            formatter = new Formatter(timezone = Some("GMT-7:00"))
          ).apply()
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

        val handler = FileHandler(
          filename = folderName + "/test.log",
          rollPolicy = Policy.Hourly,
          append = true,
          rotateCount = 2,
          formatter = BareFormatter
        ).apply()

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

    "ignores the target filename despite shorter filenames" in {
      // even if the sort order puts the target filename before `rotateCount` other
      // files, it should not be removed
      withTempFolder {
        new File(folderName).list().length mustEqual 0
        val namePrefix = "test"
        val name = namePrefix + ".log"

        val handler = FileHandler(
          filename = folderName + "/" + name,
          rollPolicy = Policy.Hourly,
          append = true,
          rotateCount = 1,
          formatter = BareFormatter
        ).apply()

        // create a file without the '.log' suffix, which will sort before the target
        new File(folderName, namePrefix).createNewFile()

        def flush() = {
          handler.publish(record1)
          handler.roll()
          new File(folderName).list().length mustEqual 3
        }

        // the target, 1 rotated file, and the short file should all remain
        (1 to 5).foreach { _ => flush() }
        new File(folderName).list().toSet must beLike {
          case x => x.contains(name) && x.contains(namePrefix)
        }
      }
    }

    "correctly handles relative paths" in {
      withTempFolder {
        // user.dir will be replaced with the temp folder,
        // and will be restored when the test is complete
        val wdir = System.getProperty("user.dir")

        try {
          System.setProperty("user.dir", folderName)

          val handler = FileHandler(
            filename = "test.log", // Note relative path!
            rollPolicy = Policy.Hourly,
            append = true,
            rotateCount = 2,
            formatter = BareFormatter
          ).apply()

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
        finally {
          // restore user.dir to its original configuration
          System.setProperty("user.dir", wdir)
        }

      }
    }

    "roll log files based on max size" in {
      withTempFolder {
        // roll the log on the 3rd write.
        val maxSize = record1.getMessage.length * 3 - 1

        new File(folderName).list().length mustEqual 0

        val handler = FileHandler(
          filename = folderName + "/test.log",
          rollPolicy = Policy.MaxSize(maxSize.bytes),
          append = true,
          formatter = BareFormatter
        ).apply()

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
