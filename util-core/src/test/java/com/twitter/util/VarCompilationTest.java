package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;
import scala.runtime.BoxedUnit;

import java.util.*;

public class VarCompilationTest {

  private static class OwnVar extends AbstractVar<String> {
    @Override
    public Closable observe(int depth, Var.Observer<String> obs) {
      return Closables.NOP;
    }

    private int twentyOne() {
      return 21;
    }
  }

  @Test
  public void testUpdateAndExtractAndSample() {
    ReadWriteVar<Integer> var = new ReadWriteVar<Integer>(100);
    var.update(200);
    int sample = var.sample();
    int extract = var.apply();

    Assert.assertEquals(extract, sample);
    Assert.assertEquals(200, sample);
  }

  @Test
  public void testChanges() throws Exception {
    final List<Integer> list = new ArrayList<Integer>();
    ReadWriteVar<Integer> var = Vars.newVar(100);
    var.changes().respond(new Function<Integer, BoxedUnit>() {
      @Override
      public BoxedUnit apply(Integer value) {
        list.add(value);
        return BoxedUnit.UNIT;
      }
    });
    var.update(200);
    var.update(300);
    var.update(400);

    Assert.assertArrayEquals(new Integer[] { 100, 200, 300, 400 }, list.toArray());
  }

  @Test
  public void testMapAndFlatMap() {
    ReadWriteVar<Integer> a = Vars.newVar(100);
    Var<String> b = a.map(new Function<Integer, String>() {
      @Override
      public String apply(Integer value) {
        return "v" + value;
      }
    });
    Var<String> c = b.flatMap(new Function<String, Var<String>>() {
      @Override
      public Var<String> apply(String value) {
        return new ReadWriteVar<String>(value + "sample");
      }
    });

    Assert.assertEquals("v100", b.sample());
    Assert.assertEquals("v100sample", c.sample());

    a.update(444);

    Assert.assertEquals("v444", b.sample());
    Assert.assertEquals("v444sample", c.sample());
  }

  @Test
  public void testObserve() {
    final List<Integer> list = new ArrayList<Integer>();
    ReadWriteVar<Integer> var = Vars.newVar(100);
    var.observe(0, new Var.Observer<Integer>(new Function<Integer, BoxedUnit>() {
      @Override
      public BoxedUnit apply(Integer value) {
        list.add(value);
        return BoxedUnit.UNIT;
      }
    }));

    var.update(200);
    var.update(300);

    Assert.assertArrayEquals(new Integer[] { 100, 200, 300 }, list.toArray());
  }

  @Test
  public void testAsync() {
    Var<Integer> var = Vars.async(200, new Function<Updatable<Integer>, Closable>() {
      @Override
      public Closable apply(Updatable<Integer> arg) {
        return Closables.NOP;
      }
    });
    Assert.assertEquals(200, (int) Vars.sample(var));
  }

  @Test
  public void testCollect() {
    ArrayList<Var<Integer>> list = new ArrayList<Var<Integer>>();
    list.add(Vars.newVar(100));
    list.add(Vars.newVar(200));
    list.add(Vars.newVar(300));

    Var<Collection<Integer>> vars = Vars.collect(list);

    Assert.assertArrayEquals(new Integer[] { 100, 200, 300 }, Vars.sample(vars).toArray());
  }

  @Test
  public void testOwnVarImplementation() {
    OwnVar var = new OwnVar();
    Assert.assertEquals(21, var.twentyOne());
  }

  @Test
  public void testVal() {
    Var<Integer> val = Vars.newConstantVar(100);
    Assert.assertFalse(val == null);
  }
}
