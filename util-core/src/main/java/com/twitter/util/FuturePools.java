package com.twitter.util;

import java.util.concurrent.ExecutorService;

/**
 * A Java adaptation of the {@link com.twitter.util.FuturePool} companion object.
 */
public class FuturePools {

  /**
   * @see FuturePool$#immediatePool()
   */
  public static final FuturePool IMMEDIATE_POOL = FuturePool$.MODULE$.immediatePool();

  /**
   * @see FuturePool$#unboundedPool()
   *
   * <p />
   *
   * This method is not a constant since the unbounded pool is initialized lazily.
   */
  public static FuturePool unboundedPool() {
    return FuturePool$.MODULE$.unboundedPool();
  }

  /**
   * @see FuturePool$#interruptibleUnboundedPool()
   *
   * <p />
   *
   * This method is not a constant since the unbounded pool is initialized lazily.
   */
  public static FuturePool interruptibleUnboundedPool() {
    return FuturePool$.MODULE$.interruptibleUnboundedPool();
  }

  /**
   * @see FuturePool$#apply(java.util.concurrent.ExecutorService)
   */
  public static ExecutorServiceFuturePool newFuturePool(ExecutorService executor) {
    return new ExecutorServiceFuturePool(executor);
  }

  /**
   * @see FuturePool$#interruptible(java.util.concurrent.ExecutorService)
   */
  public static InterruptibleExecutorServiceFuturePool newInterruptibleFuturePool(ExecutorService executor) {
    return new InterruptibleExecutorServiceFuturePool(executor);
  }
}
