package com.twitter.util

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.mutable
import java.util.concurrent.atomic.AtomicReference

@RunWith(classOf[JUnitRunner])
class VarTest extends FunSuite {
  private case class U[T](init: T) extends UpdatableVar[T](init) {
    import Var.Observer

    var observerCount = 0
    var accessCount = 0

    override def observe(d: Int, obs: Observer[T]) = {
      accessCount += 1
      observerCount += 1
      Closable.all(
        super.observe(d, obs),
        Closable.make { deadline =>
          observerCount -= 1
          Future.Done
        })
    }
  }

  test("Var.map") {
    val v = Var(123)
    val s = v map (_.toString)
    assert(Var.sample(s) === "123")
    v() = 8923
    assert(Var.sample(s) === "8923")
    
    var buf = mutable.Buffer[String]()
    s observe { v => buf += v }
    assert(buf.toSeq === Seq("8923"))
    v() = 111
    assert(buf.toSeq === Seq("8923", "111"))
  }

  test("depth ordering") {
    val v0 = U(3)
    val v1 = U(2)
    val v2 = v1 flatMap { i => v1 }
    val v3 = v2 flatMap { i => v1 }
    val v4 = v3 flatMap { i => v0 }

    var result = 1
    v4.observe{ i => result = result+2 } // result = 3
    v0.observe{ i => result = result*2 } // result = 6
    assert(result === 6)

    result = 1 // reset the value, but this time the ordering will go v0, v4 because of depth
    v0() = 4 // trigger recomputation, supplied value is unused
    // v0 observation: result = result*2 = 2
    // v4 observation: result = result+2 = 4
    assert(result === 4)
  }

  test("version ordering") {
    val v1 = Var(2)
    var result = 0

    val o1 = v1.observe { i => result = result + i } // result = 2
    val o2 = v1.observe { i => result = result * i * i } // result = 2 * 2 * 2 = 8
    val o3 = v1.observe { i => result = result + result + i } // result = 8 + 8 + 2 = 18

    assert(result === 18) // ensure those three things happened in sequence

    result=1 // just reset for sanity
    v1() = 3 // this should invoke o1-o3 in order:
    // result = 1 + 3 = 4
    // result = 4 * 3 * 3 = 36
    // result = 36 + 36 + 3 = 75
    assert(result === 75)
  }

  test("flatMap") {
    val us = Seq.fill(5) { U(0) }
    def short(us: Seq[Var[Int]]): Var[Int] = us match {
      case Seq(hd, tl@_*) =>
        hd flatMap {
          case 0 => short(tl)
          case i => Var(i)
        }
      case Seq() =>
        Var(-1)
    }

    val s = short(us)
    assert(Var.sample(s) === -1)
    assert(us forall (_.accessCount == 1), us map(_.accessCount) mkString ",")

    Var.sample(s); Var.sample(s)
    assert(us forall (_.accessCount == 3))
    assert(us forall (_.observerCount == 0), us map(_.observerCount.toString) mkString(","))

    // Now maintain a subscription.
    var cur = Var.sample(s)
    val sub = s.observe { cur = _ }
    assert(cur === -1)

    assert(us forall (_.observerCount == 1))

    us(0).update(123)
    assert(cur === 123)
    assert(us(0).observerCount === 1)
    assert(us drop 1 forall (_.observerCount == 0))
    
    us(1).update(333)
    assert(cur === 123)
    assert(us(0).observerCount === 1)
    assert(us drop 1 forall (_.observerCount == 0))
    
    us(0).update(0)
    assert(cur === 333)
    assert(us(0).observerCount === 1)
    assert(us(1).observerCount === 1)
    assert(us drop 2 forall (_.observerCount == 0))
    
    val f = sub.close()
    assert(f.isDefined)
    Await.result(f)
    assert(us forall (_.observerCount == 0))
  }
  
  test("Var(init)") {
    val v = Var(123)
    var cur = Var.sample(v)
    val sub = v observe { cur = _ }
    v() = 333
    assert(cur === 333)
    v() = 111
    assert(cur === 111)
    val f = sub.close()
    assert(f.isDefined)
    Await.result(f)
    v() = 100
    assert(cur === 111)
  }
  
  test("multiple observers at the same level") {
    val v = Var(2)
    val a = v map(_*2)
    val b = v map(_*3)
    
    var x, y = 0
    a observe { x = _ }
    b observe { y = _ }

    assert(x === 4)
    assert(y === 6)
    
    v() = 1
    assert(x === 2)
    assert(y === 3)
  }

  test("Var.async") {
    val x = Var[Int](333)
    val p = new Promise[Unit]
    var closed: Time = Time.Zero
    var called = 0
    val c = Closable.make { t =>
      closed = t
      p
    }
    val v = Var.async(123) { v =>
      called += 1
      x observe { v() = _ }
      c
    }
    
    assert(called === 0)
    var vv: Int = 0
    val o = v observe { vv = _ }
    assert(called === 1)
    assert(vv === 333)
    assert(closed === Time.Zero)
    
    x() = 111
    assert(vv === 111)
    assert(closed === Time.Zero)
    
    val o1 = v observe { v => () }

    val t = Time.now
    val f = o.close(t)
    assert(called === 1)
    assert(closed === Time.Zero)
    assert(f.isDone)

    val f1 = o1.close(t)
    assert(closed === t)
    assert(!f1.isDone)
    p.setDone()
    assert(f1.isDone)
  }
  
  test("Var.collect[Seq]") {
    val vars = Seq(
      Var(1),
      Var(2),
      Var(3))
    
    val coll = Var.collect(vars: Seq[Var[Int]])
    val ref = new AtomicReference[Seq[Int]]
    coll.observeTo(ref)
    assert(ref.get === Seq(1,2,3))
    
    vars(1).update(999)
    assert(ref.get === Seq(1,999,3))
  }
  
  // This is either very neat or very horrendous, 
  // depending on your point of view.
  test("Var.collect[Set]") {
    val vars = Seq(
      Var(1),
      Var(2),
      Var(3))

    val coll = Var.collect(vars map (v => v: Var[Int]) toSet)
    val ref = new AtomicReference[Set[Int]]
    coll.observeTo(ref)
    assert(ref.get === Set(1,2,3))
    
    vars(1).update(1)
    assert(ref.get === Set(1,3))
    
    vars(1).update(999)
    assert(ref.get === Set(1,999,3))
  }

  test("Var.observeUntil") {
    val v = Var[Int](123)
    // Now: Future.event(Event(v) filter(_%2 == 0))
    // 	v.changes.filter(_%2==0).toFuture
    val f = v.observeUntil(_%2 == 0)
    assert(!f.isDefined)
    v() = 333
    assert(!f.isDefined)
    v() = 332
    assert(f.isDefined)
    assert(Await.result(f) === 332)
  }

  /**
   * ensure object consistency with Var.value
   */
  test("Var.value") {
    val contents = List(1,2,3,4)
    val v1 = Var.value(contents)
    assert(Var.sample(v1) eq contents)
    v1.observe { l => assert(contents eq l) }
  }

  /**
   * Ensures that we halt observation after all observers are closed, and then
   * resume once observation returns.
   */
  test("Var observers coming and going") {
    val v = Var(11)
    val f = v.flatMap { i =>
      assert(i != 10)
      Var.value(i*2)
    }

    val c1 = f.observe { i => assert(i === 22) }
    val c2 = f.observe { i => assert(i === 22) }

    c1.close()
    c2.close()

    v() = 10 // this should not assert because it's unobserved
    v() = 22 // now it's safe to re-observe

    var observed = 3
    val c3 = f.observe { i => observed = i }

    assert(Var.sample(f) === 44)
    assert(Var.sample(v) === 22)
    assert(observed === 44)
  }

  /**
   * This test is inspired by a conversation with marius where he asked how
   * would you embody this test in Vars:
   * if (x == 0) 0 else 1/x
   *
   * The idea is that you compose Var x with maps and flatMaps that do not
   * execute until they are observed.
   *
   * It is this case that prevents caching the value of the Var before it's observed
   */
  test("Var not executing until observed") {
    val x = Var(0)
    val invertX = x map { i => 1/i }
    val result = x flatMap { i =>
      if (i == 0) Var(0) else invertX
    }

    x() = 42
    x() = 0 // this should not throw an exception because there are no observers
    x() = 1

    assert(Var.sample(result) === 1) // invertX is observed briefly
    x() = 0
    assert(Var.sample(result) === 0) // but invertX is not being observed here so we're ok
  }

  test("Var.Sampled") {
    val v = Var(123)
    v match {
      case Var.Sampled(123) =>
      case _ => fail()
    }
    
    v() = 333
    v match {
      case Var.Sampled(333) =>
      case _ => fail
    }
  }
  
  def testPropagation(typ: String, newVar: Int => Var[Int]) {
    test("Don't propagate up-to-date "+typ+"-valued Var observations") {
      val v = Var(123)
      val w = newVar(333)
      val x = v flatMap { _ => w }
      var buf = mutable.Buffer[Int]()
      x observe { v => buf += v }
      
      assert(buf === Seq(333))
      v() = 333
      assert(buf === Seq(333))
    }

    test("Do propagate out-of-date "+typ+"-valued observations") {
      val v = Var(123)
      val w1 = newVar(333)
      val w2 = newVar(444)
      val x = v flatMap {
        case 123 => w1
        case _ => w2
      }
  
      var buf = mutable.Buffer[Int]()
      x observe { v => buf += v }
  
      assert(buf === Seq(333))
      v() = 333
      assert(buf === Seq(333, 444))
      v() = 123
      assert(buf === Seq(333, 444, 333))
      v() = 333
      assert(buf === Seq(333, 444, 333, 444))
      v() = 334
      assert(buf === Seq(333, 444, 333, 444))
    }
  }

  testPropagation("constant", Var.value)
  testPropagation("variable", Var.apply(_))

  test("Race-a-Var") {
    class Counter(n: Int, u: Updatable[Int]) extends Thread {
      override def run() {
        var i = 1
        while (i < n) {
          u() = i
          i += 1
        }
      }
    }

    val N = 10000
    val a, b = Var(0)
    val c = a.flatMap(_ => b)

    @volatile var j = -1
    @volatile var n = 0
    c observe { i =>
      assert(i === j+1)
      j = i
    }

    val ac = new Counter(N, a)
    val bc = new Counter(N, b)

    ac.start()
    bc.start()
    ac.join()
    bc.join()
    
    assert(j === N-1)
  }
}
