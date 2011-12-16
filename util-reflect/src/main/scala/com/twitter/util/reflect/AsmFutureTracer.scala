package com.twitter.util.reflect

import com.twitter.util.{Local, Future}
import org.objectweb.asm.commons.EmptyVisitor
import org.objectweb.asm.{Label, ClassReader}
import net.sf.cglib.proxy.Enhancer

class AsmFutureTracer(maxDepth: Int) extends Future.Tracer {
  def this() = this(24)

  /**
   * An exception whose stacktrace is the provided realException but whose cause
   * is the fakeStackTrace. This is the inverse of a normal wrapped-exception,
   * but more clearly expresses the true cause and the asynchronous path to the cause.
   */
  private[this] class TracedException(realException: Throwable, fakeStackTrace: Seq[StackTraceElement])
  extends Exception(realException.getMessage) {
    if (realException.getCause eq null) {
      val fakeException = new Exception {
        override def fillInStackTrace() = {
          setStackTrace(fakeStackTrace.toArray)
          this
        }
        override def toString = "com.twitter.util.Future asynchronous trace"
      }
      initCause(fakeException)
    } else {
      initCause(new TracedException(realException.getCause, fakeStackTrace))
    }

    override def fillInStackTrace() = {
      setStackTrace(realException.getStackTrace)
      this
    }
    override def toString = realException.toString
  }

  private[this] val local = new Local[Vector[Class[_]]]

  private[util] def record(a: AnyRef) {
    val clazz = a.getClass
    local() = (clazz +: local().getOrElse(Vector.empty[Class[_]])).take(maxDepth)
  }

  /**
   * Note: because of limitations in the JVM, exceptions can only be enhanced if their class
   * has a zero-args constructor. The original exception is returned if this is not the case.
   */
  private[util] def wrap[T <: Throwable](throwable: T): T = {
    val fakeTrace = stackTrace
    if (fakeTrace.isEmpty) return throwable
    val hasNoArgsConstructor = throwable.getClass.getConstructors.exists(_.getParameterTypes.isEmpty)
    if (!hasNoArgsConstructor) return throwable

    val tracedException = new TracedException(throwable, fakeTrace)
    val enhancer = new Enhancer
    enhancer.setSuperclass(throwable.getClass)
    val interceptor = new MethodInterceptor[T](None, { call =>
      val method = call.method
      val args = call.args

      if (method.getDeclaringClass == classOf[Throwable]) {
        method.invoke(tracedException, args: _*)
      } else
        method.invoke(throwable, args: _*)
    })
    enhancer.setCallback(interceptor)
    enhancer.create.asInstanceOf[T]
  }

  def stackTrace = {
    val clazzes = local().getOrElse(Vector.empty[Class[_]])
    clazzes.map(toStackTraceElement(_))
  }

  private[this] def toStackTraceElement(clazz: Class[_]) = {
    val stream = clazz.getResourceAsStream(clazz.getName.split('.').last + ".class")
    val classReader = new ClassReader(stream)

    var source = ""
    var line = 0
    var visitedLine = false

    val classVisitor = new EmptyVisitor {
      override def visitSource(_source: String, debug: String) {
        source = _source
      }
      override def visitMethod(
        access: Int,
        name: String,
        desc: String,
        signature: String,
        exceptions: Array[String]
      ) = {
        if (name != "<init>") null
        else new EmptyVisitor {
          override def visitLineNumber(_line: Int, label: Label) {
            // Note: this is just the first line in the method, as we can't get an exact line number
            if (visitedLine) return

            line = _line
            visitedLine = true
          }
        }
      }
    }

    classReader.accept(classVisitor, 0)
    new StackTraceElement(clazz.getName, "<init>", source, line)
  }
}
