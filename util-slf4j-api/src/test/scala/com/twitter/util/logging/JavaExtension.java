package com.twitter.util.logging;

import scala.runtime.AbstractFunction0;

public class JavaExtension extends AbstractTraitWithLogging {
  private static final Logger LOG = Logger.getLogger(JavaExtension.class);

  /**
   * myMethod2
   * @return a "hello world" String
   */
  public String myMethod2() {
    info(new AbstractFunction0<Object>() {
      @Override
      public Object apply() {
        return "In myMethod2: using trait#info";
      }
    });

    logger().info("In myMethod2: using logger#info");
    LOG.info("In myMethod2: using LOG#info");

    return "Hello, world";
  }
}
