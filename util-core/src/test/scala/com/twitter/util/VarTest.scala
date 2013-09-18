package com.twitter.util.exp

// Remove when moving out of exp:
import com.twitter.util.{Await, Closable, Future, Time}

import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class VarTest extends FunSuite {
  private case class U[T](init: T) extends UpdatableVar[T] {
    value = init

    var n = 0
    var count = 0

    override def observe(d: Int, o: T => Unit) = {
      count += 1
      n += 1
      Closable.all(
        super.observe(d, o),
        Closable.make { deadline => 
          n -= 1
          Future.Done
        })
    }
  }

  test("Var.map") {
    val v = Var(123)
    val s = v map (_.toString)
    assert(s() === "123")
    v() = 8923
    assert(s() === "8923")
    
    var buf = mutable.Buffer[String]()
    s observe { v => buf += v }
    assert(buf.toSeq === Seq("8923"))
    v() = 111
    assert(buf.toSeq === Seq("8923", "111"))
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
    assert(s() === -1)
    assert(us forall (_.count == 1), us map(_.count) mkString ",")

    s(); s()
    assert(us forall (_.count == 3))
    assert(us forall (_.n == 0), us map(_.n.toString) mkString(","))

    // Now maintain a subscription.
    var cur = s()
    val sub = s.observe { cur = _ }
    assert(cur === -1)

    assert(us forall (_.n == 1))

    us(0).update(123)
    assert(cur === 123)
    assert(us(0).n === 1)
    assert(us drop 1 forall (_.n == 0))
    
    us(1).update(333)
    assert(cur === 123)
    assert(us(0).n === 1)
    assert(us drop 1 forall (_.n == 0))
    
    us(0).update(0)
    assert(cur === 333)
    assert(us(0).n === 1)
    assert(us(1).n === 1)
    assert(us drop 2 forall (_.n == 0))
    
    val f = sub.close()
    assert(f.isDefined)
    Await.result(f)
    assert(us forall (_.n == 0))
  }
  
  test("Var(init)") {
    val v = Var(123)
    var cur = v()
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
  
  test("Var.memo") {
    val u = U(0)
    assert(u.count === 0)
    val m = u.memo()
    assert(u.count === 0)
    
    var i1, i2: Int = -1

    val o1 = m.observe { i1 = _ }
    assert(u.count === 1)
    assert(u.n === 1)
    val o2 = m.observe { i2 = _ }
    assert(u.count === 1)
    assert(u.n === 1)
    
    u() = 123
    assert(i1 === 123)
    assert(i2 === 123)
    
    Await.result(o2.close())
    assert(u.count === 1)
    assert(u.n === 1)
    
    Await.result(o1.close())
    assert(u.count === 1)
    assert(u.n === 0)
    
    assert(u() === 123)
    assert(u.count === 2)
    assert(u.n === 0)
  }
}
