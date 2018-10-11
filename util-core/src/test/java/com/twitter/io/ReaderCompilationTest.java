/* Copyright 2015 Twitter, Inc. */
package com.twitter.io;

import com.twitter.concurrent.AsyncStream;
import java.io.ByteArrayInputStream;
import java.io.File;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class ReaderCompilationTest {

  @Test
  public void testNull() {
    assertNotNull(Readers.NULL);
  }

  @Test
  public void testNewBufReader() {
    Readers.newBufReader(Bufs.EMPTY);
  }

  @Test
  public void testReadAll() {
    Readers.readAll(Readers.newEmptyReader());
  }

  @Test
  public void testConcat() {
    Readers.concat(AsyncStream.<Reader<Buf>>empty());
  }


  @Test
  public void testPipe() {
    Pipe<Buf> w = new Pipe<>();
    w.close();
  }

  @Test
  public void testNewFileReader() {
    try {
      Readers.newFileReader(new File("a"));
    } catch (Exception x) {
      // ok
    }
  }

  @Test
  public void testNewInputStreamReader() throws Exception {
    Readers.newInputStreamReader(new ByteArrayInputStream(new byte[0]));
  }

}
