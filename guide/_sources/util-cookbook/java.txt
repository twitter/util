Usage from Java
===============

Scala's Functions
-----------------

Due to Scala's ``Function0`` and ``Function1`` being ``traits``, these are
difficult to instantiate directly from Java.

Use ``com.twitter.util.Function`` instead of ``scala.Function1``\s and
``scala.PartialFunction``\s. While ``com.twitter.util.Function0`` is a
replacement for ``scala.Function0``.

While the ``scala.runtime.AbstractFunction``\s look tempting, they were not
intended to be used for Java interop and we got mixed messages from the scalac
team as to whether it was a good idea to use them. The ``com.twitter.util``
versions of these work perfectly well and should be used instead of those.

There are also construction methods on the ``Function`` object such as ``func``
which improves interop with Java 8 as well as ``ofCallable`` and ``ofRunnable``.

.. code-block:: java

    import com.twitter.util.Function;
    import com.twitter.util.Function0;
    import scala.runtime.BoxedUnit;

    Function0<BoxedUnit> fn0 = new Function0<BoxedUnit>() {
      @Override
      public BoxedUnit apply() {
        println("this function is on fleek");
        return BoxedUnit.UNIT;
      }
    };

    Function<Integer, String> fn1 = new Function<Integer, String>() {
      @Override
      public String apply(Integer i) {
        return Integer.toString(i);
      }
    };

Compatibility APIs
------------------

For many common classes and methods, there are static compatibility classes that
provide Java developers with more idiomatic APIs and less ``Foo$.MODULE$``\s.
Note that Scala will sometimes create the static forwarding methods on ``Foo``
itself so it is worth looking there as well.

The class or object's scaladocs should point you towards where to look. These
are typically located at ``$module/src/main/java``.  There are also Java
compatibility tests which exist under ``$module/src/test/java/`` which can be
helpful for seeing how to use the APIs from Java. Often, the Java analog will
follow the convention of being in the same package, but with a plural name.
For example, ``com.twitter.io.Buf`` has Java's ``com.twitter.io.Bufs``.

As an example, this uses ``com.twitter.util.FuturePools`` in order to access
the ``FuturePool`` companion object methods.

.. code-block:: java

    import com.twitter.util.FuturePool;
    import com.twitter.util.FuturePools;
    import java.util.concurrent.Executors;

    FuturePool unboundedPool = FuturePools.unboundedPool();
    FuturePool execServicePool =
      FuturePools.newFuturePool(Executors.newSingleThreadExecutor());


