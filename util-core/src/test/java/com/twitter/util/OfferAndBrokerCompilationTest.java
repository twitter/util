package com.twitter.util;

import com.twitter.concurrent.*;
import org.junit.Assert;
import org.junit.Test;
import scala.runtime.AbstractFunction0;
import scala.runtime.BoxedUnit;

import java.util.ArrayList;

public class OfferAndBrokerCompilationTest {

  private static class OwnOffer extends AbstractOffer<String> {
    @Override
    public Future<Tx<String>> prepare() {
      return Future.value(Txs.newConstTx("32"));
    }

    public int ten() {
      return 10;
    }
  }

  @Test
  public void testOwnOfferImplementation() {
    OwnOffer own = new OwnOffer();
    Assert.assertEquals(10, own.ten());
  }

  @Test
  public void testOfferCreation() {
    Offer<?> a = Offers.NEVER;
    Offer<Integer> b = Offers.newConstOffer(100);
    Offer<String> c = Offers.newConstOffer(new Function0<String>() {
      @Override
      public String apply() {
        return "33";
      }
    });
    Offer<BoxedUnit> d = Offers.newTimeoutOffer(Duration.fromMicroseconds(1), new NullTimer());

    Assert.assertNotNull(a);
    Assert.assertNotNull(b);
    Assert.assertNotNull(c);
    Assert.assertNotNull(d);
  }

  @Test
  public void testChoose() {
    ArrayList<Offer<String>> offers = new ArrayList<Offer<String>>();
    offers.add(Offers.newConstOffer("911"));
    offers.add(Offers.newConstOffer("112"));
    Offer<String> c = Offers.choose(offers);

    Assert.assertNotNull(c);
  }

  @Test
  public void testConst() throws Exception {
    Offer<Integer> a = Offers.newConstOffer(100);
    Offer<String> b = a.mapConstFunction(new AbstractFunction0<String>() {
      @Override
      public String apply() {
        return "serenity";
      }
    });
    Offer<Integer> c = b.mapConst(200);

    Assert.assertEquals("serenity", Await.result(b.sync()));
    Assert.assertEquals(200, (int) Await.result(c.sync()));
  }

  @Test
  public void testBrokerSendAndReceive() throws Exception {
    Broker<Integer> a = new Broker<Integer>();
    Offer<Integer> b = a.recv();
    a.send(100).sync();

    Assert.assertEquals(100, (int) Await.result(b.sync()));
  }

  @Test
  public void testBrokerApi() throws Exception {
    Broker<String> a = new Broker<String>();
    Future<String> b = a.recvAndSync();
    Future<BoxedUnit> c = a.sendAndSync("a");

    Assert.assertEquals("a", Await.result(b));
    Assert.assertEquals(BoxedUnit.UNIT, Await.result(c));
  }
}
