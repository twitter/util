package com.twitter.util.tunable;

import org.junit.Test;

import com.twitter.util.tunable.TunableMap.Key;
import com.twitter.util.tunable.TunableMap.Mutable;

public class TunableMapTest {

    @Test
    public void testTunableGet() {
        Mutable map = TunableMap.newMutable();
        Key<String> key = new Key<String>("key", String.class);
        Tunable<String> tunable = map.apply(key);
        assert(!tunable.apply().isDefined());
    }

    @Test
    public void testTunableMapPut() {
        Mutable map = TunableMap.newMutable();
        Key<Integer> key = map.put("id", Integer.class, 5);
        Tunable<Integer> tunable = map.apply(key);
        assert(tunable.apply().get() == 5);
    }

    @Test
    public void testTunableMapClear() {
        Mutable map = TunableMap.newMutable();
        Key<Integer> key = map.put("id", Integer.class, 5);
        Tunable<Integer> tunable = map.apply(key);
        map.clear(key);
        assert(!tunable.apply().isDefined());
    }
}