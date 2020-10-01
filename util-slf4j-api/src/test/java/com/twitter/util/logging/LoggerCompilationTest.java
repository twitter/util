package com.twitter.util.logging;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;

public class LoggerCompilationTest {

  @Test
  public void testApply1() {
    /* By slf4j Logger */

    Logger logger =
        Logger.getLogger(LoggerFactory.getLogger(this.getClass()));

    assertEquals(
        "com.twitter.util.logging.LoggerCompilationTest",
        logger.name());
  }

  @Test
  public void testApply2() {
    /* By Class */

    Logger logger =
        Logger.getLogger(LoggerCompilationTest.class);

    assertEquals(
        "com.twitter.util.logging.LoggerCompilationTest",
        logger.name());
  }

  @Test
  public void testApply3() {
    /* By String */

    Logger logger =
        Logger.getLogger("LoggerCompilationTest");

    assertEquals(
        "LoggerCompilationTest",
        logger.name());
  }
}
