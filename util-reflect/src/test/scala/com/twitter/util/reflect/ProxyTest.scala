package com.twitter.util.reflect

import com.twitter.util.{Future, Promise, Stopwatch}
import org.scalatest.WordSpec

object ProxySpec {
  trait TestInterface {
    def foo: String
    def bar(a: Int): Option[Long]
    def whoops: Int
    def theVoid(): Unit
    def theJavaVoid: java.lang.Void
    def aFuture: Future[Int]
    def aPromise: Promise[Int]
  }

  class TestImpl extends TestInterface {
    def foo = "foo"
    def bar(a: Int) = Some(a.toLong)
    def whoops = if (false) 2 else sys.error("whoops")
    def theVoid() = ()
    def theJavaVoid = null
    def aFuture = Future.value(2)
    def aPromise = new Promise[Int] {
      setValue(2)
    }
  }

  class TestClass {
    val slota = "a"
    val slotb = 2.0
  }
}

class ProxyTest extends WordSpec {

  import ProxySpec._

  "ProxyFactory" should {
    "generate a factory for an interface" in {
      var called = 0

      val f1 = new ProxyFactory[TestInterface]({ m =>
        called += 1; m()
      })
      val obj = new TestImpl

      val proxied = f1(obj)

      assert(proxied.foo == "foo")
      assert(proxied.bar(2) == Some(2L))

      assert(called == 2)
    }

    "generate a factory for a class with a default constructor" in {
      var called = 0

      val f2 = new ProxyFactory[TestClass]({ m =>
        called += 1; m()
      })
      val obj = new TestClass

      val proxied = f2(obj)

      assert(proxied.slota == "a")
      assert(proxied.slotb == 2.0)

      assert(called == 2)
    }

    "must not throw UndeclaredThrowableException" in {
      val pf = new ProxyFactory[TestImpl](_())
      val proxied = pf(new TestImpl)

      intercept[RuntimeException] {
        proxied.whoops
      }
    }

    "MethodCall returnsUnit must be true for unit/void methods" in {
      var unitsCalled = 0

      val pf = new ProxyFactory[TestImpl]({ call =>
        if (call.returnsUnit) unitsCalled += 1
        call()
      })

      val proxied = pf(new TestImpl)
      proxied.foo
      assert(unitsCalled == 0)

      proxied.theVoid()
      assert(unitsCalled == 1)

      proxied.theJavaVoid
      assert(unitsCalled == 2)
    }

    "MethodCall returnsFuture must be true for methods that return a future or subclass" in {
      var futuresCalled = 0

      val pf = new ProxyFactory[TestImpl]({ call =>
        if (call.returnsFuture) futuresCalled += 1
        call()
      })

      val proxied = pf(new TestImpl)

      proxied.foo
      assert(futuresCalled == 0)

      proxied.aFuture
      assert(futuresCalled == 1)

      proxied.aPromise
      assert(futuresCalled == 2)
    }

    "MethodCall throws an exception when invoked without a target" in {
      val pf = new ProxyFactory[TestImpl](_())
      val targetless = pf()

      intercept[NonexistentTargetException] {
        targetless.foo
      }
    }

    "MethodCall can be invoked with alternate target" in {
      val alt = new TestImpl { override def foo = "alt foo" }
      val pf = new ProxyFactory[TestImpl](m => m(alt))
      val targetless = pf()

      assert(targetless.foo == "alt foo")
    }

    "MethodCall can be invoked with alternate arguments" in {
      val pf = new ProxyFactory[TestInterface](m => m(Array(3.asInstanceOf[AnyRef])))
      val proxied = pf(new TestImpl)

      assert(proxied.bar(2) == Some(3L))
    }

    "MethodCall can be invoked with alternate target and arguments" in {
      val alt = new TestImpl { override def bar(i: Int) = Some(i.toLong * 10) }
      val pf = new ProxyFactory[TestImpl](m => m(alt, Array(3.asInstanceOf[AnyRef])))
      val targetless = pf()

      assert(targetless.bar(2) == Some(30L))
    }

    // Sigh. Benchmarking has no place in unit tests.

    def time(f: => Unit) = { val elapsed = Stopwatch.start(); f; elapsed().inMilliseconds }
    def benchmark(f: => Unit) = {
      // warm up
      for (i <- 1 to 10) f

      // measure
      val results = for (i <- 1 to 7) yield time(f)
      //println(results)

      // drop the top and bottom then average
      results.sortWith((a, b) => a > b).slice(2, 5).sum / 3
    }

    val reflectConstructor = { () =>
      new ReferenceProxyFactory[TestInterface](_())
    }
    val cglibConstructor = { () =>
      new ProxyFactory[TestInterface](_())
    }
    /*
    "maintains proxy creation speed" in {
      val repTimes = 40000

      val obj = new TestImpl
      val factory1 = reflectConstructor()
      val factory2 = cglibConstructor()

      val t1 = benchmark { for (i <- 1 to repTimes) factory1(obj) }
      val t2 = benchmark { for (i <- 1 to repTimes) factory2(obj) }

      // println("proxy creation")
      // println(t1)
      // println(t2)

      t2 should beLessThan(200L)
    }
     */
    "maintains invocation speed" ignore {
      val repTimes = 1500000

      val obj = new TestImpl
      val proxy1 = reflectConstructor()(obj)
      val proxy2 = cglibConstructor()(obj)

      val t1 = benchmark { for (i <- 1 to repTimes) { proxy1.foo; proxy1.bar(2) } }
      val t2 = benchmark { for (i <- 1 to repTimes) { proxy2.foo; proxy2.bar(2) } }
      val t3 = benchmark { for (i <- 1 to repTimes) { obj.foo; obj.bar(2) } }

      // println("proxy invocation")
      // println(t1)
      // println(t2)
      // println(t3)

      // faster than normal reflection
      assert(t2 < t1)

      // less than 4x raw invocation
      assert(t2 < t3 * 4)
    }
  }

  class ReferenceProxyFactory[I <: AnyRef: Manifest](f: (() => AnyRef) => AnyRef) {
    import java.lang.reflect

    protected val interface = implicitly[Manifest[I]].runtimeClass

    private val proxyConstructor = {
      reflect.Proxy
        .getProxyClass(interface.getClassLoader, interface)
        .getConstructor(classOf[reflect.InvocationHandler])
    }

    def apply[T <: I](instance: T) = {
      proxyConstructor
        .newInstance(new reflect.InvocationHandler {
          def invoke(p: AnyRef, method: reflect.Method, args: Array[AnyRef]) = {
            try {
              f.apply(() => method.invoke(instance, args: _*))
            } catch { case e: reflect.InvocationTargetException => throw e.getCause }
          }
        })
        .asInstanceOf[I]
    }
  }
}
