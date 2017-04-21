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
which improves interop with Java 8's lambdas as well as ``ofCallable`` and
``ofRunnable``.

.. code-block:: java

    import com.twitter.util.Future;
    import com.twitter.util.Function;
    import com.twitter.util.Function0;
    import static com.twitter.util.Function.func;
    import static com.twitter.util.Function.func0;
    import static com.twitter.util.Function.cons;
    import scala.runtime.BoxedUnit;

    // use a static import of func0 and lambdas for interop with
    // Scala's Function0 and call-by-name method arguments.
    Function0<String> fn0 = func0(() -> "example");
    // and used inline:
    Future<String> fs = Future.apply(func0(() -> "example"));

    // use a static import of func and lambdas for interop with
    // Scala's Function1.
    Function<String, Integer> fn = func(s -> s.length());
    // and used inline, with method references:
    Future<Integer> fi = fs.map(func(String::length()));

    // use a static import of cons and lambdas for interop with
    // Scala Function1's that return Unit.
    Function<String, BoxedUnit> consumer = cons(s -> System.out.println(s));
    // and used inline, with method references:
    Future<String> f2 = fs.onSuccess(cons(System.out::println));

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


