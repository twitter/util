package com.twitter.util

import scala.language.experimental.macros
import scala.concurrent.{ExecutionContext}
import com.twitter.util.Future
import scala.annotation.compileTimeOnly
import scala.reflect.macros.whitebox

/**
  * Async blocks provide a direct means to work with [[scala.concurrent.Future]].
  *
 * For example, to use an API that fetches a web page to fetch
  * two pages and add their lengths:
  *
 * {{{
  *  import com.twitter.util.Future
  *  import com.twitter.util.Async.{async, await}
  *
 *  def fetchURL(url: URL): Future[String] = ...
  *
 *  val sumLengths: Future[Int] = async {
  *    val body1 = fetchURL("http://scala-lang.org")
  *    val body2 = fetchURL("http://docs.scala-lang.org")
  *    await(body1).length + await(body2).length
  *  }
  * }}}
  *
 * Note that in the following program, the second fetch does *not* start
  * until after the first. If you need to start tasks in parallel, you must do
  * so before `await`-ing a result.
  *
 * {{{
  *  val sumLengths: Future[Int] = async {
  *    await(fetchURL("http://scala-lang.org")).length + await(fetchURL("http://docs.scala-lang.org")).length
  *  }
  * }}}
  */
object Async {

  /**
    * Run the block of code `body` asynchronously. `body` may contain calls to `await` when the results of
    * a `Future` are needed; this is translated into non-blocking code.
    */
  def async[T](body: => T): Future[T] =
    macro asyncImpl[T]

  /**
    * Non-blocking await the on result of `awaitable`. This may only be used directly within an enclosing `async` block.
    *
   * Internally, this will register the remainder of the code in enclosing `async` block as a callback
    * in the `onComplete` handler of `awaitable`, and will *not* block a thread.
    */
  @compileTimeOnly("[async] `await` must be enclosed in an `async` block")
  def await[T](awaitable: Future[T]): T =
    ??? // No implementation here, as calls to this are translated to `onComplete` by the macro.

  def asyncImpl[T: c.WeakTypeTag](
      c: whitebox.Context
  )(body: c.Tree): c.Tree = {
    import c.universe._
    if (!c.compilerSettings.contains("-Xasync")) {
      c.abort(
        c.macroApplication.pos,
        "The async requires the compiler option -Xasync (supported only by Scala 2.12.12+ / 2.13.3+)"
      )
    } else
      try {
        val awaitSym = typeOf[Async.type].decl(TermName("await"))
        def mark(t: DefDef): Tree = {
          import language.reflectiveCalls
          c.internal
            .asInstanceOf[{
                def markForAsyncTransform(
                    owner: Symbol,
                    method: DefDef,
                    awaitSymbol: Symbol,
                    config: Map[String, AnyRef]
                ): DefDef
              }
            ]
            .markForAsyncTransform(
              c.internal.enclosingOwner,
              t,
              awaitSym,
              Map.empty
            )
        }
        val name = TypeName("stateMachine$async")
        q"""
      final class $name extends _root_.com.twitter.util.FutureStateMachine() {
        // FSM translated method
        ${mark(
          q"""override def apply(tr$$async: _root_.scala.util.Try[_root_.scala.AnyRef]) = ${body}"""
        )}
      }
      new $name().start() : ${c.macroApplication.tpe}
    """
      } catch {
        case e: ReflectiveOperationException =>
          c.abort(
            c.macroApplication.pos,
            "-Xasync is provided as a Scala compiler option, but the async macro is unable to call c.internal.markForAsyncTransform. " + e.getClass.getName + " " + e.getMessage
          )
      }
  }
}
