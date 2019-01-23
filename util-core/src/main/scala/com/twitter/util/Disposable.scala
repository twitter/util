package com.twitter.util

/**
 * ==Resource Management==
 *
 * [[com.twitter.util.Disposable]] represents a live resource that must be disposed after use.
 * [[com.twitter.util.Managed]] is a factory for creating and composing such resources.
 * Managed resources are composed together so their lifetimes are synchronized.
 *
 * The following example shows how to build and use composite managed resources:
 *
 * {{{
 * // Create managed Tracer
 * def mkManagedTracer() = new Managed[Tracer] {
 *   def make() = new Disposable[Tracer] {
 *     val underlying = new Tracer()
 *     def get = underlying
 *     def dispose(deadline: Time) = underlying.close() // assumes Tracer uses relese() to manage lifetime
 *   }
 * }
 *
 * // Create managed Server using Tracer as dependency
 * def mkManagedServer(t: Tracer) = new Managed[Server] {
 *   def make() = new Disposable[Server] {
 *     val underlying = new Server(t) // Server requires tracer to be created
 *     def get = underlying
 *     def dispose(deadline: Time) = underlying.close() // assumes Server uses close() to manage lifetime
 *   }
 * }
 *
 * // Create composite resource
 * val compRes: Managed[Server] = for {
 *   a <- mkManagedTracer()
 *   b <- mkManagedServer(a)
 * } yield b
 *
 * // Use composite resource in safe fashion. It's guaranteed that both resources
 * // will be properly closed/released when done using them.
 * compRes foreach { b =>
 *   // use b (which is type Server in this case)
 * } // dispose called on both resources
 * }}}
 *
 * =Disposable/Managed Semantics=
 *
 * [[com.twitter.util.Disposable]]: get can be called multiple times and should return same instance;
 * dispose can be called only once and should release resource that get is returning;
 * calling get after dispose is undefined.
 *
 * [[com.twitter.util.Managed]]: multiple calls to make could return
 *  a) new instance or
 *  b) ref counted instance of the underlying resource or
 *  c) same instance when resource doesn't need to be actually disposed, etc.
 *
 * Disposable is a container for a resource that must be explicitly disposed when
 * no longer needed. After this, the resource is no longer available.
 */
trait Disposable[+T] {
  def get: T

  /**
   * Dispose of a resource by deadline given.
   */
  def dispose(deadline: Time): Future[Unit]
  final def dispose(): Future[Unit] = dispose(Time.Top)
}

object Disposable {
  def const[T](t: T): Disposable[T] = new Disposable[T] {
    def get = t
    def dispose(deadline: Time) = Future.value(())
  }
}

/**
 * `Managed[T]` is a resource of type `T` which lifetime is explicitly managed.
 * It is created with `make()`. Composite resources, which lifetimes are managed
 * together, are created by the use of `flatMap`, ensuring proper construction and
 * teardown of the comprised resources.
 */
trait Managed[+T] { selfT =>

  /**
   * Create a new T, and pass it to the given operation (f).
   * After it completes, the resource is disposed.
   */
  def foreach(f: T => Unit): Unit = {
    val r = this.make()
    try f(r.get)
    finally r.dispose()
  }

  /**
   * Compose a new managed resource that depends on `this' managed resource.
   */
  def flatMap[U](f: T => Managed[U]): Managed[U] = new Managed[U] {
    def make() = new Disposable[U] {
      val t = selfT.make()

      val u = try {
        f(t.get).make()
      } catch {
        case e: Exception =>
          t.dispose()
          throw e
      }

      def get = u.get

      def dispose(deadline: Time) = {
        u.dispose(deadline) transform {
          case Return(_) => t.dispose(deadline)
          case Throw(outer) =>
            t.dispose transform {
              case Throw(inner) => Future.exception(new DoubleTrouble(outer, inner))
              case Return(_) => Future.exception(outer)
            }
        }
      }
    }
  }

  def map[U](f: T => U): Managed[U] = flatMap { t =>
    Managed.const(f(t))
  }

  /**
   * Builds a resource.
   */
  def make(): Disposable[T]
}

object Managed {
  def singleton[T](t: Disposable[T]): Managed[T] = new Managed[T] { def make() = t }
  def const[T](t: T): Managed[T] = singleton(Disposable.const(t))
}

class DoubleTrouble(cause1: Throwable, cause2: Throwable) extends Exception {
  override def getStackTrace: Array[StackTraceElement] = cause1.getStackTrace
  override def getMessage: String =
    "Double failure while disposing composite resource: %s \n %s".format(
      cause1.getMessage,
      cause2.getMessage
    )
}
