package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;
import scala.runtime.BoxedUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class EventAndWitnessCompilationTest {

  private static class OwnEvent extends AbstractEvent<String> {
    @Override
    public Closable register(Witness<String> s) {
      return Closable$.MODULE$.nop();
    }

    public int fiftySeven() {
      return 57;
    }
  }

  private static class OwnWitness extends AbstractWitness<String> {
    @Override
    public void notify(String note) {
      // do noting
    }

    public int sixtyOne() {
      return 61;
    }
  }

  @Test
  public void testOwnEventImplementation() {
    OwnEvent event = new OwnEvent();
    Assert.assertEquals(57, event.fiftySeven());
  }

  @Test
  public void testOwnWitnessImplementation() {
    OwnWitness witness = new OwnWitness();
    Assert.assertEquals(61, witness.sixtyOne());
  }

  @Test
  public void testWitnessedEvent() {
    final List<String> list = new ArrayList<String>();
    WitnessedEvent<String> event = Events.newEvent();
    event.register(Witnesses.newWitness(new Function<String, BoxedUnit>() {
      @Override
      public BoxedUnit apply(String arg) {
        list.add(arg);
        return BoxedUnit.UNIT;
      }
    }));
    event.notify("test42");

    Assert.assertArrayEquals(new String[] { "test42" }, list.toArray());
  }

  @Test
  public void testWitness() throws Exception {
    AtomicReference<String> reference = new AtomicReference<String>("a");
    Promise<String> promise = new Promise<String>();
    UpdatableVar<String> var = Vars.newVar("100");

    Witness<String> a = Witnesses.newWitness(reference);
    Witness<String> b = Witnesses.newWitness(promise);
    Witness<String> c = Witnesses.newWitness(var);

    a.notify("aa");
    b.notify("bb");
    c.notify("cc");

    Assert.assertEquals("aa", reference.get());
    Assert.assertEquals("bb", Await.result(promise));
    Assert.assertEquals("cc", Vars.sample(var));
  }
}
