package com.twitter.collection

/**
 * RecordSchema represents the declaration of a heterogeneous
 * [[com.twitter.collection.RecordSchema.Record Record]] type, with
 * [[com.twitter.collection.RecordSchema.Field Fields]] that are determined at runtime. A Field
 * declares the static type of its associated values, so although the record itself is dynamic,
 * field access is type-safe.
 *
 * Given a RecordSchema declaration `schema`, any number of
 * [[com.twitter.collection.RecordSchema.Record Records]] of that schema can be obtained with
 * `schema.newRecord`. The type that Scala assigns to this value is what Scala calls a
 * "[[http://lampwww.epfl.ch/~amin/dot/fpdt.pdf path-dependent type]]," meaning that
 * `schema1.Record` and `schema2.Record` name distinct types. The same is true of fields:
 * `schema1.Field[A]` and `schema2.Field[A]` are distinct, and can only be used with the
 * corresponding Record.
 *
 * The `put` method attaches a field with a value to a record. Scala's type system doesn't provide a
 * convenient mechanism to encode the prior presence or absence of a particular field (this would be
 * handled elegantly by [[https://www.cs.cmu.edu/~neelk/rows.pdf row polymorphism]], as implemented
 * in [[https://realworldocaml.org/v1/en/html/objects.html OCaml]], whereas Scala's traits and
 * structural types seem to fall short). So, if there is already a value assigned for a given field
 * in a given instance, `put` will throw a runtime exception.
 *
 * Record also provides an `update` method, which does allow values to be mutated, but only for
 * [[com.twitter.collection.RecordSchema.MutableField MutableFields]]. The distinction between Field
 * and MutableField is enforced at compile time, so you can't unintentionally `update` an immutable
 * field.
 *
 * Lastly, a field may declare a default value, which is returned from `get` and `apply` when there
 * is no value associated with that field in a record.
 */
final class RecordSchema {
  import RecordSchema._

  /**
   * Record is an instance of a [[com.twitter.collection.RecordSchema RecordSchema]] declaration.
   *
   * '''Note that this implementation is not synchronized.''' If multiple threads access a record
   * concurrently, and at least one of the threads modifies the record, it ''must'' be synchronized
   * externally.
   */
  final class Record private[RecordSchema] {
    import java.util.{IdentityHashMap, NoSuchElementException}

    private[this] val fields = new IdentityHashMap[Field[_], AnyRef]

    /**
     * Returns the value associated with `field` if present in this record, or `field`'s default
     * value if defined, or `None`.
     *
     * @param field the field to access in this record
     * @return the value associated with `field` if present in this record, or `field`'s default
     *         value if defined, or `None`
     */
    def get[A](field: Field[A]): Option[A] = {
      val value = fields.get(field)
      if (value eq null) {
        field.default
      } else if (value eq NullSentinel) {
        Some(null.asInstanceOf[A])
      } else {
        Some(value.asInstanceOf[A])
      }
    }

    /**
     * Returns the value associated with `field` if present in this record, or `field`'s default
     * value if defined.
     *
     * @param field the field to access in this record
     * @return the value associated with `field` if present in this record, or `field`'s default
     *         value if defined
     * @throws NoSuchElementException
     */
    @throws(classOf[NoSuchElementException])
    def apply[A](field: Field[A]): A =
      get(field).get

    /**
     * Attaches a `field` with a given `value` to this record, failing if the field is already
     * attached.
     *
     * @param field the field to put in this record
     * @param value the value to associate with `field` in this record
     * @return this record, mutated to include the new field with its value
     * @throws IllegalStateException
     */
    @throws(classOf[IllegalStateException])
    def put[A](field: Field[A], value: A): Record = {
      val oldValue = fields.get(field)
      if (oldValue ne null) {
        throw new IllegalStateException(s"attempt to put field with value $value, previously defined with value $oldValue")
      }
      updateImpl(field, value)
    }

    /**
     * Updates a mutable `field` with a given `value` in this record, attaching it if it isn't
     * already attached.
     *
     * @param field the field to put or update in this record
     * @param value the new value to associate with `field` in this record
     * @return this record, mutated to include or update the field with its new value
     */
    def update[A](field: MutableField[A], value: A): Record = updateImpl(field, value)

    private[this] def updateImpl[A](field: Field[A], value: A): Record = {
      val ref = value.asInstanceOf[AnyRef]
      fields.put(field, if (ref eq null) NullSentinel else ref)
      this
    }
  }

  /**
   * Field is a handle — or a map key — used to access some corresponding value in a
   * [[com.twitter.collection.RecordSchema.Record Record]]. A field may also declare a default
   * value, which is returned from `get` and `apply` when there is no value associated it in a
   * record.
   *
   * The identity semantics of Field are intentionally inherited from [[java.lang.Object]].
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   */
  sealed class Field[A] private[RecordSchema] (val default: Option[A])

  /*
   * MutableField is a [[com.twitter.collection.RecordSchema.Field Field]] which can be `update`d
   * in a [[com.twitter.collection.RecordSchema.Record Record]].
   *
   * The identity semantics of MutableField are intentionally inherited from [[java.lang.Object]].
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   */
  final class MutableField[A] private[RecordSchema] (default: Option[A]) extends Field(default)

  /**
   * Creates a new [[com.twitter.collection.RecordSchema.Record Record]] from this Schema.
   *
   * @return a new [[com.twitter.collection.RecordSchema.Record Record]]
   */
  def newRecord: Record = new Record

  /**
   * Creates a new immutable [[com.twitter.collection.RecordSchema.Field Field]] with no default
   * value, to be used only with [[com.twitter.collection.RecordSchema.Record Records]] from this
   * schema.
   *
   * @return an immutable [[com.twitter.collection.RecordSchema.Field Field]] with no default value
   */
  def newField[A]: Field[A] = new Field(None)

  /**
   * Creates a new immutable [[com.twitter.collection.RecordSchema.Field Field]] with the given
   * `default` value, to be used only with [[com.twitter.collection.RecordSchema.Record Records]]
   * from this schema.
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   * @return an immutable [[com.twitter.collection.RecordSchema.Field Field]] with the given
   *         `default` value
   */
  def newField[A](default: A): Field[A] = new Field(Some(default))

  /**
   * Creates a new [[com.twitter.collection.RecordSchema.MutableField MutableField]] with no default
   * value, to be used only with [[com.twitter.collection.RecordSchema.Record Records]] from this
   * schema.
   *
   * @return a [[com.twitter.collection.RecordSchema.MutableField MutableField]] with no default
   *         value
   */
  def newMutableField[A]: MutableField[A] = new MutableField(None)

  /**
   * Creates a new [[com.twitter.collection.RecordSchema.MutableField MutableField]] with the given
   * `default` value, to be used only with [[com.twitter.collection.RecordSchema.Record Records]]
   * from this schema.
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   * @return a [[com.twitter.collection.RecordSchema.MutableField MutableField]] with the given
   *         `default` value
   */
  def newMutableField[A](default: A): MutableField[A] = new MutableField(Some(default))
}

object RecordSchema {
  private object NullSentinel {
    override def toString: String = "null"
  }
}
