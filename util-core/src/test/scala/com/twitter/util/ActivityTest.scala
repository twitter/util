package com.twitter.util

import com.twitter.conversions.DurationOps._
import java.util.concurrent.atomic.AtomicReference
import org.scalatest.funsuite.AnyFunSuite

class ActivityTest extends AnyFunSuite {
  test("Activity#flatMap") {
    val v = Var(Activity.Pending: Activity.State[Int])
    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    val act = Activity(v) flatMap {
      case i if i % 2 == 0 => Activity.value(-i)
      case i => Activity.value(i)
    }
    act.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Pending))

    v() = Activity.Ok(1)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1)))

    v() = Activity.Ok(2)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1), Activity.Ok(-2)))
  }

  test("Activity#handle") {
    val e = new Exception
    case class E(x: Int) extends Exception

    val (a, w) = Activity[Int]()

    val ref = new AtomicReference[Activity.State[Int]]
    a.handle { case E(x) => x }.states.register(Witness(ref))

    assert(ref.get == Activity.Pending)
    w.notify(Return(1))
    assert(ref.get == Activity.Ok(1))
    w.notify(Throw(E(2)))
    assert(ref.get == Activity.Ok(2))
    w.notify(Throw(e))
    assert(ref.get == Activity.Failed(e))
  }

  test("Activity#collect") {
    val v = Var(Activity.Pending: Activity.State[Int])
    val ref = new AtomicReference(Seq.empty: Seq[Try[String]])
    val act = Activity(v) collect {
      case i if i % 2 == 0 => "EVEN%d".format(i)
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
    assert(ref.get == Seq(Return(Seq.range(0, 10)), Throw(exc), Return(100 +: Seq.range(1, 10))))
  }

  test("Activity.future: produce an initially-pending Activity") {
    assert(Activity.future(Future.never).run.sample() == Activity.Pending)
  }

  test("Activity.future: produce an Activity that completes on success of the original Future") {
    val p = new Promise[Int]
    val act = Activity.future(p)
    assert(act.run.sample() == Activity.Pending)
    p.setValue(4)
    assert(act.run.sample() == Activity.Ok(4))
  }

  test("Activity.future: produce an Activity that fails on failure of the original Future") {
    val p = new Promise[Unit]
    val e = new Exception("gooby pls")
    val act = Activity.future(p)
    assert(act.run.sample() == Activity.Pending)
    p.setException(e)
    assert(act.run.sample() == Activity.Failed(e))
  }

  test(
    "Activity.future: produce an Activity that doesn't propagate " +
      "cancellation back to the parent future"
  ) {
    val p = new Promise[Unit]
    val obs = Activity.future(p).run.changes.register(Witness((_: Any) => ()))
    Await.result(obs.close(), 5.seconds)
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
    assert(ref.get == Seq(Return(1), Throw(exc1), Return(2), Throw(exc2), Return(3), Throw(exc3)))
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
    assert(
      ref.get == Seq(
        Activity.Pending,
        Activity.Pending,
        Activity.Ok((1, "ok")),
        Activity.Failed(exc)
      )
    )
  }

  test("Activity#stabilize will update until an Ok event") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Pending)

    val a = Activity(v)

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    a.stabilize.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Pending))

    v() = Activity.Failed(ex)
    assert(ref.get == Seq(Activity.Pending, Activity.Failed(ex)))

    v() = Activity.Ok(1)
    assert(ref.get == Seq(Activity.Pending, Activity.Failed(ex), Activity.Ok(1)))
  }

  test("Activity#stabilize will stop updating on non-Oks after an Ok event") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Pending)

    val a = Activity(v)

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    a.stabilize.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Pending))

    v() = Activity.Ok(1)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1)))

    v() = Activity.Failed(ex)
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1), Activity.Ok(1)))

    v() = Activity.Pending
    assert(ref.get == Seq(Activity.Pending, Activity.Ok(1), Activity.Ok(1), Activity.Ok(1)))
  }

  test("Activity#stabilize will also keep the old event on non-Ok after it starts in Ok") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Ok(1))

    val a = Activity(v)

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    a.stabilize.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Ok(1)))

    v() = Activity.Failed(ex)
    assert(ref.get == Seq(Activity.Ok(1), Activity.Ok(1)))
  }

  test("Activity#stabilize can still transition to a different Ok") {
    val v = Var[Activity.State[Int]](Activity.Ok(1))
    val a = Activity(v)

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    a.stabilize.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Ok(1)))

    v() = Activity.Ok(2)
    assert(ref.get == Seq(Activity.Ok(1), Activity.Ok(2)))
  }

  test("Activity#stabilize will keep the old stable value when not observed") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Ok(1))
    val a = Activity(v).stabilize

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    val c = a.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Ok(1)))
    Await.result(c.close(), 5.seconds)

    assert(a.sample() == 1)
  }

  test(
    "Activity#stabilize will ignore new values until it is observed again after deregistration") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Ok(1))
    val a = Activity(v).stabilize

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    val c = a.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Ok(1)))
    Await.result(c.close(), 5.seconds)

    assert(a.sample() == 1)

    // this value does not get cached
    v() = Activity.Ok(2)

    // this is the true value of the underlying item
    v() = Activity.Pending

    // uses the cached value
    assert(a.sample() == 1)
  }

  test(
    "Activity#stabilize will see new valid values immediately on observation after deregistration") {
    val ex = new Exception
    val v = Var[Activity.State[Int]](Activity.Ok(1))
    val a = Activity(v).stabilize

    val ref = new AtomicReference[Seq[Activity.State[Int]]]
    val c = a.states.build.register(Witness(ref))

    assert(ref.get == Seq(Activity.Ok(1)))
    Await.result(c.close(), 5.seconds)

    assert(a.sample() == 1)

    v() = Activity.Ok(2)
    assert(a.sample() == 2)
  }

  class FakeEvent extends Event[Activity.State[Int]] {
    var count = 0
    var w: Witness[Activity.State[Int]] = null

    def register(witness: Witness[Activity.State[Int]]): Closable = {
      count += 1
      w = witness
      Closable.make { (deadline: Time) =>
        count -= 1
        w = null
        Future.Done
      }
    }
  }

  test("Activity.apply(Event) doesn't leak when sampled") {
    val evt = new FakeEvent
    val activity = Activity(evt)
    assert(evt.count == 0)

    assert(activity.run.sample() == Activity.Pending)
    assert(evt.count == 0)
  }

  test("Activity.apply(Event) doesn't leak with derived events") {
    val evt = new FakeEvent
    val activity = Activity(evt)
    assert(evt.count == 0)

    val derivedEvt = activity.run.changes
    assert(evt.count == 0)
    val ref = new AtomicReference[Activity.State[Int]]()
    val closable = derivedEvt.register(Witness(ref))
    assert(evt.count == 1)
    assert(ref.get == Activity.Pending)
    evt.w.notify(Activity.Ok(1))
    assert(Activity.sample(activity) == 1)
    assert(evt.count == 1)
    Await.result(closable.close(), 5.seconds)
    assert(evt.count == 0)
  }

  test("Activity.apply(Event) doesn't preserve events after it stops watching") {
    val evt = new FakeEvent
    val activity = Activity(evt)

    val derivedEvt = activity.run.changes
    val ref = new AtomicReference[Activity.State[Int]]()
    val closable = derivedEvt.register(Witness(ref))
    evt.w.notify(Activity.Ok(1))
    assert(Activity.sample(activity) == 1)
    Await.result(closable.close(), 5.seconds)

    assert(activity.run.sample() == Activity.Pending)
  }
}
