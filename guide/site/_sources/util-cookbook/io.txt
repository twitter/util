Input/Output
============

Getting the byte array out of a Buf
-----------------------------------

This can be done via ``Buf.ByteArray.Owned.extract(byteArrayBuf)``.
If you intend to mutate the data use the ``Shared`` variant instead of
``Owned``.

Scala:

.. code-block:: scala

    import com.twitter.io.Buf

    def toBytes(buf: Buf): Array[Byte] = {
      Buf.ByteArray.Owned.extract(buf)
    }

Java:

.. code-block:: java

    import com.twitter.io.Buf;

    byte[] toBytes(Buf buf) {
      return Bufs.ownedByteArray(buf);
    }


Getting the String out of a Buf.Utf8
------------------------------------

This can be done by the ``Buf.StringCoder.unapply(Buf)``
methods for the various charsets on the ``Buf`` companion object
and ``Bufs`` from Java.

Scala:

.. code-block:: scala

    import com.twitter.io.Buf

    def toString(buf: Buf): String = {
      val Buf.Utf8(str) = buf
      str
    }

Java:

.. code-block:: java

    import com.twitter.io.Buf;

    String toString(Buf buf) {
      return Bufs.asUtf8String(buf);
    }

