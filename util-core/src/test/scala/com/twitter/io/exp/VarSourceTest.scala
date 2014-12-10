package com.twitter.io.exp

import java.io.{File, ByteArrayInputStream}
import scala.util.Random
import org.scalatest.{BeforeAndAfter, FunSuite}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.twitter.io.Buf
import com.twitter.util.{FuturePool, MockTimer, Time, Var}


@RunWith(classOf[JUnitRunner])
class VarSourceTest extends FunSuite with BeforeAndAfter {

  val ok = new VarSource[String] {
    def get(varName: String) = Var.value(VarSource.Ok(varName))
  }

  val failed = new VarSource[Nothing] {
    def get(varName: String) = Var.value(VarSource.Failed(new Exception(varName)))
  }

  val tempFile = Random.alphanumeric.take(5).mkString

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

  test("VarSource.Result") {
    val ok = VarSource.Ok("ok")
    val failed = VarSource.Failed(new Exception())

    assert(ok.isOk)
    assert(failed.isFailed)
    assert(ok.get === "ok")
    intercept[NoSuchElementException] {
      failed.get
    }

    assert(ok.map(_ => 42).get === 42)
    assert(failed.map(_ => "42").isFailed)
    assert(ok.flatMap(_ => VarSource.Pending).isPending)
  }

  test("FailoverVarSource") {
    val a = new FailoverVarSource[String](failed, ok)
    val b = new FailoverVarSource[String](ok, failed)

    assert(a.get("42").sample() === VarSource.Ok("42"))
    assert(b.get("42").sample() === VarSource.Ok("42"))
  }

  test("CachingVarSource") {
    val cache = new CachingVarSource[String](new VarSource[String] {
      def get(varName: String) = Var.value(VarSource.Ok(Random.alphanumeric.take(10).mkString))
    })

    val a = cache.get("a")
    assert(a === cache.get("a"))
  }

  test("FilePollingVarSource") {
    Time.withCurrentTimeFrozen { timeControl =>
      import com.twitter.conversions.time._
      implicit val timer = new MockTimer
      val file = new FilePollingVarSource(1.microsecond, FuturePool.immediatePool)
      val buf = file.get(tempFile)

      var content: Option[String] = None

      val listen = buf.changes.respond {
        case VarSource.Ok(b) =>
          content = Some(bufToString(b))
        case _ =>
          content = None
      }

      timeControl.advance(2.microsecond)
      timer.tick()
      assert(content === Some("foo bar"))

      writeToTempFile("foo baz")

      timeControl.advance(2.microsecond)
      timer.tick()
      assert(content === Some("foo baz"))

      listen.close()
    }
  }

  test("ClassLoaderVarSource") {
    val classLoader = new ClassLoader() {
      override def getResourceAsStream(name: String) =
        new ByteArrayInputStream(name.getBytes("UTF-8"))
    }

    val loader = new ClassLoaderVarSource(classLoader, FuturePool.immediatePool)
    val buf: Var[VarSource.Result[Buf]] = loader.get("bar baz")

    val mappedBuf = buf.map(_ => VarSource.Ok(Buf.Empty))

    assert(buf.sample().isOk)
    assert(mappedBuf.sample().isOk)

    assert(bufToString(buf.sample().get) === "bar baz")
  }
}
