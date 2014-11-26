package com.twitter.logging;

/**
 * Basic java wrapper compilation and execution test.
 */
public class LoggerCompilationTest {
  Logger LOG = Logger.get();
  Logger LOG1 = Logger.get("fonzbonz");
  Logger LOG2 = Logger.get(Logger.class);

  public void testLogging() {
    LOG2.log(Logger.ERROR(), "how about some logging");
    LOG.debug("howdy");
    LOG.debug("how bout with a string %s", "string goes here");
    LOG.error("this is an error");
    LOG1.trace("with an integer %d", 22);
    Logger.reset();
    LOG2.clearHandlers();
  }
}
