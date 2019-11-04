package com.twitter.io;

import java.nio.ByteBuffer;

/**
 * A Java adaptation of the {@link com.twitter.io.Buf} companion object.
 *
 * @deprecated This will no longer be necessary when 2.11 support is dropped. 2019-10-04
 */
@Deprecated
public final class Bufs {
  private Bufs() { }

  /**
   * @see Buf$#Empty()
   */
  public static final Buf EMPTY = Buf$.MODULE$.Empty();

  /**
   * @see Buf.ByteArray.Shared#apply(byte[])
   */
  public static Buf sharedBuf(byte... bytes) {
    return Buf$ByteArray$Shared$.MODULE$.apply(bytes);
  }

  /**
   * @see Buf.ByteArray.Shared#apply(byte[], int, int)
   */
  public static Buf sharedBuf(byte bytes[], int begin, int end) {
    return Buf$ByteArray$Shared$.MODULE$.apply(bytes, begin, end);
  }

  /**
   * @see Buf.ByteArray.Owned$#apply(byte[])
   */
  public static Buf ownedBuf(byte... bytes) {
    return Buf$ByteArray$Owned$.MODULE$.apply(bytes);
  }

  /**
   * @see Buf.ByteArray.Owned#apply(byte[], int, int)
   */
  public static Buf ownedBuf(byte bytes[], int begin, int end) {
    return Buf$ByteArray$Owned$.MODULE$.apply(bytes, begin, end);
  }

  /**
   * @see Buf.ByteArray$#coerce(Buf)
   */
  public static Buf.ByteArray asByteArrayBuf(Buf buf) {
    return Buf.ByteArray$.MODULE$.coerce(buf);
  }

  /**
   * @see Buf.ByteArray.Shared#extract(Buf)
   */
  public static byte[] sharedByteArray(Buf buf) {
    return Buf$ByteArray$Shared$.MODULE$.extract(buf);
  }

  /**
   * @see Buf.ByteArray.Owned#extract(Buf)
   */
  public static byte[] ownedByteArray(Buf buf) {
    return Buf$ByteArray$Owned$.MODULE$.extract(buf);
  }

  /**
   * @see Buf.ByteBuffer.Shared#apply(java.nio.ByteBuffer)
   */
  public static Buf sharedBuf(ByteBuffer buf) {
    return Buf$ByteBuffer$Shared$.MODULE$.apply(buf);
  }

  /**
   * @see Buf.ByteBuffer.Owned#apply(java.nio.ByteBuffer)
   */
  public static Buf ownedBuf(ByteBuffer buf) {
    return Buf$ByteBuffer$Owned$.MODULE$.apply(buf);
  }

  /**
   * @see Buf.ByteBuffer#coerce(Buf)
   */
  public static Buf.ByteBuffer asByteBufferBuf(Buf buf) {
    return Buf.ByteBuffer$.MODULE$.coerce(buf);
  }

  /**
   * @see Buf.Utf8#unapply(Buf)
   */
  public static String asUtf8String(Buf buf) {
    return Buf.Utf8$.MODULE$.unapply(buf).get();
  }

  /**
   * @see Buf.ByteBuffer.Shared#extract(Buf)
   */
  public static ByteBuffer sharedByteBuffer(Buf buf) {
    return Buf$ByteBuffer$Shared$.MODULE$.extract(buf);
  }

  /**
   * @see Buf.ByteBuffer.Owned#extract(Buf)
   */
  public static ByteBuffer ownedByteBuffer(Buf buf) {
    return Buf$ByteBuffer$Owned$.MODULE$.extract(buf);
  }

  /**
   * @see Buf$#equals(Buf, Buf)
   */
  public static boolean equals(Buf x, Buf y) {
    return Buf$.MODULE$.equals(x, y);
  }

  /**
   * @see Buf$#hash(Buf)
   */
  public static int hash(Buf buf) {
    return Buf$.MODULE$.hash(buf);
  }

  /**
   * @see Buf$#slowHexString(Buf)
   */
  public static String slowHexString(Buf buf) {
    return Buf$.MODULE$.slowHexString(buf);
  }

  /**
   * @see Buf.Utf8.apply(String)
   */
  public static Buf utf8Buf(String s) {
    return Buf.Utf8$.MODULE$.apply(s);
  }

  /** @see Buf.Utf8 */
  public static final Buf.StringCoder UTF_8 = Buf.Utf8$.MODULE$;

  /** @see Buf.Utf16 */
  public static final Buf.StringCoder UTF_16 = Buf.Utf16$.MODULE$;

  /** @see Buf.Utf16BE */
  public static final Buf.StringCoder UTF_16BE = Buf.Utf16BE$.MODULE$;

  /** @see Buf.Utf16LE */
  public static final Buf.StringCoder UTF_16LE = Buf.Utf16LE$.MODULE$;

  /** @see Buf.Iso8859_1 */
  public static final Buf.StringCoder ISO_8859_1 = Buf.Iso8859_1$.MODULE$;

  /** @see Buf.UsAscii */
  public static final Buf.StringCoder US_ASCII = Buf.UsAscii$.MODULE$;

}
