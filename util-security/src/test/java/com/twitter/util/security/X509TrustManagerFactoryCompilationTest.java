package com.twitter.util.security;

import com.twitter.util.Try;
import java.io.File;
import javax.net.ssl.TrustManager;
import org.junit.Assert;
import org.junit.Test;

public class X509TrustManagerFactoryCompilationTest {

  @Test
  public void testGetTrustManagers() {
    File tempCertsFile = new File("");
    X509TrustManagerFactory factory = new X509TrustManagerFactory(tempCertsFile);
    Try<TrustManager[]> trustManagers = factory.getTrustManagers();
  }

}
