package com.twitter.util;

import com.twitter.util.Awaitable.CanAwait;
import scala.runtime.BoxedUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java's analog of Scala's {@link com.twitter.util.CloseAwaitably}.
 *
 * <p>
 * In order to make it more Java-friendly, the abstract method
 * {@link com.twitter.util.AbstractCloseAwaitably#onClose} is used instead of a higher-order
 * function in Scala version.
 * </p>
 * <p>
 * An example usage looks as follows.
 * </p>
 * <pre>
 * {@code
 *
 *   class A extends AbstractCloseAwaitably {
 *     public Future<BoxedUnit> onClose(Time deadline) {
 *       return Future.Done();
 *     }
 *   }
 *
 * }
 * </pre>
 */
public abstract class AbstractCloseAwaitably extends AbstractClosable implements Awaitable<BoxedUnit> {

  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Promise<BoxedUnit> onClose = new Promise<BoxedUnit>();

  /**
   * An on-close handler that is executed when this {@link com.twitter.util.Closable}
   * is being closed.
   */
  public abstract Future<BoxedUnit> onClose(Time deadline);

  /**
   * @see com.twitter.util.Closable#close()
   */
  @Override
  public Future<BoxedUnit> close(Time deadline) {
    if (closed.compareAndSet(false, true)) {
      onClose.become(onClose(deadline));
    }
    return onClose;
  }

  /**
   * @see com.twitter.util.Awaitable#ready(Duration, com.twitter.util.Awaitable.CanAwait)
   */
  @Override
  public Awaitable<BoxedUnit> ready(Duration timeout, CanAwait permit) throws TimeoutException, InterruptedException {
    onClose.ready(timeout, permit);
    return this;
  }

  /**
   * @see com.twitter.util.Awaitable#result(Duration, com.twitter.util.Awaitable.CanAwait)
   */
  @Override
  public BoxedUnit result(Duration timeout, CanAwait permit) throws Exception {
    return onClose.result(timeout, permit);
  }

  /**
   * @see com.twitter.util.Awaitable#isReady(com.twitter.util.Awaitable.CanAwait)
   */
  @Override
  public boolean isReady(CanAwait permit) {
    return onClose.isReady(permit);
  }
}
