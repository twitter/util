package com.twitter.util.security;

import com.twitter.util.Try;
import java.io.File;
import javax.net.ssl.KeyManager;
import org.junit.Assert;
import org.junit.Test;

public class Pkcs8KeyManagerFactoryCompilationTest {

  @Test
  public void testGetKeyManagers() {
    File tempCertFile = new File("");
    File tempKeyFile = new File("");
    Pkcs8KeyManagerFactory factory = new Pkcs8KeyManagerFactory(tempCertFile, tempKeyFile);
    Try<KeyManager[]> keyManagers = factory.getKeyManagers();
  }

}
