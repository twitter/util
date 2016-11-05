package com.twitter.util

import com.twitter.io.TempFile
import java.io.{File, FileWriter}
import org.junit.runner.RunWith
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import scala.io.Source
import scala.language.reflectiveCalls
import scala.reflect.internal.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.{AbstractReporter, Reporter}

@RunWith(classOf[JUnitRunner])
class EvalTest extends WordSpec {
  "Evaluator" should {

    "apply('expression')" in {
      assert((new Eval).apply[Int]("1 + 1") == 2)
    }

    "apply(new File(...))" in {
      assert((new Eval).apply[Int](TempFile.fromResourcePath("/OnePlusOne.scala")) == 2)
    }

    "apply(new File(...), new File(...))" in {
      val derived = (new Eval).apply[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/Derived.scala"))
      assert(derived() == "hello")
    }

    "apply(new File(...) with a dash in the name with target" in {
      val f = File.createTempFile("eval", "target")
      f.delete()
      f.mkdir()
      val e = new Eval(Some(f))
      val sourceFile = TempFile.fromResourcePath("/file-with-dash.scala")
      val res: String = e(sourceFile)
      assert(res == "hello")
      val className = e.fileToClassName(sourceFile)
      val processedSource = e.sourceForString(Source.fromFile(sourceFile).getLines().mkString("\n"))
      val fullClassName = "Evaluator__%s_%s.class".format(
        className, e.uniqueId(processedSource, None))
      val targetFileName = f.getAbsolutePath() + File.separator + fullClassName
      val targetFile = new File(targetFileName)
      assert(targetFile.exists)
    }

    "apply(new File(...) with target" in {
      val f = File.createTempFile("eval", "target")
      f.delete()
      f.mkdir()
      val e = new Eval(Some(f))
      val sourceFile = TempFile.fromResourcePath("/OnePlusOne.scala")
      val res: Int = e(sourceFile)
      assert(res == 2)

      // make sure it created a class file with the expected name
      val className = e.fileToClassName(sourceFile)
      val processedSource = e.sourceForString(Source.fromFile(sourceFile).getLines().mkString("\n"))
      val fullClassName = "Evaluator__%s_%s.class".format(
        className, e.uniqueId(processedSource, None))
      val targetFileName = f.getAbsolutePath() + File.separator + fullClassName
      val targetFile = new File(targetFileName)
      assert(targetFile.exists)
      val targetMod = targetFile.lastModified

      // eval again, make sure it works
      val res2: Int = e(sourceFile)
      // and make sure it didn't create a new file (1 + checksum)
      assert(f.listFiles.length == 2)
      // and make sure it didn't update the file
      val targetFile2 = new File(targetFileName)
      assert(targetFile2.lastModified == targetMod)

      // touch source, ensure no-recompile (checksum hasn't changed)
      sourceFile.setLastModified(System.currentTimeMillis())
      val res3: Int = e(sourceFile)
      assert(res3 == 2)
      // and make sure it didn't create a different file
      assert(f.listFiles.length == 2)
      // and make sure it updated the file
      val targetFile3 = new File(targetFileName)
      assert(targetFile3.lastModified == targetMod)

      // append a newline, altering checksum, verify recompile
      val writer = new FileWriter(sourceFile)
      writer.write("//a comment\n2\n")
      writer.close()
      val res4: Int = e(sourceFile)
      assert(res4 == 2)
      // and make sure it created a new file
      val targetFile4 = new File(targetFileName)
      assert(!targetFile4.exists)
    }

    "apply(InputStream)" in {
      assert((new Eval).apply[Int](getClass.getResourceAsStream("/OnePlusOne.scala")) == 2)
    }

    "uses deprecated" in {
      val deprecated = (new Eval).apply[() => String](
        TempFile.fromResourcePath("/Deprecated.scala"))
      assert(deprecated() == "hello")
    }

    "inPlace('expression')" in {
      // Old object API works
      Eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      assert(Eval.inPlace[Int]("Doubler(2)") == 4)
      assert(Eval.inPlace[Int]("Doubler(14)") == 28)
      // New class API fails
      // val eval = new Eval
      // eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      // assert(eval.inPlace[Int]("Doubler(2)") == 4)
      // assert(eval.inPlace[Int]("Doubler(14)") == 28)
    }

    "check" in {
      (new Eval).check("23")
      intercept[Eval.CompilerException] {
        (new Eval).check("invalid")
      }
    }

    "#include" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/DerivedWithInclude.scala"))
      assert(derived() == "hello")
      assert(derived.toString == "hello, joe")
    }

    "recursive #include" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/IncludeInclude.scala"))
      assert(derived() == "hello")
      assert(derived.toString == "hello, joe; hello, joe")
    }

    "toSource returns post-processed code" in {
      val derived = Eval.toSource(TempFile.fromResourcePath("/DerivedWithInclude.scala"))
      assert(derived.contains("hello, joe"))
      assert(derived.contains("new Base"))
    }

    "throws a compilation error when Ruby is #included" in {
      intercept[Throwable] {
        Eval[() => String](
          TempFile.fromResourcePath("RubyInclude.scala")
        )
      }
    }

    "clean class names" in {
      val e = new Eval()
      // regular old scala file
      assert(e.fileToClassName(new File("foo.scala")) == "foo")
      // without an extension
      assert(e.fileToClassName(new File("foo")) == "foo")
      // with lots o dots
      assert(e.fileToClassName(new File("foo.bar.baz")) == "foo$2ebar")
      // with dashes
      assert(e.fileToClassName(new File("foo-bar-baz.scala")) == "foo$2dbar$2dbaz")
      // with crazy things
      assert(e.fileToClassName(new File("foo$! -@@@")) == "foo$24$21$20$2d$40$40$40")
    }

    "allow custom error reporting" when {
      class Ctx {
        val eval = new Eval {
          @volatile var errors: Seq[(String, String)] = Nil

          override lazy val compilerMessageHandler: Option[Reporter] = Some(new AbstractReporter {
            override val settings: Settings = compilerSettings
            override def displayPrompt(): Unit = ()
            override def display(pos: Position, msg: String, severity: this.type#Severity): Unit = {
              errors = errors :+ ((msg, severity.toString))
            }
            override def reset() = {
              super.reset()
              errors = Nil
            }
          })
        }
      }

      "not report errors on success" in {
        val ctx = new Ctx
        import ctx._

        assert(eval[Int]("val a = 3; val b = 2; a + b", true) == 5)
        assert(eval.errors.isEmpty)
      }

      "report errors on bad code" in {
        val ctx = new Ctx
        import ctx._

        intercept[Throwable] {
          eval[Int]("val a = 3; val b = q; a + b", true)
        }
        assert(eval.errors.nonEmpty)
      }

      "reset state between invocations" in {
        val ctx = new Ctx
        import ctx._

        intercept[Throwable] {
          eval[Int]("val a = 3; val b = q; a + b", true)
        }
        assert(eval.errors.nonEmpty)
        assert(eval[Int]("val d = 3; val e = 2; d + e", true) == 5)
        assert(eval.errors.isEmpty)
      }

      "reset reporter between inPlace invocations" in {
        val ctx = new Ctx
        import ctx._

        intercept[Throwable] {
          eval.inPlace[Int]("val a = 3; val b = q; a + b")
        }
        assert(eval.errors.nonEmpty)
        assert(eval.inPlace[Int]("val d = 3; val e = 2; d + e") == 5)
        assert(eval.errors.isEmpty)
      }

      "reporter should be reset between checks, but loaded class should remain" in {
        val ctx = new Ctx
        import ctx._

        // compile and load compiled class
        eval.compile("class A()")

        intercept[Throwable] {
          eval.check("new B()")
        }
        assert(eval.errors.nonEmpty)

        eval.check("new A()")
        assert(eval.errors.isEmpty)
      }
    }

    "eval with overriden classloader" in {
      val eval = new Eval{
        override def classLoader = this.getClass.getClassLoader
      }
      assert(eval.apply[Int]("1 + 1") == 2)
    }
  }
}
