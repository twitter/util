/* Copyright 2015 Twitter, Inc. */
package com.twitter.io;

import java.io.ByteArrayOutputStream;

import org.junit.Test;

public class WriterCompilationTest {

  @Test
  public void testNewOutputStreamWriter() {
    Writer.ClosableWriter w = Writers.newOutputStreamWriter(new ByteArrayOutputStream());
    w.close();
  }

}
