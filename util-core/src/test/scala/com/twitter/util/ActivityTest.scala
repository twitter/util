package com.twitter.util

import java.util.concurrent.atomic.AtomicReference
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ActivityTest extends FunSuite {
  test("Activity#flatMap") {
    val v = Var(Activity.Pending: Activity.State[Int])
    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    val act = Activity(v) flatMap {
      case i if i%2==0 => Activity.value(-i)
      case i => Activity.value(i)
    }
    act.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Pending))

    v() = Activity.Ok(1)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1)))

    v() = Activity.Ok(2)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1), Activity.Ok(-2)))
  }

  test("Activity#collect") {
    val v = Var(Activity.Pending: Activity.State[Int])
    val ref = new AtomicReference(Seq.empty: Seq[Try[String]])
    val act = Activity(v) collect {
      case i if i%2 == 0 => "EVEN%d".format(i)
    }
    act.values.build.register(Witness(ref))

    assert(ref.get.isEmpty)
    v() = Activity.Ok(1)
    assert(ref.get.isEmpty)
    v() = Activity.Ok(2)
    assert(ref.get == Seq(Return("EVEN2")))
    v() = Activity.Ok(3)
    assert(ref.get == Seq(Return("EVEN2")))
    v() = Activity.Ok(4)
    assert(ref.get == Seq(Return("EVEN2"), Return("EVEN4")))

    val exc = new Exception
    v() = Activity.Failed(exc)
    assert(ref.get == Seq(Return("EVEN2"), Return("EVEN4"), Throw(exc)))
  }

  test("Activity.collect") {
    val (acts, wits) = Seq.fill(10) { Activity[Int]() }.unzip
    val ref = new AtomicReference(Seq.empty: Seq[Try[Seq[Int]]])
    Activity.collect(acts).values.build.register(Witness(ref))

    for ((w, i) <- wits.zipWithIndex) {
      assert(ref.get.isEmpty)
      w.notify(Return(i))
    }

    assert(ref.get == Seq(Return(Seq.range(0, 10))))

    val exc = new Exception
    wits(0).notify(Throw(exc))
    assert(ref.get == Seq(Return(Seq.range(0, 10)), Throw(exc)))

    wits(0).notify(Return(100))
    assert(ref.get == Seq(
      Return(Seq.range(0, 10)), Throw(exc), Return(100 +: Seq.range(1, 10))))
  }

  test("Activity.future: produce an initially-pending Activity") {
    assert(Activity.future(Future.never).run.sample == Activity.Pending)
  }

  test("Activity.future: produce an Activity that completes on success of the original Future") {
    val p = new Promise[Int]
    val act = Activity.future(p)
    assert(act.run.sample == Activity.Pending)
    p.setValue(4)
    assert(act.run.sample == Activity.Ok(4))
  }

  test("Activity.future: produce an Activity that fails on failure of the original Future") {
    val p = new Promise[Unit]
    val e = new Exception("gooby pls")
    val act = Activity.future(p)
    assert(act.run.sample == Activity.Pending)
    p.setException(e)
    assert(act.run.sample == Activity.Failed(e))
  }

  test("Activity.future: produce an Activity that doesn't propagate " +
    "cancellation back to the parent future")
  {
    val p = new Promise[Unit]
    val obs = Activity.future(p).run.changes.register(Witness(_ => ()))
    Await.ready(obs.close())
    assert(!p.isDefined)
  }

  test("Exceptions are encoded") {
    val (a, w) = Activity[Int]()
    val exc1 = new Exception("1")
    val exc2 = new Exception("2")
    val exc3 = new Exception("3")

    val ref = new AtomicReference(Seq.empty: Seq[Try[Int]])
    val b = a map {
      case 111 => throw exc1
      case i => i
    } flatMap {
      case 222 => throw exc2
      case i => Activity.value(i)
    } transform {
      case Activity.Ok(333) => throw exc3
      case other => Activity(Var.value(other))
    }

    b.values.build.register(Witness(ref))

    assert(ref.get.isEmpty)

    w.notify(Return(1))
    assert(ref.get == Seq(Return(1)))

    w.notify(Return(111))
    assert(ref.get == Seq(Return(1), Throw(exc1)))

    w.notify(Return(2))
    assert(ref.get == Seq(Return(1), Throw(exc1), Return(2)))

    w.notify(Return(222))
    assert(ref.get == Seq(Return(1), Throw(exc1), Return(2), Throw(exc2)))

    w.notify(Return(3))
    assert(ref.get == Seq(Return(1), Throw(exc1), Return(2), Throw(exc2), Return(3)))

    w.notify(Return(333))
    assert(ref.get == Seq(Return(1), Throw(exc1), Return(2),
      Throw(exc2), Return(3), Throw(exc3)))
  }

  test("Activity.sample") {
    val (a, w) = Activity[Int]()

    val exc = intercept[IllegalStateException] { a.sample() }
    assert(exc.getMessage == "Still pending")

    val exc1 = new Exception
    w.notify(Throw(exc1))
    assert(intercept[Exception] { a.sample() } == exc1)

    w.notify(Return(123))
    assert(a.sample() == 123)
  }

  test("Activity.join") {
    val (a, aw) = Activity[Int]()
    val (b, bw) = Activity[String]()

    val ab = Activity.join(a, b)
    
    val ref = new AtomicReference[Seq[Activity.State[(Int, String)]]]
    ab.states.build.register(Witness(ref))
    
    assert(ref.get == Seq(Activity.Pending))
    
    aw.notify(Return(1))
    assert(ref.get == Seq(Activity.Pending, Activity.Pending))
    bw.notify(Return("ok"))
    assert(ref.get == Seq(Activity.Pending, Activity.Pending, Activity.Ok((1, "ok"))))
    
    val exc = new Exception
    aw.notify(Throw(exc))
    assert(ref.get == Seq(
      Activity.Pending, Activity.Pending, 
      Activity.Ok((1, "ok")), Activity.Failed(exc)))
  }

}
