package com.twitter.util

import scala.tools.nsc.{Global, Settings} 
import scala.tools.nsc.reporters.ConsoleReporter
import scala.runtime._
import java.io.{File, FileWriter}
import java.net.{URL, URLClassLoader}
import scala.io.Source

/**
 * Eval is a utility function to evaluate a file and return its results.
 * It is intended to be used for application configuration (rather than Configgy, XML, YAML files, etc.)
 * and anything else.
 *
 * Eval takes a file or string and generates a new scala class that has an apply method that 
 * evaluates that string. The newly generated file is then compiled. All generated .scala and .class
 * files are stored, by default, in System.getProperty("java.io.tmpdir").
 *
 * After compilation, the a new class loader is created with the temporary dir as the classPath.
 * The generated class is loaded and then apply() is called.
 * 
 * This implementation is inspired by
 * http://scala-programming-language.1934581.n4.nabble.com/Compiler-API-td1992165.html
 */
object Eval {
  private val compilerPath = jarPathOfClass("scala.tools.nsc.Interpreter")
  private val libPath = jarPathOfClass("scala.ScalaObject")

  /**
   * Eval[Int]("1 + 1") // => 2
   */
  def apply[T](stringToEval: String): T = {
    val className = "Evaluator"
    val targetDir = new File(System.getProperty("java.io.tmpdir"))
    val wrappedFile = wrapInClass(stringToEval, className, targetDir)
    compile(wrappedFile, targetDir)
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

  /**
   * Wrap sourceCode in a new class that has an apply method that evaluates that sourceCode.
   * Write generated (temporary) classes to targetDir
   */
  private def wrapInClass(sourceCode: String, className: String, targetDir: File) = {
    val targetFile = File.createTempFile(className, ".scala", targetDir)
    targetFile.deleteOnExit()
    val writer = new FileWriter(targetFile)
    writer.write("class " + className + " extends (() => Any) {\n")
    writer.write("  def apply() = {\n")
    writer.write(sourceCode)
    writer.write("\n  }\n")
    writer.write("}\n")
    writer.close()
    targetFile
  }

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
    val bootClassPath = origBootclasspath.split(java.io.File.pathSeparator).toList

    // the classpath for compile is our app path + boot path + make sure we have compiler/lib there
    val pathList = bootClassPath ::: (configulousClasspath ::: List(compilerPath, libPath))
    val pathString = pathList.mkString(java.io.File.pathSeparator)
    settings.bootclasspath.value = pathString
    settings.classpath.value = pathString
    settings.deprecation.value = true // enable detailed deprecation warnings 
    settings.unchecked.value = true // enable detailed unchecked warnings 
    settings.outdir.value = targetDir.toString

    val reporter = new ConsoleReporter(settings) 
    val compiler = new Global(settings, reporter) 
    (new compiler.Run).compile(List(file.toString))
    
    if (reporter.hasErrors || reporter.WARNING.count > 0) { 
      // TODO: throw ...
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