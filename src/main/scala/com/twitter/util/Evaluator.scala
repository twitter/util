package com.twitter.util

import java.io.{File, FileWriter}
import java.math.BigInteger
import java.net.{URL, URLClassLoader}
import java.security.MessageDigest
import java.util.jar._
import java.util.Random
import scala.io.Source
import scala.runtime._
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.{Global, Settings}

/**
 * Eval is a utility function to evaluate a file and return its results.
 * It is intended to be used for application configuration (rather than Configgy, XML, YAML files, etc.)
 * and anything else.
 *
 * Consider this example. You have the following configuration trait Config.scala.
 *
 *     package com.mycompany
 *
 *     import com.twitter.util.Duration
 *     import com.twitter.util.StorageUnit
 *
 *     trait Config {
 *       def myValue: Int
 *       def myTimeout: Duration
 *       def myBufferSize: StorageUnit
 *     }
 *
 * You have the following configuration file (specific values) in config/Development.scala:
 *
 *     import com.mycompany.Config
 *     import com.twitter.util.TimeConversions._
 *     import com.twitter.util.StorageUnitConversions._
 *
 *     new Config {
 *       val myValue = 1
 *       val myTimeout = 2.seconds
 *       val myBufferSize = 14.kilobytes
 *     }
 *
 * And finally, in Main.scala:
 *
 *     package com.mycompany
 *
 *     object Main {
 *       def main(args: Array[String]) {
 *         val config = Eval[Config](new File(args(0)))
 *         ...
 *       }
 *     }
 *
 * Note that in this example there is no need for any configuration format like Configgy, YAML, etc.
 *
 * So how does this work? Eval takes a file or string and generates a new scala class that has an apply method that
 * evaluates that string. The newly generated file is then compiled. All generated .scala and .class
 * files are stored, by default, in System.getProperty("java.io.tmpdir").
 *
 * After compilation, a new class loader is created with the temporary dir as the classPath.
 * The generated class is loaded and then apply() is called.
 *
 * This implementation is inspired by
 * http://scala-programming-language.1934581.n4.nabble.com/Compiler-API-td1992165.html
 *
 */
object Eval {
  private val compilerPath = jarPathOfClass("scala.tools.nsc.Interpreter")
  private val libPath = jarPathOfClass("scala.ScalaObject")
  private val jvmId = java.lang.Math.abs(new Random().nextInt())

  /**
   * Eval[Int]("1 + 1") // => 2
   */
  def apply[T](stringToEval: String): T = {
    val md = MessageDigest.getInstance("SHA")
    val digest = md.digest(stringToEval.getBytes())
    val sha = new BigInteger(1, digest).toString(16)

    val uniqueId = sha + "_" + jvmId
    val className = "Evaluator" + uniqueId
    val targetDir = new File(System.getProperty("java.io.tmpdir") + "evaluator_" + uniqueId)
    ifUncompiled(targetDir, className) { targetFile =>
      wrapInClassAndOutput(stringToEval, className, targetFile)
      compile(targetFile, targetDir)
    }
    val clazz = loadClass(targetDir, className)
    val constructor = clazz.getConstructor()
    val evaluator = constructor.newInstance().asInstanceOf[() => Any]
    evaluator().asInstanceOf[T]
  }

  /**
   * Eval[Int](new File("..."))
   */
  def apply[T](fileToEval: File): T = {
    val stringToEval = scala.io.Source.fromFile(fileToEval).mkString
    apply(stringToEval)
  }

  private def ifUncompiled(targetDir: File, className: String)(f: File => Unit) {
    targetDir.mkdirs()
    targetDir.deleteOnExit()

    val targetFile = new File(targetDir, className + ".scala")
    if (!targetFile.exists) {
      val created = targetFile.createNewFile()
      if (!created) {
        // FIXME: this indicates that another jvm randomly generated the same
        // integer and compiled this file. Or, more likely, this method was called
        // simultaneously from two threads.
      }
      f(targetFile)
    }

    targetDir.listFiles().foreach { _.deleteOnExit() }
  }

  /**
   * Wrap sourceCode in a new class that has an apply method that evaluates that sourceCode.
   * Write generated (temporary) classes to targetDir
   */
  private def wrapInClassAndOutput(sourceCode: String, className: String, targetFile: File) {
    val writer = new FileWriter(targetFile)
    writer.write("class " + className + " extends (() => Any) {\n")
    writer.write("  def apply() = {\n")
    writer.write("    " + sourceCode)
    writer.write("\n  }\n")
    writer.write("}\n")
    writer.close()
  }

  val JarFile = """\.jar$""".r
  /**
   * Compile a given file into the targetDir
   */
  private def compile(file: File, targetDir: File) {
    val settings = new Settings
    val origBootclasspath = settings.bootclasspath.value

    // Figure out our app classpath.
    // TODO: there are likely a ton of corner cases waiting here
    val configulousClassLoader = this.getClass.getClassLoader.asInstanceOf[URLClassLoader]
    val configulousClasspath = configulousClassLoader.getURLs.map { url =>
      val urlStr = url.toString
      urlStr.substring(5, urlStr.length)
    }.toList

    // It's not clear how many nested jars we should open.
    val classPathAndClassPathsNestedInJars = configulousClasspath.flatMap { fileName =>
      val nestedClassPath = if (JarFile.findFirstMatchIn(fileName).isDefined) {
      val nestedClassPathAttribute = new JarFile(fileName).getManifest.getMainAttributes.getValue("Class-Path")
        // turns /usr/foo/bar/foo-1.0.jar into ["", "usr", "foo", "bar", "foo-1.0.jar"]
        val rootDirPath = fileName.split(File.separator).toList
        // and then into /usr/foo/bar
        val rootDir = rootDirPath.slice(1, rootDirPath.length - 1).mkString(File.separator, File.separator, File.separator)

        if (nestedClassPathAttribute != null) {
          nestedClassPathAttribute.split(" ").toList.map(rootDir + File.separator + _)
        } else Nil
      } else Nil
      List(fileName) ::: nestedClassPath
    }
    val bootClassPath = origBootclasspath.split(File.pathSeparator).toList

    // the classpath for compile is our app path + boot path + make sure we have compiler/lib there
    val pathList = bootClassPath ::: (classPathAndClassPathsNestedInJars ::: List(compilerPath, libPath))
    val pathString = pathList.mkString(File.pathSeparator)
    settings.bootclasspath.value = pathString
    settings.classpath.value = pathString
    settings.deprecation.value = true // enable detailed deprecation warnings
    settings.unchecked.value = true // enable detailed unchecked warnings
    settings.outdir.value = targetDir.toString

    val reporter = new ConsoleReporter(settings)
    val compiler = new Global(settings, reporter)
    (new compiler.Run).compile(List(file.toString))

    if (reporter.hasErrors || reporter.WARNING.count > 0) {
      // FIXME: use proper logging
      System.err.println("reporter has warnings attempting to compile")
      reporter.printSummary()
    }
  }

  /**
   * Create a new classLoader with the targetDir as the classPath.
   * Load the class with className
   */
  private def loadClass(targetDir: File, className: String) = {
    // set up the new classloader in targetDir
    val scalaClassLoader = this.getClass.getClassLoader
    val targetDirURL = targetDir.toURL
    val newClassLoader = URLClassLoader.newInstance(Array(targetDir.toURL), scalaClassLoader)
    newClassLoader.loadClass(className)
  }

  private def jarPathOfClass(className: String) = {
    val resource = className.split('.').mkString("/", "/", ".class")
    //println("resource for %s is %s".format(className, resource))
    val path = getClass.getResource(resource).getPath
    val indexOfFile = path.indexOf("file:")
    val indexOfSeparator = path.lastIndexOf('!')
    path.substring(indexOfFile, indexOfSeparator)
  }
}
