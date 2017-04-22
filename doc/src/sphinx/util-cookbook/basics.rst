Basics
======

Getting the result out of a Try
-------------------------------

While trivial with Scala's pattern matching it is
less obvious from Java.

Scala:

.. code-block:: scala

    import com.twitter.util.{Return, Throw, Try}

    def extract(t: Try[String]): String =
      t match {
        case Return(ret) => ret
        case Throw(ex) => ex.toString
      }

Java:

.. code-block:: java

    import com.twitter.util.Return;
    import com.twitter.util.Throw;
    import com.twitter.util.Try;

    String extract(Try<String> t) {
      if (t.isReturn()) {
        return t.get();
      } else {
        return t.throwable().toString();
      }
    }

Conversions between Twitter's Try and Scala's Try
-------------------------------------------------

Scala's ``scala.util.Try``, ``scala.util.Success``, and ``scala.util.Failure``
were based on Twitter's ``com.twitter.util.Try``, ``com.twitter.util.Return``
and ``com.twitter.util.Throw`` and converting to and from them is sometimes
necessary.

Scala:

.. code-block:: scala

    import com.twitter.util.{Try => TwitterTry}

    def toScalaTry(twitterTry: TwitterTry[String]): scala.util.Try[String] =
      twitterTry.asScala

    def fromScalaTry(scalaTry: scala.util.Try[String]): TwitterTry[String] =
      TwitterTry.fromScala(scalaTry)

Java:

.. code-block:: java

    import com.twitter.util.Try;

    scala.util.Try<String> toScalaTry(Try<String> twitterTry) {
      return twitterTry.asScala();
    }

    Try<String> fromScalaTry(scala.util.Try<String> scalaTry) {
      return Try.fromScala(scalaTry);
    }

Controlling Time for tests
--------------------------

The current time, ``com.twitter.util.Time.now``, can be manipulated for
writing deterministic tests with ``Time.withTimeAt``, ``Time.withCurrentTimeFrozen``,
``Time.withTimeFunction``, and ``Time.sleep``.

While ``Time.now`` is not a "global", it is properly propagated through
to other code via the standard usage of ``Locals`` throughout `util`.
Specifically, code using ``Future``\s, ``FuturePool``\s, and ``MockTimer``\s
will see the manipulated ``Time.now``.

Scala:

.. code-block:: scala

    import com.twitter.conversions.time._
    import com.twitter.util.{FuturePool, Time}

    val time = Time.fromMilliseconds(123456L)
    Time.withTimeAt(time) { timeControl =>
      assert(Time.now == time)

      // you can control time via the `TimeControl` instance.
      timeControl.advance(2.seconds)
      FuturePool.unboundedPool {
        assert(Time.now == time + 2.seconds)
      }
    }

Java:

.. code-block:: java

    import com.twitter.util.Duration;
    import com.twitter.util.FuturePool;
    import com.twitter.util.Time;
    import static com.twitter.util.Function.func;
    import static com.twitter.util.Function.func0;

    Time time = Time.fromMilliseconds(123456L);
    Time.withTimeAt(time, func(timeControl -> {
      assert(Time.now().equals(time));

      // you can control time via the `TimeControl` instance.
      timeControl.advance(Duration.fromSeconds(2));
      FuturePools.unboundedPool().apply(func0(() -> {
        assert(Time.now().equals(time.plus(Duration.fromSeconds(2))));
        return BoxedUnit.UNIT;
      }));
      return null;
    }));

Controlling Timers
~~~~~~~~~~~~~~~~~~~

For the same reasons you would want to control ``Time`` deterministically,
you may have code that relies on a ``Timer`` and need the same abilities.
Enter ``com.twitter.util.MockTimer``, which plays well with the ``Time``
manipulation methods discussed above. It adds a method to ``Timer``, ``tick()``,
which runs all ``TimerTasks`` that are past their deadline.

Scala:

.. code-block:: scala

    import com.twitter.conversions.time._
    import com.twitter.util.{Future, MockTimer, Time}

    Time.withWithCurrentTimeFrozen { timeControl =>
      val timer = new MockTimer()
      // schedule some work for later
      val f: Future[String] = timer.doLater(1.millisecond) {
        // some work, and return a status string
        "done"
      }
      // the task will not execute until we say so.
      assert(!f.isDefined)

      // move time forward, past when the task should be done
      timeControl.advance(2.milliseconds)
      // the task will not execute until we `Timer.tick()`
      assert(!f.isDefined)

      // let the Timer run and the task will run
      timer.tick()
      assert(f.isDefined)
    }

.. code-block:: java

    import com.twitter.util.Duration;
    import com.twitter.util.Future;
    import com.twitter.util.MockTimer;
    import com.twitter.util.Time;
    import static com.twitter.util.Function.func;
    import static com.twitter.util.Function.func0;

    Time.withCurrentTimeFrozen(func(timeControl -> {
      MockTimer timer = new MockTimer();
      Future<String> f = timer.doLater(Duration.fromMilliseconds(1),
        func0(() -> "done"));
      // the task will not execute until we say so.
      assert(!f.isDefined());

      // move time forward, past when the task should be done
      timeControl.advance(Duration.fromMilliseconds(2));
      // the task will not execute until we `Timer.tick()`
      assert(!f.isDefined());

      // let the Timer run and the task will run
      timer.tick();
      assert(!f.isDefined());
      return null;
    }));

