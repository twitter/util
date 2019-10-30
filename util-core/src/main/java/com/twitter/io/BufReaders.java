package com.twitter.io;

import com.twitter.util.Future;

/**
 * Better Java APIs of BufReader for Scala 2.11.
 * This will be removed when drop Scala 2.11 support.
 *
 * @see com.twitter.io.BufReader
 */
public final class BufReaders {

  private BufReaders() {
    throw new IllegalStateException();
  }

  /**
   * See {@code com.twitter.io.BufReader.readAll}.
   */
  public static Future<Buf> readAll(Reader<Buf> r) {
    return BufReader$.MODULE$.readAll(r);
  }
}
