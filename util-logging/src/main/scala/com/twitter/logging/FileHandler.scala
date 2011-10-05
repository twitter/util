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

import java.io.{File, FileOutputStream, OutputStreamWriter, Writer}
import java.text.SimpleDateFormat
import java.util.{Calendar, Date, logging => javalog}
import com.twitter.util.{HandleSignal, StorageUnit, Time}
import config._

sealed abstract class Policy
object Policy {
  case object Never extends Policy
  case object Hourly extends Policy
  case object Daily extends Policy
  case class Weekly(dayOfWeek: Int) extends Policy
  case object SigHup extends Policy
  case class MaxSize(size: StorageUnit) extends Policy
}

/**
 * A log handler that writes log entries into a file, and rolls this file
 * at a requested interval (hourly, daily, or weekly).
 */
class FileHandler(val filename: String, rollPolicy: Policy, val append: Boolean, rotateCount: Int,
                  formatter: Formatter, level: Option[Level])
      extends Handler(formatter, level) {
  private var stream: Writer = null
  private var openTime: Long = 0
  private var nextRollTime: Option[Long] = Some(0)
  private var bytesWrittenToFile: Long = 0

  private val maxFileSize: Option[StorageUnit] = rollPolicy match {
    case Policy.MaxSize(size) => Some(size)
    case _ => None
  }


  openLog()

  if (rollPolicy == Policy.SigHup) {
    HandleSignal("HUP") { signal =>
      val oldStream = stream
      synchronized {
        stream = openWriter()
      }
      try {
        oldStream.close()
      } catch { case _ => () }
    }
  }

  def flush() = {
    stream.flush()
  }

  def close() = {
    flush()
    try {
      stream.close()
    } catch { case _ => () }
  }

  private def openWriter() = {
    val dir = new File(filename).getParentFile
    if ((dir ne null) && !dir.exists) dir.mkdirs
    new OutputStreamWriter(new FileOutputStream(filename, append), "UTF-8")
  }

  private def openLog() = {
    stream = openWriter()
    openTime = Time.now.inMilliseconds
    nextRollTime = computeNextRollTime(openTime)
    bytesWrittenToFile = 0
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
  private def removeOldFiles(filenamePrefix: String) = {
    if (rotateCount >= 0) {
      val filesInLogDir = new File(filename).getParentFile().listFiles()
      val rotatedFiles = filesInLogDir.filter(f => f.getName != filename &&
        f.getName.startsWith(new File(filenamePrefix).getName)).sortBy(_.getName)

      val toDeleteCount = math.max(0, rotatedFiles.size - rotateCount)
      rotatedFiles.take(toDeleteCount).foreach(_.delete())
    }
  }

  def roll() = synchronized {
    stream.close()
    val n = filename.lastIndexOf('.')
    val (filenamePrefix, filenameSuffix) =
      if (n > 0) (filename.substring(0, n), filename.substring(n)) else (filename, "")

    val newFilename = filenamePrefix + "-" + timeSuffix(new Date(openTime)) + filenameSuffix
    new File(filename).renameTo(new File(newFilename))
    openLog()
    removeOldFiles(filenamePrefix)
  }

  def publish(record: javalog.LogRecord) = {
    try {
      val formattedLine = getFormatter.format(record)
      val lineSizeBytes = formattedLine.getBytes("UTF-8").length

      if (nextRollTime.isDefined) {
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
        stream.write(formattedLine)
        stream.flush()
        bytesWrittenToFile += lineSizeBytes
      }
    } catch {
      case e =>
        System.err.println(Formatter.formatStackTrace(e, 30).mkString("\n"))
    }
  }
}
