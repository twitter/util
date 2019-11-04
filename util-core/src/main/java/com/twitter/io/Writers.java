/* Copyright 2015 Twitter, Inc. */
package com.twitter.io;

import java.io.OutputStream;

/**
 * Java APIs for Writer.
 *
 * @see com.twitter.io.Writer
 * @deprecated This will no longer be necessary when 2.11 support is dropped. 2019-10-04
 */
@Deprecated
public final class Writers {

  private Writers() { throw new IllegalStateException(); }

  /**
   * See {@code com.twitter.io.Writer.fromOutputStream}
   */
  public static Writer<Buf> newOutputStreamWriter(OutputStream out) {
    return Writer$.MODULE$.fromOutputStream(out);
  }

  /**
   * See {@code com.twitter.io.Writer.fromOutputStream}
   */
  public static Writer<Buf> newOutputStreamWriter(OutputStream out, int bufsize) {
    return Writer$.MODULE$.fromOutputStream(out, bufsize);
  }

}
