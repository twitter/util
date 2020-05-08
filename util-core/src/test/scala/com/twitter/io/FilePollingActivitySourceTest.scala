package com.twitter.io

import com.twitter.util.{Activity, FuturePool, MockTimer, Time}
import java.io.File
import org.scalatest.{BeforeAndAfter, FunSuite}
import scala.util.Random

class FilePollingActivitySourceTest extends FunSuite with BeforeAndAfter {

  def writeToTempFile(s: String): Unit = {
    val printer = new java.io.PrintWriter(new File(tempFile))
    try {
      printer.print(s)
    } finally {
      printer.close()
    }
  }

  def deleteTempFile(): Unit =
    new File(tempFile).delete()

  val tempFile: String = Random.alphanumeric.take(5).mkString

  before {
    writeToTempFile("foo bar")
  }

  after {
    deleteTempFile()
  }

  test("FilePollingActivitySource") {
    Time.withCurrentTimeFrozen { timeControl =>
      import com.twitter.conversions.DurationOps._
      implicit val timer = new MockTimer
      val file = new FilePollingActivitySource(1.microsecond, FuturePool.immediatePool)
      val buf = file.get(tempFile)

      var content: Option[String] = None

      val listen = buf.run.changes.respond {
        case Activity.Ok(b) =>
          content = Some(ActivitySourceTest.bufToString(b))
        case _ =>
          content = None
      }

      timeControl.advance(2.microsecond)
      timer.tick()
      assert(content == Some("foo bar"))

      writeToTempFile("foo baz")

      timeControl.advance(2.microsecond)
      timer.tick()
      assert(content == Some("foo baz"))

      listen.close()
    }
  }

}
