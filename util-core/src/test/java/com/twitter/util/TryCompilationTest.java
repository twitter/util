package com.twitter.util;

import org.junit.Assert;
import org.junit.Test;

public class TryCompilationTest {
    @Test
    public void testThrowThrowable() {
        final Exception e = new Exception("exc");
        final Throw t = new Throw(e);
        Assert.assertEquals(t.throwable(), e);
    }

    @Test
    public void testReturnThrowable() {
        final Return<Integer> r = new Return<>(1);
        try {
            r.throwable();
            Assert.fail("should not be reached");
        } catch (IllegalStateException e) {
            // ok
        }
    }
}
