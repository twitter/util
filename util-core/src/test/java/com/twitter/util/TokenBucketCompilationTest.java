package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;
import scala.runtime.BoxedUnit;

public class TokenBucketCompilationTest {
  @Test
  public void testBoundedBucket() throws Exception {
    TokenBucket bucket = TokenBucket.newBoundedBucket(3);
    bucket.put(3);
    Assert.assertFalse(bucket.tryGet(4));
    Assert.assertEquals(3, bucket.count());
  }

  @Test
  public void testLeakyBucket() throws Exception {
    TokenBucket bucket = TokenBucket.newLeakyBucket(Duration.fromSeconds(3), 3);
    bucket.put(3);
    bucket.tryGet(4); // don't check the value so we don't have to manipulate time
    bucket.count(); // don't check the value
  }
}
