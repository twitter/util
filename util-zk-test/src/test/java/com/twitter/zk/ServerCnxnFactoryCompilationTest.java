package com.twitter.zk;

import java.net.InetAddress;
import java.net.UnknownHostException;

class ServerCnxnFactoryCompilationTest {
  static {
    try {
      ServerCnxnFactory$.MODULE$.apply(InetAddress.getLocalHost());
    } catch (UnknownHostException e) {
      //
    }
  }
}