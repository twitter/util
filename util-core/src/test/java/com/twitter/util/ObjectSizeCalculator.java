package com.twitter.util;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import scala.Predef;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

// copied from https://github.com/twitter/commons/blob/master/src/java/com/twitter/common/objectsize/ObjectSizeCalculator.java
/**
 * Contains utility methods for calculating the memory usage of objects. It
 * only works on the HotSpot JVM, and infers the actual memory layout (32 bit
 * vs. 64 bit word size, compressed object pointers vs. uncompressed) from
 * best available indicators. It can reliably detect a 32 bit vs. 64 bit JVM.
 * It can only make an educated guess at whether compressed OOPs are used,
 * though; specifically, it knows what the JVM's default choice of OOP
 * compression would be based on HotSpot version and maximum heap sizes, but if
 * the choice is explicitly overridden with the <tt>-XX:{+|-}UseCompressedOops</tt> command line
 * switch, it can not detect
 * this fact and will report incorrect sizes, as it will presume the default JVM
 * behavior.
 */
public class ObjectSizeCalculator {

  /**
   * Describes constant memory overheads for various constructs in a JVM implementation.
   */
  public interface MemoryLayoutSpecification {

    /**
     * Returns the fixed overhead of an array of any type or length in this JVM.
     *
     * @return the fixed overhead of an array.
     */
    int getArrayHeaderSize();

    /**
     * Returns the fixed overhead of for any {@link Object} subclass in this JVM.
     *
     * @return the fixed overhead of any object.
     */
    int getObjectHeaderSize();

    /**
     * Returns the quantum field size for a field owned by an object in this JVM.
     *
     * @return the quantum field size for an object.
     */
    int getObjectPadding();

    /**
     * Returns the fixed size of an object reference in this JVM.
     *
     * @return the size of all object references.
     */
    int getReferenceSize();

    /**
     * Returns the quantum field size for a field owned by one of an object's ancestor superclasses
     * in this JVM.
     *
     * @return the quantum field size for a superclass field.
     */
    int getSuperclassFieldPadding();
  }

  private static class CurrentLayout {
    private static final MemoryLayoutSpecification SPEC =
        getEffectiveMemoryLayoutSpecification();
  }

  /**
   * Given an object, returns the total allocated size, in bytes, of the object
   * and all other objects reachable from it.  Attempts to to detect the current JVM memory layout,
   * but may fail with {@link UnsupportedOperationException};
   *
   * @param obj the object; can be null. Passing in a {@link java.lang.Class} object doesn't do
   *          anything special, it measures the size of all objects
   *          reachable through it (which will include its class loader, and by
   *          extension, all other Class objects loaded by
   *          the same loader, and all the parent class loaders). It doesn't provide the
   *          size of the static fields in the JVM class that the Class object
   *          represents.
   * @return the total allocated size of the object and all other objects it
   *         retains.
   * @throws UnsupportedOperationException if the current vm memory layout cannot be detected.
   */
  public static long getObjectSize(Object obj) throws UnsupportedOperationException {
    return obj == null ? 0 : new ObjectSizeCalculator(CurrentLayout.SPEC).calculateObjectSize(obj);
  }

  // Fixed object header size for arrays.
  private final int arrayHeaderSize;
  // Fixed object header size for non-array objects.
  private final int objectHeaderSize;
  // Padding for the object size - if the object size is not an exact multiple
  // of this, it is padded to the next multiple.
  private final int objectPadding;
  // Size of reference (pointer) fields.
  private final int referenceSize;
  // Padding for the fields of superclass before fields of subclasses are
  // added.
  private final int superclassFieldPadding;

  // Have to use buildAsync with a synchronous view due to a CHM bug in Java 8.
  // See https://github.com/ben-manes/caffeine/issues/89
  private final LoadingCache<Class<?>, ClassSizeInfo> classSizeInfos =
    Caffeine.newBuilder()
        .buildAsync(ClassSizeInfo::new)
        .synchronous();

  private final Set<Object> alreadyVisited = Collections.newSetFromMap(new IdentityHashMap<>());
  private final Deque<Object> pending = new ArrayDeque<Object>(16 * 1024);
  private long size;

  /**
   * Creates an object size calculator that can calculate object sizes for a given
   * {@code memoryLayoutSpecification}.
   *
   * @param memoryLayoutSpecification a description of the JVM memory layout.
   */
  public ObjectSizeCalculator(MemoryLayoutSpecification memoryLayoutSpecification) {
    Predef.require(memoryLayoutSpecification != null);
    arrayHeaderSize = memoryLayoutSpecification.getArrayHeaderSize();
    objectHeaderSize = memoryLayoutSpecification.getObjectHeaderSize();
    objectPadding = memoryLayoutSpecification.getObjectPadding();
    referenceSize = memoryLayoutSpecification.getReferenceSize();
    superclassFieldPadding = memoryLayoutSpecification.getSuperclassFieldPadding();
  }

  /**
   * Given an object, returns the total allocated size, in bytes, of the object
   * and all other objects reachable from it.
   *
   * @param obj the object; can be null. Passing in a {@link java.lang.Class} object doesn't do
   *          anything special, it measures the size of all objects
   *          reachable through it (which will include its class loader, and by
   *          extension, all other Class objects loaded by
   *          the same loader, and all the parent class loaders). It doesn't provide the
   *          size of the static fields in the JVM class that the Class object
   *          represents.
   * @return the total allocated size of the object and all other objects it
   *         retains.
   */
  public synchronized long calculateObjectSize(Object obj) {
    // Breadth-first traversal instead of naive depth-first with recursive
    // implementation, so we don't blow the stack traversing long linked lists.
    try {
      for (;;) {
        visit(obj);
        if (pending.isEmpty()) {
          return size;
        }
        obj = pending.removeFirst();
      }
    } finally {
      alreadyVisited.clear();
      pending.clear();
      size = 0;
    }
  }

  private void visit(Object obj) {
    if (alreadyVisited.contains(obj)) {
      return;
    }
    final Class<?> clazz = obj.getClass();
    if (clazz == ArrayElementsVisitor.class) {
      ((ArrayElementsVisitor) obj).visit(this);
    } else {
      alreadyVisited.add(obj);
      if (clazz.isArray()) {
        visitArray(obj);
      } else {
        classSizeInfos.get(clazz).visit(obj, this);
      }
    }
  }

  private void visitArray(Object array) {
    final Class<?> componentType = array.getClass().getComponentType();
    final int length = Array.getLength(array);
    if (componentType.isPrimitive()) {
      increaseByArraySize(length, getPrimitiveFieldSize(componentType));
    } else {
      increaseByArraySize(length, referenceSize);
      // If we didn't use an ArrayElementsVisitor, we would be enqueueing every
      // element of the array here instead. For large arrays, it would
      // tremendously enlarge the queue. In essence, we're compressing it into
      // a small command object instead. This is different than immediately
      // visiting the elements, as their visiting is scheduled for the end of
      // the current queue.
      switch (length) {
        case 0: {
          break;
        }
        case 1: {
          enqueue(Array.get(array, 0));
          break;
        }
        default: {
          enqueue(new ArrayElementsVisitor((Object[]) array));
        }
      }
    }
  }

  private void increaseByArraySize(int length, long elementSize) {
    increaseSize(roundTo(arrayHeaderSize + length * elementSize, objectPadding));
  }

  private static class ArrayElementsVisitor {
    private final Object[] array;

    ArrayElementsVisitor(Object[] array) {
      this.array = array;
    }

    public void visit(ObjectSizeCalculator calc) {
      for (Object elem : array) {
        if (elem != null) {
          calc.visit(elem);
        }
      }
    }
  }

  void enqueue(Object obj) {
    if (obj != null) {
      pending.addLast(obj);
    }
  }

  void increaseSize(long objectSize) {
    size += objectSize;
  }

  /** Visible for testing */
  static long roundTo(long x, int multiple) {
    return ((x + multiple - 1) / multiple) * multiple;
  }

  private class ClassSizeInfo {
    // Padded fields + header size
    private final long objectSize;
    // Only the fields size - used to calculate the subclasses' memory
    // footprint.
    private final long fieldsSize;
    private final Field[] referenceFields;

    public ClassSizeInfo(Class<?> clazz) {
      long fieldsSize = 0;
      final List<Field> referenceFields = new LinkedList<Field>();
      for (Field f : clazz.getDeclaredFields()) {
        if (Modifier.isStatic(f.getModifiers())) {
          continue;
        }
        final Class<?> type = f.getType();
        if (type.isPrimitive()) {
          fieldsSize += getPrimitiveFieldSize(type);
        } else {
          f.setAccessible(true);
          referenceFields.add(f);
          fieldsSize += referenceSize;
        }
      }
      final Class<?> superClass = clazz.getSuperclass();
      if (superClass != null) {
        final ClassSizeInfo superClassInfo = classSizeInfos.get(superClass);
        fieldsSize += roundTo(superClassInfo.fieldsSize, superclassFieldPadding);
        referenceFields.addAll(Arrays.asList(superClassInfo.referenceFields));
      }
      this.fieldsSize = fieldsSize;
      this.objectSize = roundTo(objectHeaderSize + fieldsSize, objectPadding);
      this.referenceFields = referenceFields.toArray(
          new Field[referenceFields.size()]);
    }

    void visit(Object obj, ObjectSizeCalculator calc) {
      calc.increaseSize(objectSize);
      enqueueReferencedObjects(obj, calc);
    }

    public void enqueueReferencedObjects(Object obj, ObjectSizeCalculator calc) {
      for (Field f : referenceFields) {
        try {
          calc.enqueue(f.get(obj));
        } catch (IllegalAccessException e) {
          throw new AssertionError(
              "Unexpected denial of access to " + f, e);
        }
      }
    }
  }

  private static long getPrimitiveFieldSize(Class<?> type) {
    if (type == boolean.class || type == byte.class) {
      return 1;
    }
    if (type == char.class || type == short.class) {
      return 2;
    }
    if (type == int.class || type == float.class) {
      return 4;
    }
    if (type == long.class || type == double.class) {
      return 8;
    }
    throw new AssertionError("Encountered unexpected primitive type " +
        type.getName());
  }

  /** Visible for testing */
  static MemoryLayoutSpecification getEffectiveMemoryLayoutSpecification() {
    final String vmName = System.getProperty("java.vm.name");
    if (vmName == null || !(vmName.startsWith("Java HotSpot(TM) ")
        || vmName.startsWith("OpenJDK") || vmName.startsWith("TwitterJDK"))) {
      throw new UnsupportedOperationException(
          "ObjectSizeCalculator only supported on HotSpot VM");
    }

    final String dataModel = System.getProperty("sun.arch.data.model");
    if ("32".equals(dataModel)) {
      // Running with 32-bit data model
      return new MemoryLayoutSpecification() {
        @Override public int getArrayHeaderSize() {
          return 12;
        }
        @Override public int getObjectHeaderSize() {
          return 8;
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
    } else if (!"64".equals(dataModel)) {
      throw new UnsupportedOperationException("Unrecognized value '" +
          dataModel + "' of sun.arch.data.model system property");
    }

    final HotSpotDiagnosticMXBean diagBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
    if (diagBean == null) {
      throw new UnsupportedOperationException("ObjectSizeCalculator only supported on HotSpot VM");
    }
    final VMOption useCoopsOption = diagBean.getVMOption("UseCompressedOops");
    final boolean useCoops = Boolean.parseBoolean(useCoopsOption.getValue());

    if (useCoops) {
      // Returning the 64-bit compressed OOPs object model
      return new MemoryLayoutSpecification() {
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
    } else {
      // Returning the 64-bit non-compressed OOPs object model
      return new MemoryLayoutSpecification() {
        @Override public int getArrayHeaderSize() {
          return 24;
        }
        @Override public int getObjectHeaderSize() {
          return 16;
        }
        @Override public int getObjectPadding() {
          return 8;
        }
        @Override public int getReferenceSize() {
          return 8;
        }
        @Override public int getSuperclassFieldPadding() {
          return 8;
        }
      };
    }
  }
}
