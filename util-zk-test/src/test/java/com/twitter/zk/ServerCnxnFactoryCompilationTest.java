package com.twitter.zk;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Test;

public class ServerCnxnFactoryCompilationTest {

  @Test
  public void testServerCnxnFactory() {
    try {
      ServerCnxnFactory$.MODULE$.apply(InetAddress.getLocalHost());
    } catch (UnknownHostException e) {
      //
    }
  }

}
