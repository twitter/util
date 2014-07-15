package com.twitter.logging;

import com.twitter.logging.Logger;
import java.util.ArrayList;
import junit.framework.TestCase;
import scala.collection.immutable.Nil$;
import com.twitter.util.Function0;

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

    Function0<Handler> handler = new Function0<Handler>() {
      public Handler apply() {
        return NullHandler.get();
      }
    };

    LoggerFactoryBuilder intermediate = builder
      .addHandler(handler);
  }
}
