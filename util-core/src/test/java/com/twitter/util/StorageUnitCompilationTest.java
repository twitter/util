package com.twitter.util;

import org.junit.Test;

public class StorageUnitCompilationTest {

  @Test
  public void testFrom() {
    StorageUnit a = StorageUnit.fromBytes(1);
    StorageUnit b = StorageUnit.fromKilobytes(1);
    StorageUnit c = StorageUnit.fromMegabytes(1);
    StorageUnit d = StorageUnit.fromTerabytes(1);
    StorageUnit e = StorageUnit.fromPetabytes(1);
    StorageUnit f = StorageUnit.fromExabytes(1);
  }

  @Test
  public void testBinaryOperations() {
    StorageUnit a = StorageUnit.fromBytes(1);
    StorageUnit b = StorageUnit.fromBytes(1);

    a.plus(b).minus(a).times(1.0).times(1L).divide(1L);
  }
}
