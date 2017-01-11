package com.twitter.util.security;

import javax.security.auth.x500.X500Principal;
import org.junit.Test;

public class X500PrincipalInfoCompilationTest {

  @Test
  public void testCommonName() {
    X500Principal principal = new X500Principal("");
    X500PrincipalInfo info = new X500PrincipalInfo(principal);
  }

}
