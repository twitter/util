/* Copyright 2015 Twitter, Inc. */
package com.twitter.io;

import java.io.OutputStream;

/**
 * Java APIs for Writer.
 *
 * @see com.twitter.io.Writer
 */
public final class Writers {

  private Writers() { throw new IllegalStateException(); }

  /**
   * See {@code com.twitter.io.Writer.fromOutputStream}
   */
  public static Writer.ClosableWriter<Buf> newOutputStreamWriter(OutputStream out) {
    return Writer$.MODULE$.fromOutputStream(out);
  }

  /**
   * See {@code com.twitter.io.Writer.fromOutputStream}
   */
  public static Writer.ClosableWriter<Buf> newOutputStreamWriter(OutputStream out, int bufsize) {
    return Writer$.MODULE$.fromOutputStream(out, bufsize);
  }

}
