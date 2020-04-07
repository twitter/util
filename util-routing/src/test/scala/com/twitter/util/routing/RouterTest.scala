package com.twitter.util.routing

import com.twitter.conversions.DurationOps._
import com.twitter.util.routing.dynamic.{DynamicRouter, DynamicRoute, Method, Request, Result}
import com.twitter.util.routing.simple.{SimpleRoute, SimpleRouter}
import com.twitter.util.{Await, Awaitable, Closable, Future, Time}
import java.util.concurrent.atomic.AtomicBoolean
import org.scalatest.FunSuite

class RouterTest extends FunSuite {

  private def await[T](awaitable: Awaitable[T]): T =
    Await.result(awaitable, 2.seconds)

  test("can define a router of generic types") {
    val helloRoute = SimpleRoute("hello", true)
    val goodbyeRoute = SimpleRoute("goodbye", false)

    val router = new SimpleRouter(Map("hello" -> helloRoute, "goodbye" -> goodbyeRoute))

    assert(router("hello") == Some(helloRoute))
    assert(router("goodbye") == Some(goodbyeRoute))
    assert(router("something else") == None)
  }

  test("test a router with dynamic routes") {
    val abcReadHandler =
      DynamicRoute(Method.Read, _.query.startsWith("abc/"), _ => Future.value(Result.Success))
    val xyzReadHandler =
      DynamicRoute(Method.Read, _.query.startsWith("xyz/"), _ => Future.value(Result.Failure))
    val abcWriteHandler =
      DynamicRoute(Method.Write, _.query.startsWith("abc/"), _ => Future.value(Result.Success))
    val defWriteHandler =
      DynamicRoute(Method.Write, _.query == "def", _ => Future.value(Result.Failure))

    val router = DynamicRouter
      .newBuilder()
      .withRoute(abcReadHandler)
      .withRoute(xyzReadHandler)
      .withRoute(abcWriteHandler)
      .withRoute(defWriteHandler)
      .newRouter()

    assert(router(Request(Method.Read, "abc/123/456")) == Some(abcReadHandler))
    assert(router(Request(Method.Read, "def/123/456")) == None)
    assert(router(Request(Method.Read, "xyz/123/456")) == Some(xyzReadHandler))
    assert(router(Request(Method.Write, "abc/123/456")) == Some(abcWriteHandler))
    assert(router(Request(Method.Write, "def")) == Some(defWriteHandler))
    assert(router(Request(Method.Write, "def/123/456")) == None)
    assert(router(Request(Method.Write, "xyz/123/456")) == None)

    await(router.close())
    assert(router.isClosed)
    router.routes.foreach(r => assert(r.isClosed))
  }

  test("multiple close() calls on a router only executes close once") {
    val router = new SimpleRouter(Map.empty)

    assert(router.closedTimes.get() == 0)
    await(router.close())
    assert(router.closedTimes.get() == 1)
    await(router.close())
    assert(router.closedTimes.get() == 1)
    await(router.close())
    assert(router.closedTimes.get() == 1)
  }

  test("routing to closed router throws") {
    val router = new SimpleRouter(Map.empty)
    await(router.close())
    intercept[ClosedRouterException] {
      router("hello")
    }
  }

  test("router closes routes that are closable") {
    class AtomicClosable extends Closable {
      val isClosed = new AtomicBoolean(false);
      override def close(deadline: Time): Future[Unit] = {
        isClosed.compareAndSet(false, true)
        Future.Done
      }
    }

    val closableA = new AtomicClosable
    val closableB = new AtomicClosable
    val closableC = new AtomicClosable

    class ClosableRouter extends Router[Boolean, Closable] {
      override def label: String = "closable"
      override def routes: Iterable[Closable] = Seq(closableA, closableB, closableC)
      override protected def find(input: Boolean): Option[Closable] = None
    }

    val router = new ClosableRouter

    assert(closableA.isClosed.get() == false)
    assert(closableB.isClosed.get() == false)
    assert(closableC.isClosed.get() == false)

    await(router.close())

    assert(closableA.isClosed.get() == true)
    assert(closableB.isClosed.get() == true)
    assert(closableC.isClosed.get() == true)

  }

  test("a router is marked as closed before underlying routes complete closing") {
    val closableA = Closable.make(_ => Future.never)
    val closableB = Closable.make(_ => Future.Done)

    class ClosableRouter extends Router[Boolean, Closable] {
      override def label: String = "closable"
      override def routes: Iterable[Closable] = Seq(closableA, closableB)
      override protected def find(input: Boolean): Option[Closable] = None
    }

    val router = new ClosableRouter

    router(true) // route doesn't throw, router is still open

    val closeFuture = router.close() // close the router, any further requests will fail

    assert(closeFuture.isDefined == false)
    intercept[ClosedRouterException] {
      router(true)
    }
    assert(closeFuture.isDefined == false) // the future never completes
  }

  test("a router suppresses non-fatal exceptions on close") {
    val closableA = Closable.make(_ => throw new IllegalStateException("BOOM"))

    class ClosableRouter extends Router[Boolean, Closable] {
      override def label: String = "closable"
      override def routes: Iterable[Closable] = Seq(closableA)
      override protected def find(input: Boolean): Option[Closable] = None
    }

    val router = new ClosableRouter
    await(router.close()) // close without exception
  }

  test("a router bubbles up fatal exceptions on close") {
    val closableA = Closable.make(_ => throw new OutOfMemoryError("BOOM"))
    val closableB = Closable.make(_ => throw new OutOfMemoryError("BAM"))

    class ClosableRouter extends Router[Boolean, Closable] {
      override def label: String = "closable"
      override def routes: Iterable[Closable] = Seq(closableA, closableB)
      override protected def find(input: Boolean): Option[Closable] = None
    }

    val router = new ClosableRouter
    val e = intercept[OutOfMemoryError] {
      await(router.close())
    }
    assert(e.getMessage == "BOOM")
  }

}
