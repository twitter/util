package com.twitter.concurrent;

import com.twitter.util.Future;
import scala.Tuple2;
import scala.runtime.BoxedUnit;

/**
 * A Java adaptation of {@link com.twitter.concurrent.Tx} companion object.
 */
public final class Txs {
  private Txs() { }

  /**
   * @see Tx$#Unit()
   */
  public final static Tx<BoxedUnit> UNIT = Txs.newConstTx(BoxedUnit.UNIT);

  /**
   * @see Tx$#aborted()
   */
  public final static Tx<?> ABORTED = Tx$.MODULE$.aborted();

  /**
   * Creates a new {@link com.twitter.concurrent.Tx} instance of given value
   * {@code message}. Returns {@link com.twitter.concurrent.Tx.Commit} if the
   * argument is not null, and {@link com.twitter.concurrent.Tx.Abort} it it
   * is null.
   */
  public static <T> Tx<T> newTx(T message) {
    return Tx$.MODULE$.apply(message);
  }

  /**
   * @see Tx$#const(Object)
   *
   * Note: Updates here must also be done at {@link com.twitter.concurrent.Tx$#const(Object)}
   */
  public static <T> Tx<T> newConstTx(final T message) {
    return new Tx<T>() {
      @Override
      public Future<Result<T>> ack() {
        Result<T> result = new Tx.Commit<T>(message);
        return Future.value(result);
      }

      @Override
      public void nack() {
        // do nothing
      }
    };
  }

  /**
   * @see Tx$#aborted()
   */
  public static <T> Tx<T> newAbortedTx() {
    return Txs.newTx(null);
  }

  /**
   * @see Tx$#twoParty(Object)
   */
  public static <T> Tuple2<Tx<BoxedUnit>, Tx<T>> twoParty(T message) {
    return Tx$.MODULE$.twoParty(message);
  }

  /**
   * Checks whether the given {@code result} is {@link com.twitter.concurrent.Tx.Commit}
   * or not.
   */
  public static <T> boolean isCommitted(Tx.Result<T> result) {
    return result instanceof Tx.Commit;
  }

  /**
   * Checks whether the given {@code result} is {@link com.twitter.concurrent.Tx.Abort}
   * or not.
   */
  public static boolean isAborted(Tx.Result<?> result) {
    return result instanceof Tx.Abort$;
  }

  /**
   * Retrieves the value of given {@code result} if it is {@link com.twitter.concurrent.Tx.Commit}
   * instance.
   */
  public static <T> T sample(Tx.Result<T> result) {
    if (Txs.isCommitted(result)) {
      return ((Tx.Commit<T>) result).value();
    } else {
      throw new IllegalArgumentException("Given result is not committed.");
    }
  }
}
