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

