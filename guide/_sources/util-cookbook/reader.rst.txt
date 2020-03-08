Reader
======

A reader exposes a pull-based API to model a potentially infinite stream of elements. Similar to use an
iterator to iterate through all elements from the stream, users are able to read one element at a time,
all elements are read in the order as they are fed to the reader.

Interface
---------

The most frequently used APIs are: 

.. code-block:: scala
  
    trait Reader {
      def read(): Future[Option[A]]

      def discard(): Unit

      def onClose: Future[StreamTermination]

      final def map[B](f: A => B): Reader[B]

      final def flatMap[B](f: A => Reader[B]): Reader[B]

      def flatten[B](implicit ev: A <:< Reader[B]): Reader[B]
    }

You could find a complete list of APIs from our `Reader Scaladoc <https://twitter.github.io/util/docs/com/twitter/io/Reader.html>`_. 
The APIs give you abilities to read from the reader, discard the reader after you are done reading or
you don't want others to read from it. You can use flatMap and map on a reader in a similar way you
use them on `Future` and return a new reader after applying all transforming logic you need. Use
`onClose` to check if the stream is terminated (read until the end of stream, discarded, or encountered
an exception).

read()
^^^^^^

Read the next element of this stream. The returned `Future` will resolve into `Some(e)` when the
element is available, or into `None` when the stream is fully-consumed. Reading from a discarded
reader will always return `ReaderDiscardedException`, reading from a failed reader will always resolve
in the failed Future.

Multiple outstanding reads are not allowed on the same reader, please avoid calling `read()` again until 
the future that the previous read returned has been satisfied. 

Duplicate reads are guaranteed not to happen. When properly using the Reader, it's impossible for a 
subsequent read to be satisfied before the first read, since the second read will not be issued until 
after the first read has been satisfied.

discard()
^^^^^^^^^

Always discard the reader when its output is no longer required to avoid resource leaks. Reading from a 
discarded reader will always resolve in `ReaderDiscardedException`.

Discarding a fully-consumed reader or a failed reader won't override its stream termination to `Discarded`. 
A fully-consumed reader remains at `FullyRead` and a failed reader remains at the failed future that 
terminates the stream.

onClose
^^^^^^^

A `Future` that resolves once this reader is fully-consumed (will resolves in `FullyRead`), discarded
(will resolve in `Discarded`), or failed (will resolve in the failed future that terminates the stream).
Once `onClose` is resolved in one stream termination, it won't be overridden to another one.

This is useful for any extra resource cleanup that you must do once the stream is no longer being used, like 
measuring the stream lifetime, counting the number of alive streams, counting the number of failed streams, 
calculating stream success rate, etc.

map
^^^

Construct a new Reader by lazily applying `f` to every item read from this Reader, where `f` is the function
that transforms data of type A to B. Note this operation only happens everytime a `read()` is called on the 
new Reader. 

All operations of the new Reader will be in sync with self Reader. Discarding one Reader will discard 
the other Reader. When one Reader's onClose resolves, the other Reader's onClose will be resolved 
immediately with the same value.

flatMap
^^^^^^^

Construct a new Reader (aka flat reader in the rest of the context) by lazily applying `f` to every item 
read from self Reader (aka outer reader in the rest of the context), in the order as they are fed to the outer reader, 
where `f` is the function that transforms data of type A to B and constructs a Reader[B] (aka inner reader in the rest 
of the context). Note this operation only happens when we need to create a new inner reader(explained in the next section).

When a `read()` operation is called on the flat reader, we will read from the latest constructed inner reader, and 
return the value. If the current inner reader is fully-consumed (reading from it returns `None`), instead of 
returning `None`, we will apply `f` to create the next inner reader and return the value read from the new inner reader. 
If all inner readers are fully-consumed, the flat reader will be fully-consumed. If the current inner reader is failed, 
reading from the flat reader will always resolve in the same exception as reading from the failed inner reader, and no new 
inner reader will be created. If the current inner reader is discarded, reading from the flat reader will always resolve in 
`ReaderDiscardedException` and no new inner readers will be created.

All operations of the flat reader will be in sync with the outer reader. Discarding one Reader will discard the other Reader. 
When one Reader's onClose resolves, the other Reader's onClose will be resolved immediately with the same value. 

However, after creating a flat reader via `flatMap`, the flat reader should be the only channel to interact with the stream, 
please avoid manipulating the inner reader or the outer reader by reading from it, discarding it, or failing it. This will 
affect the bahivor of the flat reader.


flatten
^^^^^^^

Converts a `Reader[Reader[B]]` into a `Reader[B]`, this is a convenient abstraction to read from a 
stream (Reader, aka outer reader in the rest of the context) of Readers (aka inner reader in the rest of the context) as if 
it were a single Reader (aka flat reader in the rest of the context). The behavior to read from the flat reader
is similar to use 2 iterators, with one outer iterator to traverse all inner readers from the stream, another 
inner iterator to traverse all elements in the inner reader, and move the outer iterator after the inner iterator 
finishes traversing an inner reader (the inner reader is fully-consumed).

When a `read()` operation is called on the flat reader, we will read from the latest constructed inner reader, and 
return the value. If the current inner reader is fully-consumed (reading from it returns `None`), instead of 
returning `None`, we will read from the next inner reader and return the value read from the new inner reader. 
If all inner readers are fully-consumed, the flat reader will be fully-consumed. If the current inner reader is failed, 
reading from the flat reader will always resolve in the same exception as reading from the failed inner reader, and no new 
inner reader will be created. If the current inner reader is discarded, reading from the flat reader will always resolve in 
`ReaderDiscardedException` and no new inner readers will be created.

All operations of the flat reader will be in sync with the outer reader. Discarding one Reader will discard 
the other Reader. When one Reader's onClose resolves, the other Reader's onClose will be resolved immediately 
with the same value. 

However, after creating a flat reader via `flatten`, the flat reader should be the only channel to interact with the stream, 
please avoid manipulating the inner readers or the outer reader by reading from it, discarding it, or failing it. This will 
affect the bahivor of the flat reader.

Example Usages
--------------

Basic operations: read, discard, onClose
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

.. code-block:: scala

    import com.twitter.io._
    import com.twitter.util._

    val r1 = Reader.value(1) // Future(Some(1))
    r1.read() // Future(None)
    r1.read() // Future(StreamTermination.FullyRead)
    r1.onClose // Future(None)
    r1.read()
  
    val r2 = Reader.value(2)
    r2.discard() // Future(StreamTermination.Discarded)
    r2.onClose // Future(ReaderDiscardedException)
    r2.read()

Construct a read-loop to consume a reader
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Given the pull-based API, the consumer is responsible for driving the computation. A very typical
code pattern to consume a reader is to use a read-loop:

.. code-block:: scala

    def consume[A](r: Reader[A]))(process: A => Future[Unit]): Future[Unit] = {
      r.read().flatMap {
        case Some(a) => process(a).before(consume(r)(process))
        case None => Future.Done // reached the end of the stream; no need to discard
      }
    }

The pattern above leverages `Future` recursion to exert back-pressure by allowing only one
outstanding read. Read must not be issued until the previous read finishes. This would always 
ensure a finer grained back-pressure in network systems allowing consumers to artificially slow down 
the producers and not rely solely on transport and IO buffering.

.. NOTE::
   One way to reason about the read-loop idiom is to view it as a subscription to a publisher (reader)
   in the Pub/Sub terminology. Perhaps the major difference between readers and traditional publishers,
   is readers only allow one subscriber (read-loop). It's generally safer to assume the reader is fully
   consumed (stream is exhausted) once its read-loop is run.

Error handling
^^^^^^^^^^^^^^

Given the read-loop above, its returned `Future` could be used to observe both successful and
unsuccessful outcomes.

.. code-block:: scala

    consume(stream)(processor).respond {
      case Return(()) => println("Consumed an entire stream successfully.")
      case Throw(e) => println(s"Encountered an error while consuming a stream: $e")
    }

If an exception is thrown during read, the reader's onClose will be resolved with the same exception, so 
alternatively, you could handle a stream error like below:

.. code-block:: scala
  
    val reader = Reader.value(1)
    reader.onClose.respond {
      case Return(()) => println("Consumed an entire stream successfully.")
      case Throw(e) => println(s"Encountered an error while consuming a stream: $e")
    }   

.. NOTE::
   Once failed, a stream can not be restarted such that all future reads will resolve into a failure.
   There is no need to discard an already failed stream.

Resource Safety
^^^^^^^^^^^^^^^

One of the important implications of readers, and streams in general, is that they are prone to resource 
leaks unless fully consumed，discarded, or failed. Specifically, readers backed with a resource, like a 
network connection, **MUST** be discarded unless already consumed (EOF observed), or failed to prevent 
connection leaks. 
