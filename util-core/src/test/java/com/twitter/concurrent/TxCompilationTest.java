package com.twitter.concurrent;

import com.twitter.util.Await;
import com.twitter.util.Future;
import junit.framework.Assert;
import org.junit.Test;
import scala.Tuple2;
import scala.runtime.BoxedUnit;

public class TxCompilationTest {

  @Test
  public void testTxCreation() {
    Tx<String> a = Txs.newConstTx("44");
    Tx<BoxedUnit> b = Txs.UNIT;
    Tx<?> c = Txs.ABORTED;
    Tuple2<Tx<BoxedUnit>, Tx<String>> d = Txs.twoParty("11");
    Tx<Integer> e = Txs.newAbortedTx();

    Assert.assertNotNull(a);
    Assert.assertNotNull(b);
    Assert.assertNotNull(c);
    Assert.assertNotNull(d);
    Assert.assertNotNull(e);
  }

  @Test
  public void testCommitted() throws Exception {
    Tx<Integer> a = Txs.newConstTx(100);
    Future<Tx.Result<Integer>> futureResult = a.ack();
    Tx.Result<Integer> result = Await.result(futureResult);

    Assert.assertTrue(Txs.isCommitted(result));
    Assert.assertFalse(Txs.isAborted(result));
  }

  @Test
  public void testAborted() throws Exception {
    Tx<?> a = Txs.ABORTED;
    Future<? extends Tx.Result<?>> futureResult = a.ack();
    Tx.Result<?> result = Await.result(futureResult);

    Assert.assertTrue(Txs.isAborted(result));
    Assert.assertFalse(Txs.isCommitted(result));
  }

  @Test
  public void testResultOfNothing() throws Exception {
    Tx<String> a = Txs.newTx(null); // Abort extends Result[Nothing]
    Future<Tx.Result<String>> futureResult = a.ack();
    Tx.Result<String> result = Await.result(futureResult);

    Assert.assertTrue(Txs.isAborted(result));
    Assert.assertFalse(Txs.isCommitted(result));
  }

  @Test
  public void testResultOfT() throws Exception {
    Tx<String> a = Txs.newTx("42"); // Commit("42") extends Result[String]
    Future<Tx.Result<String>> futureResult = a.ack();
    Tx.Result<String> result = Await.result(futureResult);

    Assert.assertTrue(Txs.isCommitted(result));
    Assert.assertFalse(Txs.isAborted(result));
  }

  @Test(expected=IllegalArgumentException.class)
  public void testSample() throws Exception {
    Tx<String> a = Txs.newTx("42");
    Tx<String> b = Txs.newAbortedTx();
    Future<Tx.Result<String>> futureResultA = a.ack();
    Future<Tx.Result<String>> futureResultB = b.ack();
    Tx.Result<String> resultA = Await.result(futureResultA);
    Tx.Result<String> resultB = Await.result(futureResultB);

    Assert.assertEquals("42", Txs.sample(resultA));
    Assert.assertNotNull(Txs.sample(resultB));
  }
}
