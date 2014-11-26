package com.twitter.logging;

import junit.framework.TestCase;
import scala.Function0;

// Just make sure this compiles
public class LoggerFactoryTest extends TestCase {

  public void testBuilder() {
    LoggerFactoryBuilder builder = LoggerFactory.newBuilder();
  }

  public void testFactory() {
    LoggerFactoryBuilder builder = LoggerFactory.newBuilder();

    LoggerFactoryBuilder intermediate = builder
      .node("OK")
      .level(Logger.INFO())
      .parentLevel()
      .unhandled()
      .useParents()
      .ignoreParents();

    LoggerFactory factory = intermediate.build();
  }

  public void testAddHandler() {
    LoggerFactoryBuilder builder = LoggerFactory.newBuilder();

    Function0<StringHandler> handler = StringHandler.apply();

    LoggerFactoryBuilder intermediate = builder
      .addHandler(handler);
  }
}
