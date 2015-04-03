package com.twitter.io;

import org.junit.Assert;
import org.junit.Test;
import scala.Option;

import java.nio.ByteBuffer;

public class BufCompilationTest {

  private static class OwnBuf extends AbstractBuf {
    @Override
    public void write(byte[] output, int off) throws IllegalArgumentException {
      // do nothing
    }

    @Override
    public Option<ByteArray> unsafeByteArrayBuf() {
      return Option.apply(null);
    }

    @Override
    public int length() {
      return 0;
    }

    @Override
    public Buf slice(int from, int until) {
      return Bufs.EMPTY;
    }
  }

  @Test
  public void testOwnBufImplementation() {
    OwnBuf own = new OwnBuf();
    Assert.assertEquals(0, own.length());
  }

  @Test
  public void testByteArray() {
    byte bytes[] = new byte[] { 0x1, 0x2, 0x3, 0x4 };
    byte slicedBytes[] = new byte[] { 0x1, 0x2 };
    Buf a = Bufs.ownedBuf(bytes);
    Buf b = Bufs.ownedBuf(bytes, 0, 2);
    Buf c = Bufs.sharedBuf(bytes);
    Buf d = Bufs.sharedBuf(bytes, 0, 2);
    Buf e = Bufs.asByteArrayBuf(a);

    Assert.assertArrayEquals(bytes, a.copiedByteArray());
    Assert.assertArrayEquals(slicedBytes, b.copiedByteArray());
    Assert.assertArrayEquals(bytes, c.copiedByteArray());
    Assert.assertArrayEquals(slicedBytes, d.copiedByteArray());
    Assert.assertArrayEquals(bytes, e.copiedByteArray());
  }

  @Test
  public void testByteBuffer() {
    byte bytes[] = new byte[] { 0x1, 0x2, 0x3, 0x4 };
    ByteBuffer bb = ByteBuffer.wrap(bytes);
    Buf a = Bufs.ownedBuf(bb);
    Buf b = Bufs.sharedBuf(bb);
    Buf c = Bufs.asByteBufferBuf(a);

    Assert.assertArrayEquals(bytes, a.copiedByteArray());
    Assert.assertArrayEquals(bytes, b.copiedByteArray());
    Assert.assertArrayEquals(bytes, c.copiedByteArray());
  }


  @Test
  public void testUtf8Decoding() {
    byte bytes[] = "hello world!".getBytes();
    Buf ba = Bufs.ownedBuf(bytes);
    Assert.assertEquals("hello world!", Bufs.asUtf8String(ba));
  }

  @Test
  public void testStrings() {
    Buf b = Bufs.utf8Buf("hi");
    String s = Bufs.asUtf8String(b);
    Assert.assertEquals("hi", s);
  }

  @Test
  public void testExtractByteBuffer() {
    byte bytes[] = new byte[] { 0x1, 0x2, 0x3, 0x4 };
    Buf a = Bufs.ownedBuf(bytes);
    Buf b = Bufs.sharedBuf(ByteBuffer.wrap(bytes));

    Assert.assertArrayEquals(bytes, Bufs.sharedByteArray(a));
    Assert.assertArrayEquals(bytes, Bufs.ownedByteArray(a));
    Assert.assertArrayEquals(bytes, Bufs.ownedByteBuffer(b).array());
    Assert.assertArrayEquals(bytes, Bufs.sharedByteBuffer(a).array());
  }

  @Test
  public void testEqualsAndHashCodeAndSlowHexString() {
    byte bytes[] = new byte[] { 0x1, 0x2, 0x3, 0x4 };
    Buf a = Bufs.ownedBuf(bytes);
    Buf b = Bufs.ownedBuf(bytes);

    Assert.assertTrue(Bufs.equals(a, b));

    int hash = Bufs.hash(a);
    Assert.assertFalse(hash == 0);

    String hex = Bufs.slowHexString(a);
    Assert.assertEquals("01020304", hex);
  }
}
