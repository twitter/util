package com.twitter.logging;

import scala.Function0;

/**
 * Basic compilation test for calling {{{@link ScribeHandlers}}}
 * methods from java
 */
public class ScribeHandlersCompilationTest {
  public void testScribeHandlers() {
     Function0<ScribeHandler> scribeHandlerFunc =
         ScribeHandlers.apply("category", BareFormatter$.MODULE$);
  }
}
