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

Scala's `scala.util.Try`, `scala.util.Success`, and `scala.util.Failure`
were based on Twitter's `com.twitter.util.Try`, `com.twitter.util.Return`
and `com.twitter.util.Throw` and converting to and from them is sometimes
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
