package com.twitter.io.exp

import com.twitter.io.Buf
import com.twitter.util.{Activity, FuturePool, MockTimer, Time}
import java.io.{File, ByteArrayInputStream}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfter, FunSuite}
import scala.util.Random

@RunWith(classOf[JUnitRunner])
class ActivitySourceTest extends FunSuite with BeforeAndAfter {

  val ok: ActivitySource[String] = new ActivitySource[String] {
    def get(varName: String) = Activity.value(varName)
  }

  val failed: ActivitySource[Nothing] = new ActivitySource[Nothing] {
    def get(varName: String) = Activity.exception(new Exception(varName))
  }

  val tempFile: String = Random.alphanumeric.take(5).mkString

  def writeToTempFile(s: String): Unit = {
    val printer = new java.io.PrintWriter(new File(tempFile))
    try {
      printer.print(s)
    } finally {
      printer.close()
    }
  }

  def bufToString(buf: Buf): String = buf match {
    case Buf.Utf8(s) => s
    case _ => ""
  }

  before {
    writeToTempFile("foo bar")
  }

  after {
    new File(tempFile).delete()
  }

  test("ActivitySource.orElse") {
    val a = failed.orElse(ok)
    val b = ok.orElse(failed)

    assert("42" == a.get("42").sample())
    assert("42" == b.get("42").sample())
  }

  test("CachingActivitySource") {
    val cache = new CachingActivitySource[String](new ActivitySource[String] {
      def get(varName: String) = Activity.value(Random.alphanumeric.take(10).mkString)
    })

    val a = cache.get("a")
    assert(a == cache.get("a"))
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
          content = Some(bufToString(b))
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

  test("ClassLoaderActivitySource") {
    val classLoader = new ClassLoader() {
      override def getResourceAsStream(name: String) =
        new ByteArrayInputStream(name.getBytes("UTF-8"))
    }

    val loader = new ClassLoaderActivitySource(classLoader, FuturePool.immediatePool)
    val bufAct = loader.get("bar baz")

    assert("bar baz" == bufToString(bufAct.sample()))
  }
}
