package com.twitter.util

import org.specs.SpecificationWithJUnit
import java.io.{File, FileOutputStream, FileWriter}
import scala.io.Source

import com.twitter.io.TempFile
import scala.tools.nsc.reporters.{AbstractReporter, Reporter}
import scala.tools.nsc.Settings
import scala.tools.nsc.util.Position

class EvalSpec extends SpecificationWithJUnit {
  "Evaluator" should {

    "apply('expression')" in {
      (new Eval).apply[Int]("1 + 1") mustEqual 2
    }

    "apply(new File(...))" in {
      (new Eval).apply[Int](TempFile.fromResourcePath("/OnePlusOne.scala")) mustEqual 2
    }

    "apply(new File(...), new File(...))" in {
      val derived = (new Eval).apply[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/Derived.scala"))
      derived() mustEqual "hello"
    }

    "apply(new File(...) with a dash in the name with target" in {
      val f = File.createTempFile("eval", "target")
      f.delete()
      f.mkdir()
      val e = new Eval(Some(f))
      val sourceFile = TempFile.fromResourcePath("/file-with-dash.scala")
      val res: String = e(sourceFile)
      res mustEqual "hello"
      val className = e.fileToClassName(sourceFile)
      val processedSource = e.sourceForString(Source.fromFile(sourceFile).getLines.mkString("\n"))
      val fullClassName = "Evaluator__%s_%s.class".format(
        className, e.uniqueId(processedSource, None))
      val targetFileName = f.getAbsolutePath() + File.separator + fullClassName
      val targetFile = new File(targetFileName)
      targetFile.exists must be_==(true)
    }

    "apply(new File(...) with target" in {
      val f = File.createTempFile("eval", "target")
      f.delete()
      f.mkdir()
      val e = new Eval(Some(f))
      val sourceFile = TempFile.fromResourcePath("/OnePlusOne.scala")
      val res: Int = e(sourceFile)
      res mustEqual 2

      // make sure it created a class file with the expected name
      val className = e.fileToClassName(sourceFile)
      val processedSource = e.sourceForString(Source.fromFile(sourceFile).getLines.mkString("\n"))
      val fullClassName = "Evaluator__%s_%s.class".format(
        className, e.uniqueId(processedSource, None))
      val targetFileName = f.getAbsolutePath() + File.separator + fullClassName
      val targetFile = new File(targetFileName)
      targetFile.exists must be_==(true)
      val targetMod = targetFile.lastModified

      // eval again, make sure it works
      val res2: Int = e(sourceFile)
      // and make sure it didn't create a new file (1 + checksum)
      f.listFiles.length mustEqual 2
      // and make sure it didn't update the file
      val targetFile2 = new File(targetFileName)
      targetFile2.lastModified mustEqual targetMod

      // touch source, ensure no-recompile (checksum hasn't changed)
      sourceFile.setLastModified(System.currentTimeMillis())
      val res3: Int = e(sourceFile)
      res3 mustEqual 2
      // and make sure it didn't create a different file
      f.listFiles.length mustEqual 2
      // and make sure it updated the file
      val targetFile3 = new File(targetFileName)
      targetFile3.lastModified mustEqual targetMod

      // append a newline, altering checksum, verify recompile
      val writer = new FileWriter(sourceFile)
      writer.write("//a comment\n2\n")
      writer.close
      val res4: Int = e(sourceFile)
      res4 mustEqual 2
      // and make sure it created a new file
      val targetFile4 = new File(targetFileName)
      targetFile4.exists must beFalse
    }

    "apply(InputStream)" in {
      (new Eval).apply[Int](getClass.getResourceAsStream("/OnePlusOne.scala")) mustEqual 2
    }

    "uses deprecated" in {
      val deprecated = (new Eval).apply[() => String](
        TempFile.fromResourcePath("/Deprecated.scala"))
      deprecated() mustEqual "hello"
    }

    "inPlace('expression')" in {
      // Old object API works
      Eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      Eval.inPlace[Int]("Doubler(2)") mustEqual 4
      Eval.inPlace[Int]("Doubler(14)") mustEqual 28
      // New class API fails
      // val eval = new Eval
      // eval.compile("object Doubler { def apply(n: Int) = n * 2 }")
      // eval.inPlace[Int]("Doubler(2)") mustEqual 4
      // eval.inPlace[Int]("Doubler(14)") mustEqual 28
    }

    "check" in {
      (new Eval).check("23")      mustEqual ()
      (new Eval).check("invalid") must throwA[Eval.CompilerException]
    }

    "#include" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/DerivedWithInclude.scala"))
      derived() mustEqual "hello"
      derived.toString mustEqual "hello, joe"
    }

    "recursive #include" in {
      val derived = Eval[() => String](
        TempFile.fromResourcePath("/Base.scala"),
        TempFile.fromResourcePath("/IncludeInclude.scala"))
      derived() mustEqual "hello"
      derived.toString mustEqual "hello, joe; hello, joe"
    }

    "toSource returns post-processed code" in {
      val derived = Eval.toSource(TempFile.fromResourcePath("/DerivedWithInclude.scala"))
      derived must include("hello, joe")
      derived must include("new Base")
    }

    "throws a compilation error when Ruby is #included" in {
      Eval[() => String](
        TempFile.fromResourcePath("RubyInclude.scala")) must throwA[Throwable]
    }

    "clean class names" in {
      val e = new Eval()
      // regular old scala file
      e.fileToClassName(new File("foo.scala")) mustEqual "foo"
      // without an extension
      e.fileToClassName(new File("foo")) mustEqual "foo"
      // with lots o dots
      e.fileToClassName(new File("foo.bar.baz")) mustEqual "foo$2ebar"
      // with dashes
      e.fileToClassName(new File("foo-bar-baz.scala")) mustEqual "foo$2dbar$2dbaz"
      // with crazy things
      e.fileToClassName(new File("foo$! -@@@")) mustEqual "foo$24$21$20$2d$40$40$40"
    }

    "allow custom error reporting" in {
      val eval = new Eval {
        var errors: Seq[(String, String)] = Nil

        override lazy val compilerMessageHandler: Option[Reporter] = Some(new AbstractReporter {
          override val settings: Settings = compilerSettings
          override def displayPrompt(): Unit = ()
          override def display(pos: Position, msg: String, severity: this.type#Severity): Unit = {
            errors = errors :+ (msg, severity.toString)
          }
          override def reset() = {
            errors = Nil
          }
        })
      }

      "not report errors on success" in {
        eval[Int]("val a = 3; val b = 2; a + b", true) mustEqual 5
        eval.errors must be empty
      }

      "report errors on bad code" in {
        eval[Int]("val a = 3; val b = q", true) must throwA[Throwable]
        eval.errors must not be empty
      }

      "not reset reporter when asked not to" in {
        eval[Int]("val a = 3; val b = q; a + b", false) must throwA[Throwable]
        eval.errors must not be empty
      }

      "reset reporter when asked to" in {
        eval[Int]("val a = 3; val b = 2; a + b", true) mustEqual 5
        eval.errors must be empty
      }
    }
  }
}
