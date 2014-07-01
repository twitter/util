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

import java.io.{File, FileOutputStream, FilenameFilter, OutputStream}
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, logging => javalog}

import com.twitter.util.{HandleSignal, Return, StorageUnit, Time, Try}

sealed abstract class Policy
object Policy {
  case object Never extends Policy
  case object Hourly extends Policy
  case object Daily extends Policy
  case class Weekly(dayOfWeek: Int) extends Policy
  case object SigHup extends Policy
  case class MaxSize(size: StorageUnit) extends Policy


  private[this] val singletonPolicyNames: Map[String, Policy] =
    Map("never" -> Never, "hourly" -> Hourly, "daily" -> Daily, "sighup" -> SigHup)

  // Regex object that matches "Weekly(n)" and extracts the `dayOfWeek` number.
  private[this] val weeklyRegex = """(?i)weekly\(([1-7]+)\)""".r

  /**
   * Parse a string into a Policy object. Parsing rules are as follows:
   *
   * - Case-insensitive names of singleton Policy objects (e.g. Never, Hourly,
   *   Daily) are parsed into their corresponding objects.
   * - "Weekly(n)" is parsed into `Weekly` objects with `n` as the day-of-week
   *   integer.
   * - util-style data size strings (e.g. 3.megabytes, 1.gigabyte) are
   *   parsed into `StorageUnit` objects and used to produce `MaxSize` policies.
   *   See `StorageUnit.parse(String)` for more details.
   */
  def parse(s: String): Policy =
    (s, singletonPolicyNames.get(s.toLowerCase), Try(StorageUnit.parse(s.toLowerCase))) match {
      case (weeklyRegex(dayOfWeek), _, _) => Weekly(dayOfWeek.toInt)
      case (_, Some(singleton), _) => singleton
      case (_, _, Return(storageUnit)) => MaxSize(storageUnit)
      case _ => throw new Exception("Invalid log roll policy: " + s)
    }
}

object FileHandler {
  val UTF8 = Charset.forName("UTF-8")

  /**
   * Generates a HandlerFactory that returns a FileHandler
   *
   * @param filename
   * Filename to log to.
   *
   * @param rollPolicy
   * When to roll the logfile.
   *
   * @param append
   * Append to an existing logfile, or truncate it?
   *
   * @param rotateCount
   * How many rotated logfiles to keep around, maximum. -1 means to keep them all.
   */
  def apply(
    filename: String,
    rollPolicy: Policy = Policy.Never,
    append: Boolean = true,
    rotateCount: Int = -1,
    formatter: Formatter = new Formatter(),
    level: Option[Level] = None
  ) = () => new FileHandler(filename, rollPolicy, append, rotateCount, formatter, level)
}

/**
 * A log handler that writes log entries into a file, and rolls this file
 * at a requested interval (hourly, daily, or weekly).
 */
class FileHandler(
    path: String,
    rollPolicy: Policy,
    val append: Boolean,
    rotateCount: Int,
    formatter: Formatter,
    level: Option[Level])
  extends Handler(formatter, level) {

  // This converts relative paths to absolute paths, as expected
  val (filename, name) = {
    val f = new File(path)
    (f.getAbsolutePath, f.getName)
  }
  val (filenamePrefix, filenameSuffix) = {
    val n = filename.lastIndexOf('.')
    if (n > 0) {
      (filename.substring(0, n), filename.substring(n))
    } else {
      (filename, "")
    }
  }

  // Thread-safety is guarded by synchronized on this
  private var stream: OutputStream = null
  @volatile private var openTime: Long = 0
  // Thread-safety is guarded by synchronized on this
  private var nextRollTime: Option[Long] = None
  // Thread-safety is guarded by synchronized on this
  private var bytesWrittenToFile: Long = 0

  private val maxFileSize: Option[StorageUnit] = rollPolicy match {
    case Policy.MaxSize(size) => Some(size)
    case _ => None
  }

  openLog()

  // If nextRollTime.isDefined by openLog(), then it will always remain isDefined.
  // This allows us to avoid volatile reads in the publish method.
  private val examineRollTime = nextRollTime.isDefined

  if (rollPolicy == Policy.SigHup) {
    HandleSignal("HUP") { signal =>
      val oldStream = stream
      synchronized {
        stream = openStream()
      }
      try {
        oldStream.close()
      } catch {
        case e: Throwable => handleThrowable(e)
      }
    }
  }

  def flush() {
    synchronized {
      stream.flush()
    }
  }

  def close() {
    synchronized {
      flush()
      try {
        stream.close()
      } catch {
        case e: Throwable => handleThrowable(e)
      }
    }
  }

  private def openStream(): OutputStream = {
    val dir = new File(filename).getParentFile
    if ((dir ne null) && !dir.exists) dir.mkdirs
    new FileOutputStream(filename, append)
  }

  private def openLog() {
    synchronized {
      stream = openStream()
      openTime = Time.now.inMilliseconds
      nextRollTime = computeNextRollTime(openTime)
      bytesWrittenToFile = 0
    }
  }

  /**
   * Compute the suffix for a rolled logfile, based on the roll policy.
   */
  def timeSuffix(date: Date) = {
    val dateFormat = rollPolicy match {
      case Policy.Never => new SimpleDateFormat("yyyy")
      case Policy.SigHup => new SimpleDateFormat("yyyy")
      case Policy.Hourly => new SimpleDateFormat("yyyyMMdd-HH")
      case Policy.Daily => new SimpleDateFormat("yyyyMMdd")
      case Policy.Weekly(_) => new SimpleDateFormat("yyyyMMdd")
      case Policy.MaxSize(_) => new SimpleDateFormat("yyyyMMdd-HHmmss")
    }
    dateFormat.setCalendar(formatter.calendar)
    dateFormat.format(date)
  }

  /**
   * Return the time (in absolute milliseconds) of the next desired
   * logfile roll.
   */
  def computeNextRollTime(now: Long): Option[Long] = {
    lazy val next = {
      val n = formatter.calendar.clone.asInstanceOf[Calendar]
      n.setTimeInMillis(now)
      n.set(Calendar.MILLISECOND, 0)
      n.set(Calendar.SECOND, 0)
      n.set(Calendar.MINUTE, 0)
      n
    }


    val rv = rollPolicy match {
      case Policy.MaxSize(_) | Policy.Never | Policy.SigHup => None
      case Policy.Hourly => {
        next.add(Calendar.HOUR_OF_DAY, 1)
        Some(next)
      }
      case Policy.Daily => {
        next.set(Calendar.HOUR_OF_DAY, 0)
        next.add(Calendar.DAY_OF_MONTH, 1)
        Some(next)
      }
      case Policy.Weekly(weekday) => {
        next.set(Calendar.HOUR_OF_DAY, 0)
        do {
          next.add(Calendar.DAY_OF_MONTH, 1)
        } while (next.get(Calendar.DAY_OF_WEEK) != weekday)
        Some(next)
      }
    }

    rv map { _.getTimeInMillis }
  }

  /**
   * Delete files when "too many" have accumulated.
   * This duplicates logrotate's "rotate count" option.
   */
  private def removeOldFiles() {
    if (rotateCount >= 0) {
      // collect files which are not `filename`, but which share the prefix/suffix
      val prefixName = new File(filenamePrefix).getName
      val rotatedFiles =
        new File(filename).getParentFile().listFiles(
          new FilenameFilter {
            def accept(f: File, fname: String): Boolean =
              fname != name && fname.startsWith(prefixName) && fname.endsWith(filenameSuffix)
          }
        ).sortBy(_.getName)

      val toDeleteCount = math.max(0, rotatedFiles.size - rotateCount)
      rotatedFiles.take(toDeleteCount).foreach(_.delete())
    }
  }

  def roll() = synchronized {
    stream.close()
    val newFilename = filenamePrefix + "-" + timeSuffix(new Date(openTime)) + filenameSuffix
    new File(filename).renameTo(new File(newFilename))
    openLog()
    removeOldFiles()
  }

  def publish(record: javalog.LogRecord) {
    try {
      val formattedLine = getFormatter.format(record)
      val formattedBytes = formattedLine.getBytes(FileHandler.UTF8)
      val lineSizeBytes = formattedBytes.length

      if (examineRollTime) {
        // Only allow a single thread at a time to do a roll
        synchronized {
          nextRollTime foreach { time =>
            if (Time.now.inMilliseconds > time) roll()
          }
        }
      }

      maxFileSize foreach { size =>
        synchronized {
          if (bytesWrittenToFile + lineSizeBytes > size.bytes) roll()
        }
      }

      synchronized {
        stream.write(formattedBytes)
        stream.flush()
        bytesWrittenToFile += lineSizeBytes
      }
    } catch {
      case e: Throwable => handleThrowable(e)
    }
  }

  private def handleThrowable(e: Throwable) {
    System.err.println(Formatter.formatStackTrace(e, 30).mkString("\n"))
  }

}
