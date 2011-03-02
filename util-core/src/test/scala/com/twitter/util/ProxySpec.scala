package com.twitter.util.reflect

import org.specs.Specification
import com.twitter.util.{Future,Promise}


object ProxySpec extends Specification {
  trait TestInterface {
    def foo: String
    def bar(a: Int): Option[Long]
    def whoops: Int
    def theVoid: Unit
    def theJavaVoid: java.lang.Void
    def aFuture: Future[Int]
    def aPromise: Promise[Int]
  }

  class TestImpl extends TestInterface {
    def foo = "foo"
    def bar(a: Int) = Some(a.toLong)
    def whoops = if (false) 2 else error("whoops")
    def theVoid {}
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


  "ProxyFactory" should {
    "generate a factory for an interface" in {
      var called = 0

      val f1  = new ProxyFactory[TestInterface]({ m => called += 1; m() })
      val obj = new TestImpl

      val proxied = f1(obj)

      proxied.foo    mustEqual "foo"
      proxied.bar(2) mustEqual Some(2L)

      called mustEqual 2
    }

    "generate a factory for a class with a default constructor" in {
      var called = 0

      val f2  = new ProxyFactory[TestClass]({ m => called += 1; m() })
      val obj = new TestClass

      val proxied = f2(obj)

      proxied.slota mustEqual "a"
      proxied.slotb mustEqual 2.0

      called mustEqual 2
    }

    "must not throw UndeclaredThrowableException" in {
      val pf      = new ProxyFactory[TestImpl](_())
      val proxied = pf(new TestImpl)

      proxied.whoops must throwA(new RuntimeException("whoops"))
    }

    "MethodCall returnsUnit must be true for unit/void methods" in {
      var unitsCalled = 0

      val pf = new ProxyFactory[TestImpl]({ call =>
        if (call.returnsUnit) unitsCalled += 1
        call()
      })

      val proxied = pf(new TestImpl)
      proxied.foo
      unitsCalled mustEqual 0

      proxied.theVoid
      unitsCalled mustEqual 1

      proxied.theJavaVoid
      unitsCalled mustEqual 2
    }

    "MethodCall returnsFuture must be true for methods that return a future or subclass" in {
      var futuresCalled = 0

      val pf = new ProxyFactory[TestImpl]({ call =>
        if (call.returnsFuture) futuresCalled += 1
        call()
      })

      val proxied = pf(new TestImpl)

      proxied.foo
      futuresCalled mustEqual 0

      proxied.aFuture
      futuresCalled mustEqual 1

      proxied.aPromise
      futuresCalled mustEqual 2
    }

    "MethodCall throws an exception when invoked without a target" in {
      val pf = new ProxyFactory[TestImpl](_())
      val targetless = pf()

      targetless.foo must throwA[NonexistentTargetException]
    }

    "MethodCall can be invoked with alternate target" in {
      val alt = new TestImpl { override def foo = "alt foo" }
      val pf  = new ProxyFactory[TestImpl](m => m(alt))
      val targetless = pf()

      targetless.foo mustEqual "alt foo"
    }

    "MethodCall can be invoked with alternate arguments" in {
      val pf      = new ProxyFactory[TestInterface](m => m(Array(3.asInstanceOf[AnyRef])))
      val proxied = pf(new TestImpl)

      proxied.bar(2) mustEqual Some(3L)
    }

    "MethodCall can be invoked with alternate target and arguments" in {
      val alt = new TestImpl { override def bar(i: Int) = Some(i.toLong * 10) }
      val pf  = new ProxyFactory[TestImpl](m => m(alt, Array(3.asInstanceOf[AnyRef])))
      val targetless = pf()

      targetless.bar(2) mustEqual Some(30L)
    }


    def time(f: => Unit) = { val s = System.currentTimeMillis; f; val e = System.currentTimeMillis; e - s }
    def benchmark(f: => Unit) = {
      // warm up
      for (i <- 1 to 5) f

      // measure
      val results = for (i <- 1 to 5) yield time(f)

      // drop the top and bottom then average
      results.sortWith( (a,b) => a > b ).slice(1,4).sum / 3
    }

    val reflectConstructor = {() => new ReferenceProxyFactory[TestInterface](_()) }
    val cglibConstructor   = {() => new ProxyFactory[TestInterface](_()) }

    "maintains instantiation speed" in {
      val repTimes = 40000

      val t1 = benchmark { for (i <- 1 to repTimes) reflectConstructor() }
      val t2 = benchmark { for (i <- 1 to repTimes) cglibConstructor() }

      // println("instantiation")
      // println(t1)
      // println(t2)

      t2 must beLessThan(50L)
    }

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

      t2 must beLessThan(200L)
    }

    "maintains invocation speed" in {
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

      t2 must beLessThan(t3 * 3)
    }
  }


  class ReferenceProxyFactory[I <: AnyRef : Manifest](f: (() => AnyRef) => AnyRef) {
    import java.lang.reflect

    protected val interface = implicitly[Manifest[I]].erasure

    private val proxyConstructor = {
      reflect.Proxy
      .getProxyClass(interface.getClassLoader, interface)
      .getConstructor(classOf[reflect.InvocationHandler])
    }

    def apply[T <: I](instance: T) = {
      proxyConstructor.newInstance(new reflect.InvocationHandler {
        def invoke(p: AnyRef, method: reflect.Method, args: Array[AnyRef]) = {
          try {
            f.apply(() => method.invoke(instance, args: _*))
          } catch { case e: reflect.InvocationTargetException => throw e.getCause }
        }
      }).asInstanceOf[I]
    }
  }
}
