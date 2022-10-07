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

import java.io._
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.{logging => javalog}
import com.twitter.conversions.StorageUnitOps._
import com.twitter.conversions.DurationOps._
import com.twitter.io.TempFolder
import com.twitter.util.Time
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.function.BiPredicate
import org.scalatest.funsuite.AnyFunSuite

class FileHandlerTest extends AnyFunSuite with TempFolder {
  def reader(filename: String): BufferedReader = {
    new BufferedReader(new InputStreamReader(new FileInputStream(new File(folderName, filename))))
  }

  def writer(filename: String): OutputStreamWriter = {
    new OutputStreamWriter(new FileOutputStream(new File(folderName, filename)), "UTF-8")
  }

  private[this] val matcher = (name: String) =>
    new BiPredicate[Path, BasicFileAttributes] {
      override def test(path: Path, attributes: BasicFileAttributes): Boolean = {
        path.toFile.getName.contains(name)
      }
    }

  // Looks for files that contain `name` in the given `dirToSearch`.
  // This method is used in place of setting the `user.dir`
  // system property to ensure JDK11 compatibility. See
  // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8202127
  private def filesContainingName(dirToSearch: String, name: String): Array[String] =
    Files
      .find(
        Paths.get(dirToSearch),
        1,
        matcher(name)
      ).toArray.map(_.toString)

  val record1 = new javalog.LogRecord(Level.INFO, "first post!")
  val record2 = new javalog.LogRecord(Level.INFO, "second post")

  test("FileHandler should honor append setting on logfiles") {
    withTempFolder {
      val f = writer("test.log")
      f.write("hello!\n")
      f.close()

      val handler = FileHandler(
        filename = folderName + "/test.log",
        rollPolicy = Policy.Hourly,
        append = true,
        formatter = BareFormatter
      ).apply()

      handler.publish(record1)

      val f2 = reader("test.log")
      assert(f2.readLine == "hello!")
    }

    withTempFolder {
      val f = writer("test.log")
      f.write("hello!\n")
      f.close()

      val handler = FileHandler(
        filename = folderName + "/test.log",
        rollPolicy = Policy.Hourly,
        append = false,
        formatter = BareFormatter
      ).apply()

      handler.publish(record1)

      val f2 = reader("test.log")
      assert(f2.readLine == "first post!")
    }
  }

  test("FileHandler should roll hourly logs on time") {
    withTempFolder {
      val handler = FileHandler(
        filename = folderName + "/test.log",
        rollPolicy = Policy.Hourly,
        append = true,
        formatter = BareFormatter
      ).apply()
      assert(handler.computeNextRollTime(1206769996722L).contains(1206770400000L))
      assert(handler.computeNextRollTime(1206770400000L).contains(1206774000000L))
      assert(handler.computeNextRollTime(1206774000001L).contains(1206777600000L))
    }
  }

  test("FileHandler should roll weekly logs on time") {
    withTempFolder {
      val handler = FileHandler(
        filename = folderName + "/test.log",
        rollPolicy = Policy.Weekly(Calendar.SUNDAY),
        append = true,
        formatter = new Formatter(timezone = Some("GMT-7:00"))
      ).apply()
      assert(handler.computeNextRollTime(1250354734000L).contains(1250406000000L))
      assert(handler.computeNextRollTime(1250404734000L).contains(1250406000000L))
      assert(handler.computeNextRollTime(1250406001000L).contains(1251010800000L))
      assert(handler.computeNextRollTime(1250486000000L).contains(1251010800000L))
      assert(handler.computeNextRollTime(1250496000000L).contains(1251010800000L))
    }
  }

  // verify that at the proper time, the log file rolls and resets.
  test("FileHandler should roll logs into new files") {
    withTempFolder {
      val handler =
        new FileHandler(folderName + "/test.log", Policy.Hourly, true, -1, BareFormatter, None)
      Time.withCurrentTimeFrozen { time =>
        handler.publish(record1)
        val date = new Date(Time.now.inMilliseconds)

        time.advance(1.hour)

        handler.publish(record2)
        handler.close()

        assert(reader("test-" + handler.timeSuffix(date) + ".log").readLine == "first post!")
        assert(reader("test.log").readLine == "second post")
      }
    }
  }

  test("FileHandler should keep no more than N log files around") {
    withTempFolder {
      assert(new File(folderName).list().length == 0)

      val handler = FileHandler(
        filename = folderName + "/test.log",
        rollPolicy = Policy.Hourly,
        append = true,
        rotateCount = 2,
        formatter = BareFormatter
      ).apply()

      handler.publish(record1)
      assert(new File(folderName).list().length == 1)
      handler.roll()

      handler.publish(record1)
      assert(new File(folderName).list().length == 2)
      handler.roll()

      handler.publish(record1)
      assert(new File(folderName).list().length == 2)
      handler.close()
    }
  }

  test("FileHandler ignores the target filename despite shorter filenames") {
    // even if the sort order puts the target filename before `rotateCount` other
    // files, it should not be removed
    withTempFolder {
      assert(new File(folderName).list().length == 0)
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
        assert(new File(folderName).list().length == 3)
      }

      // the target, 1 rotated file, and the short file should all remain
      (1 to 5).foreach { _ => flush() }
      val fileSet = new File(folderName).list().toSet
      assert(fileSet.contains(name))
      assert(fileSet.contains(namePrefix))
    }
  }

  test("FileHandler correctly handles relative paths") {

    val userDir = System.getProperty("user.dir")
    val filenamePrefix = UUID.randomUUID().toString

    try {
      val handler = FileHandler(
        filename = filenamePrefix + ".log", // Note relative path!
        rollPolicy = Policy.Hourly,
        append = true,
        rotateCount = 2,
        formatter = BareFormatter
      ).apply()

      handler.publish(record1)
      assert(filesContainingName(userDir, filenamePrefix).length == 1)
      handler.roll()

      handler.publish(record1)
      assert(filesContainingName(userDir, filenamePrefix).length == 2)
      handler.roll()

      handler.publish(record1)
      assert(filesContainingName(userDir, filenamePrefix).length == 2)
      handler.close()

    } finally {
      filesContainingName(userDir, filenamePrefix).foreach(f => new File(f).delete())
    }
  }

  test("FileHandler should roll log files based on max size") {
    withTempFolder {
      // roll the log on the 3rd write.
      val maxSize = record1.getMessage.length * 3 - 1

      assert(new File(folderName).list().length == 0)

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
        assert(new File(folderName).list().length == 1)
        time.advance(1.second)
        handler.publish(record1)
        assert(new File(folderName).list().length == 1)

        time.advance(1.second)
        handler.publish(record1)
        assert(new File(folderName).list().length == 2)
        time.advance(1.second)
        handler.publish(record1)
        assert(new File(folderName).list().length == 2)

        time.advance(1.second)
        handler.publish(record1)
        assert(new File(folderName).list().length == 3)
      }

      handler.close()
    }
  }

  /**
   * This test mimics the scenario that, if log file exists, FileHandler should pick up it's size
   * and roll over at the set limit, in order to prevent the file size from growing.
   * The test succeeds if the roll over takes place as desired else fails.
   */
  withTempFolder {
    test("FileHandler should rollover at set limit when test is restarted, execution") {
      val logLevel = Level.INFO
      val fileSizeInMegaBytes: Long = 1
      val record1 = new javalog.LogRecord(logLevel, "Sending bytes to fill up file")
      val filename = folderName + "/LogFileDir/testFileSize.log"
      val rollPolicy = Policy.MaxSize(fileSizeInMegaBytes.megabytes)
      val rotateCount = 8
      val append = true
      val formatter = BareFormatter

      val handler = FileHandler(filename, rollPolicy, append, rotateCount, formatter).apply()
      for (a <- 1 to 20000) {
        handler.publish(record1)
      }
      handler.close()

      val handler2 = FileHandler(filename, rollPolicy, append, rotateCount, formatter).apply()
      for (a <- 1 to 20000) {
        handler2.publish(record1)
      }
      handler2.close()

      def listLogFiles(dir: String): List[File] = {
        val d = new File(dir)
        if (d.exists && d.isDirectory) {
          d.listFiles.filter(_.isFile).toList
        } else {
          List[File]()
        }
      }

      val files = listLogFiles(folderName + "/LogFileDir")
      files.foreach { (f: File) =>
        val len = f.length().bytes
        if (len > fileSizeInMegaBytes.megabytes) {
          fail("Failed to roll over the log file")
        }
      }
    }
  }
}
