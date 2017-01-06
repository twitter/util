package com.twitter.util.security;

import com.twitter.util.Try;
import java.io.File;
import java.security.spec.PKCS8EncodedKeySpec;
import org.junit.Assert;
import org.junit.Test;

public class Pkcs8EncodedKeySpecFileCompilationTest {

  @Test
  public void testKeySpecFileRead() {
    File tempFile = new File("");
    Pkcs8EncodedKeySpecFile specFile = new Pkcs8EncodedKeySpecFile(tempFile);
    Try<PKCS8EncodedKeySpec> spec = specFile.readPkcs8EncodedKeySpec();
  }

}
