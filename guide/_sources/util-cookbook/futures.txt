Futures
=======

.. NOTE::
   See the Finagle `user guide <https://twitter.github.io/finagle/guide/Futures.html>`_
   and the `section on futures <https://twitter.github.io/effectivescala/#Twitter's%20standard%20libraries-Futures>`_
   in Effective Scala for details on concurrent programming with futures.

Conversions between Twitter's Future and Scala's Future
-------------------------------------------------------

Twitter's ``com.twitter.util.Future`` is similar to, but
`predates <https://twitter.github.io/finagle/guide/Futures.html#futures>`_,
`Scala's <https://docs.scala-lang.org/overviews/core/futures.html>`_
``scala.concurrent.Future`` and as such they are not directly compatible
(e.g. support for continuation-local variables and interruptibility).

You can use the `Twitter Bijection <https://github.com/twitter/bijection>`_
library to transform one into the other, or use a small bit of code shown
below to avoid the extra dependency.

Scala:

.. code-block:: scala

  import com.twitter.util.{Future => TwitterFuture, Promise => TwitterPromise, Return, Throw}
  import scala.concurrent.{Future => ScalaFuture, Promise => ScalaPromise, ExecutionContext}
  import scala.util.{Success, Failure}

  /** Convert from a Twitter Future to a Scala Future */
  implicit class RichTwitterFuture[A](val tf: TwitterFuture[A]) extends AnyVal {
    def asScala: ScalaFuture[A] = {
      val promise: ScalaPromise[A] = ScalaPromise()
      tf.respond {
        case Return(value) => promise.success(value)
        case Throw(exception) => promise.failure(exception)
      }
      promise.future
    }
  }

  /** Convert from a Scala Future to a Twitter Future */
  implicit class RichScalaFuture[A](val sf: ScalaFuture[A]) extends AnyVal {
    def asTwitter(implicit e: ExecutionContext): TwitterFuture[A] = {
      val promise: TwitterPromise[A] = new TwitterPromise[A]()
      sf.onComplete {
        case Success(value) => promise.setValue(value)
        case Failure(exception) => promise.setException(exception)
      }
      promise
    }
  }

Blocking or synchronous work
----------------------------

When you have work that is blocking, say I/O or a library
not written in an asynchronous style, you should use a
``com.twitter.util.FuturePool``. There are a default implementations
that can wrap an ``java.util.concurrent.ExecutorService`` as well
as a ``FuturePool.unboundedPool`` for executing I/O. Note that as the
name implies, ``unboundedPool``, is unbounded and you must take care
to not enqueue work faster than you can complete the tasks or you will
have a memory leak. You can create a bounded ``FuturePool`` via
``FuturePool.apply(ExecutorService)``.

For better Java compatibility, take a look at ``com.twitter.util.FuturePools``.

Scala:

.. code-block:: scala

    import com.twitter.util.{Future, FuturePool}

    def someIO(): String =
      // does some blocking I/O and returns a string

    val futureResult: Future[String] = FuturePool.unboundedPool {
      someIO()
    }

Java:

.. code-block:: java

    import com.twitter.util.Future;
    import com.twitter.util.FuturePools;
    import static com.twitter.util.Function.func0;

    Future<String> futureResult = FuturePools.unboundedPool().apply(
      func0(() -> someIO());
    );

Future Recursion
----------------

Often there is a need for a future to recurse and call itself.
Twitter's ``Future``\s implement something akin to tail-call elimination
which means you will not see a stack overflow with code written
in this manner.

Scala:

.. code-block:: scala

    import com.twitter.util.Future
    import java.util.concurrent.atomic.AtomicBoolean

    val done = new AtomicBoolean(false)

    def callThatReturnsFuture(): Future[Unit] = ...

    def loop(): Future[Unit] = {
      if (done.get) {
        Future.Done
      } else {
        callThatReturnsFuture().before {
          loop()
        }
      }
    }

Java:

.. code-block:: java

    import com.twitter.util.Future;
    import com.twitter.util.Function;
    import java.util.concurrent.atomic.AtomicBoolean;
    import scala.runtime.BoxedUnit;

    AtomicBoolean done = new AtomicBoolean(false);

    public Future<BoxedUnit> loop() {
      if (done.get()) {
        return Future.Done();
      } else {
        return callThatReturnsFuture().flatMap(
          new Function<BoxedUnit, Future<BoxedUnit>>() {
            public Future<BoxedUnit> apply(BoxedUnit unit) {
              return loop();
            }
          }
        );
      }
    }

A call to ``loop()`` will return a ``Future`` that will not be
satisfied until the loop exits with one of these outcomes:

1. the flag, ``done``, gets set to true; or
2. ``callThatReturnsFuture`` returns a failed ``Future``.

Limiting concurrency via semaphores and mutexes
-----------------------------------------------

Use ``com.twitter.concurrent.AsyncSemaphore`` or an
``com.twitter.concurrent.AsyncMutex`` for this. There is the succinct method
``acquireAndRun(=> Future[T])`` which, as the name implies, asynchronously
acquires a permit and runs the given function once acquired, then releases
the permit after the future is satisfied. While that should be fine for
most use cases ``acquire()`` can be used for more fine-grained control.

Scala:

.. code-block:: scala

    import com.twitter.concurrent.AsyncSemaphore

    val semaphore = new AsyncSemaphore(3)

    semaphore.acquireAndRun {
      callCatGifService(someId)
    }

    // or, with more control:
    semaphore.acquire().flatMap { permit =>
      callCatGifService(someId).ensure { permit.release() }
    }

Java:

.. code-block:: java

    import com.twitter.concurrent.AsyncSemaphore;
    import com.twitter.util.Function0;
    import com.twitter.util.Future;

    AsyncSemaphore semaphore = new AsyncSemaphore(3);
    semaphore.acquireAndRun(new Function0<Future<String>>() {
      public Future<String> apply() {
        return callCatGifService(someId);
      }
    });

Caching Futures
---------------

It's often useful to have an in-process cache of ``com.twitter.util.Future``\s.
However, it's tricky to get right especially around the handling of eviction of
failed ``Futures`` and interruption of any ``Futures`` returned. Prefer using
``com.twitter.cache.FutureCache.default()`` possibly combined with a
`Caffeine cache <https://github.com/ben-manes/caffeine>`_
for the correct behavior.

Scala:

.. code-block:: scala

    import com.github.benmanes.caffeine.cache._
    import com.twitter.util.Future

    val loader: CacheLoader[String, Future[String]] =
      new CacheLoader[String, Future[String]] {
        override def load(key: String): Future[String] = anExpensiveRpc(key)
      }

    val caffeine: LoadingCache[String, Future[String]] =
      Caffeine.newBuilder().build(loader)

    val futureCache: LoadingFutureCache[String, String] =
      new LoadingFutureCache(caffeine)

    val value: Future[String] = futureCache("key")

Java:

.. code-block:: java

    import com.github.benmanes.caffeine.cache.CacheLoader;
    import com.github.benmanes.caffeine.cache.Caffeine;
    import com.github.benmanes.caffeine.cache.LoadingCache;
    import com.github.benmanes.caffeine.cache.LoadingFutureCache;
    import com.twitter.util.Future;

    CacheLoader<String, Future<String>> loader = new CacheLoader<String, Future<String>>() {
      @Override
      public Future<String> load(String s) throws Exception {
        return anExpensiveRpc(s);
      }
    };

    LoadingCache<String, Future<String>> caffeine = Caffeine.newBuilder().build(loader);

    Function1<String, Future<String>> futureCache = new LoadingFutureCache(caffeine);

    Future<String> value = futureCache.apply("key");

