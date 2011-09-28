package com.twitter.concurrent;

import com.twitter.concurrent.ivar.State;

public class IVarField<A> {
  /**
   * This is needed because we cannot create
   * a field like this in Scala.
   */
  volatile State<A> state;
}
