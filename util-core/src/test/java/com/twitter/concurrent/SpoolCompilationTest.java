package com.twitter.concurrent;

import com.twitter.util.Await;
import com.twitter.util.Future;
import org.junit.Assert;
import org.junit.Test;
import scala.collection.JavaConversions;

import java.util.Arrays;
import java.util.Collection;

public class SpoolCompilationTest {
  private static class OwnSpool extends AbstractSpool<String> {
    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public Future<Spool<String>> tail() {
      return Future.value(Spools.<String>newEmptySpool());
    }

    @Override
    public String head() {
      return "spool";
    }
  }

  @Test
  public void testOwnSpool() {
    Spool<String> a = new OwnSpool();
    Assert.assertFalse(a.isEmpty());
    Assert.assertEquals("spool", a.head());
  }

  @Test
  public void testSpoolCreation() {
    Spool<String> a = Spools.newEmptySpool();
    Spool<?> b = Spools.EMPTY;
    Spool<String> c = Spools.newSpool(Arrays.asList("a", "b"));

    Assert.assertNotNull(a);
    Assert.assertNotNull(b);
    Assert.assertNotNull(c);
  }

  @Test
  public void testSpoolConcat() throws Exception {
    Spool<String> a = Spools.newSpool(Arrays.asList("a"));
    Spool<String> b = Spools.newSpool(Arrays.asList("b"));
    Spool<String> c = Spools.newSpool(Arrays.asList("c"));

    Spool<String> ab = a.concat(b);
    Spool<String> abNothing = ab.concat(Spools.<String>newEmptySpool());
    Spool<String> abc = Await.result(ab.concat(Future.value(c)));

    Collection<String> listA = JavaConversions.seqAsJavaList(Await.result(ab.toSeq()));
    Collection<String> listB = JavaConversions.seqAsJavaList(Await.result(abNothing.toSeq()));
    Collection<String> listC = JavaConversions.seqAsJavaList(Await.result(abc.toSeq()));

    Assert.assertArrayEquals(new String[] { "a", "b"}, listA.toArray());
    Assert.assertArrayEquals(new String[] { "a", "b"}, listB.toArray());
    Assert.assertArrayEquals(new String[] { "a", "b", "c"}, listC.toArray());
  }
}
