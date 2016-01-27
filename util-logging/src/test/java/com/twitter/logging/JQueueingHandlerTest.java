package com.twitter.logging;

import org.junit.Test;

import scala.Function0;

import static org.junit.Assert.*;

public class JQueueingHandlerTest {
  @Test
  public void testConstruction() {
    final Function0<StringHandler> h = StringHandler.apply();

    // Arity 1 factory works
    QueueingHandler qf1 = QueueingHandler.apply(h).apply();
    assertEquals(Integer.MAX_VALUE, qf1.maxQueueSize());

    // We can't call the arity-2 method with Function0<StringHandler>,
    // because Java can't prove that Function0<StringHandler> is an
    // instance of Function0<Handler>, so we can't properly test this
    // without scala helper code to create the Function0 for us.

    //QueueingHandler qf2 = QueueingHandler.apply(h, 1).apply();
    //assertEquals(1, qf2.maxQueueSize());

    // Arity 3 factory works
    QueueingHandler qf3 = QueueingHandler.apply(h, 1, true).apply();
    assertEquals(1, qf3.maxQueueSize());

    // Arity 1 construction works.
    QueueingHandler q1 = new QueueingHandler(h.apply());
    assertEquals(Integer.MAX_VALUE, q1.maxQueueSize());

    // Arity 2 construction works.
    QueueingHandler q2 = new QueueingHandler(h.apply(), 1);
    assertEquals(1, q2.maxQueueSize());

    // Arity 3 construction works.
    QueueingHandler q3 = new QueueingHandler(h.apply(), 1, true);
    assertEquals(1, q2.maxQueueSize());
  }
}
