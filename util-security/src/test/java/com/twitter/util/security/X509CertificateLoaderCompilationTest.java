package com.twitter.util.security.exp;

import java.io.File;
import java.security.cert.X509Certificate;
import org.junit.Assert;
import org.junit.Test;
import scala.Option;

public class X509CertificateLoaderCompilationTest {

  @Test
  public void testCertificateLoaderCompilation() {
    File tempFile = new File("");
    Option<X509Certificate> cert = X509CertificateLoader.load(tempFile);
  }

}
