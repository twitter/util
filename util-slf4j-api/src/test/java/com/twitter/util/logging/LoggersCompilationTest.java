package com.twitter.util.logging;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public final class LoggersCompilationTest {

  @Test
  public void testApply1() {
    /* By slf4j Logger */

    Logger logger =
        Loggers.getLogger(LoggerFactory.getLogger(this.getClass()));

    assertEquals(
        "com.twitter.util.logging.LoggersCompilationTest",
        logger.name());
  }

  @Test
  public void testApply2() {
    /* By Class */

    Logger logger =
        Loggers.getLogger(LoggersCompilationTest.class);

    assertEquals(
        "com.twitter.util.logging.LoggersCompilationTest",
        logger.name());
  }

  @Test
  public void testApply3() {
    /* By String */

    Logger logger =
        Loggers.getLogger("LoggersCompilationTest");

    assertEquals(
        "LoggersCompilationTest",
        logger.name());
  }
}
