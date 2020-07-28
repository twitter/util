package com.twitter.logging;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import scala.Function0;

// Just make sure this compiles
public class LoggerFactoryTest {

  @Test
  public void testBuilder() {
    LoggerFactoryBuilder builder = LoggerFactory.newBuilder();
  }

  @Test
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

  @Test
  public void testAddHandler() {
    LoggerFactoryBuilder builder = LoggerFactory.newBuilder();

    Function0<StringHandler> handler = StringHandler.apply();

    LoggerFactoryBuilder intermediate = builder
      .addHandler(handler);
  }
}
