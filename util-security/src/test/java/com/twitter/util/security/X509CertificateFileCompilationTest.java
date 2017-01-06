package com.twitter.util.security;

import com.twitter.util.Try;
import java.io.File;
import java.security.cert.X509Certificate;
import org.junit.Assert;
import org.junit.Test;

public class X509CertificateFileCompilationTest {

  @Test
  public void testCertificateFileRead() {
    File tempFile = new File("");
    X509CertificateFile certFile = new X509CertificateFile(tempFile);
    Try<X509Certificate> cert = certFile.readX509Certificate();
  }

}
