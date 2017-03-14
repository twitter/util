package com.twitter.util;

import org.junit.Before;
import org.junit.Test;

import com.twitter.util.ObjectSizeCalculator.MemoryLayoutSpecification;

import static junit.framework.Assert.assertEquals;

// copied from https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/objectsize/ObjectSizeCalculatorTest.java

public class ObjectSizeCalculatorTest {

  private int A;
  private int O;
  private int R;
  private int S;
  private ObjectSizeCalculator objectSizeCalculator;

  @Before
  public void setUp() {
    MemoryLayoutSpecification memoryLayoutSpecification =
        new MemoryLayoutSpecification() {
          @Override public int getArrayHeaderSize() {
            return 16;
          }
          @Override public int getObjectHeaderSize() {
            return 12;
          }
          @Override public int getObjectPadding() {
            return 8;
          }
          @Override public int getReferenceSize() {
            return 4;
          }
          @Override public int getSuperclassFieldPadding() {
            return 4;
          }
        };

    A = memoryLayoutSpecification.getArrayHeaderSize();
    O = memoryLayoutSpecification.getObjectHeaderSize();
    R = memoryLayoutSpecification.getReferenceSize();
    S = memoryLayoutSpecification.getSuperclassFieldPadding();

    objectSizeCalculator = new ObjectSizeCalculator(memoryLayoutSpecification);
  }

  @Test
  public void testRounding() {
    assertEquals(0, roundTo(0, 8));
    assertEquals(8, roundTo(1, 8));
    assertEquals(8, roundTo(7, 8));
    assertEquals(8, roundTo(8, 8));
    assertEquals(16, roundTo(9, 8));
    assertEquals(16, roundTo(15, 8));
    assertEquals(16, roundTo(16, 8));
    assertEquals(24, roundTo(17, 8));
  }

  @Test
  public void testObjectSize() {
    assertSizeIs(O, new Object());
  }

  static class ObjectWithFields {
    int length;
    int offset;
    int hashcode;
    char[] data = {};
  }

  @Test
  public void testObjectWithFields() {
    assertSizeIs(O + 3 * 4 + R + A, new ObjectWithFields());
  }

  public static class Class1 {
    private boolean b1;
  }

  @Test
  public void testOneBooleanSize() {
    assertSizeIs(O + 1, new Class1());
  }

  public static class Class2 extends Class1 {
    private int i1;
  }

  @Test
  public void testSimpleSubclassSize() {
    assertSizeIs(O + roundTo(1, S) + 4, new Class2());
  }

  @Test
  public void testZeroLengthArray() {
    assertSizeIs(A, new byte[0]);
    assertSizeIs(A, new int[0]);
    assertSizeIs(A, new long[0]);
    assertSizeIs(A, new Object[0]);
  }

  @Test
  public void testByteArrays() {
    assertSizeIs(A + 1, new byte[1]);
    assertSizeIs(A + 8, new byte[8]);
    assertSizeIs(A + 9, new byte[9]);
  }

  @Test
  public void testCharArrays() {
    assertSizeIs(A + 2 * 1, new char[1]);
    assertSizeIs(A + 2 * 4, new char[4]);
    assertSizeIs(A + 2 * 5, new char[5]);
  }

  @Test
  public void testIntArrays() {
    assertSizeIs(A + 4 * 1, new int[1]);
    assertSizeIs(A + 4 * 2, new int[2]);
    assertSizeIs(A + 4 * 3, new int[3]);
  }

  @Test
  public void testLongArrays() {
    assertSizeIs(A + 8 * 1, new long[1]);
    assertSizeIs(A + 8 * 2, new long[2]);
    assertSizeIs(A + 8 * 3, new long[3]);
  }

  @Test
  public void testObjectArrays() {
    assertSizeIs(A + R * 1, new Object[1]);
    assertSizeIs(A + R * 2, new Object[2]);
    assertSizeIs(A + R * 3, new Object[3]);
    assertSizeIs(A + R * 1, new String[1]);
    assertSizeIs(A + R * 2, new String[2]);
    assertSizeIs(A + R * 3, new String[3]);
  }

  public static class Circular {
    Circular c;
  }

  @Test
  public void testCircular() {
    Circular c1 = new Circular();
    long size = objectSizeCalculator.calculateObjectSize(c1);
    c1.c = c1;
    assertEquals(size, objectSizeCalculator.calculateObjectSize(c1));
  }

  static class ComplexObject<T> {
    static class Node<T> {
      final T value;
      Node<T> previous;
      Node<T> next;

      Node(T value) {
        this.value = value;
      }
    }

    private Node<T> first;
    private Node<T> last;

    void add(T item) {
      Node<T> node = new Node<T>(item);
      if (first == null) {
        first = node;
      } else {
        last.next = node;
        node.previous = last;
      }
      last = node;
    }
  }

  @Test
  public void testComplexObject() {
    ComplexObject<Object> l = new ComplexObject<Object>();
    l.add(new Object());
    l.add(new Object());
    l.add(new Object());

    long expectedSize = 0;
    expectedSize += roundTo(O + 2 * R, 8); // The complex object itself plus first and last refs.
    expectedSize += roundTo(O + 3 * R, 8) * 3; // 3 Nodes - each with 3 object references.
    expectedSize += roundTo(O, 8) * 3; // 3 vanilla objects contained in the node values.

    assertSizeIs(expectedSize, l);
  }

  private void assertSizeIs(long size, Object o) {
    assertEquals(roundTo(size, 8), objectSizeCalculator.calculateObjectSize(o));
  }

  private static long roundTo(long x, int multiple) {
    return ObjectSizeCalculator.roundTo(x, multiple);
  }
}
