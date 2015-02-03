package com.twitter.collection

/**
 * DynamicRecord represents the first-class ''declaration'' of a heterogeneous
 * [[http://en.wikipedia.org/wiki/Record_%28computer_science%29 record]] type, with
 * [[com.twitter.collection.DynamicRecord.Field Field]]s that are determined at runtime. A Field
 * declares the static type of its associated values, so although the record is dynamic, field
 * access is type-safe.
 *
 * Given a DynamicRecord declaration `decl`, any number of
 * [[com.twitter.collection.DynamicRecord.Instance Instance]]s of that record can be obtained with
 * `decl.newInstance`. The type that Scala assigns to this value is what Scala calls a
 * "[[http://lampwww.epfl.ch/~amin/dot/fpdt.pdf path-dependent type]]," meaning that
 * `decl1.Instance` and `decl2.Instance` are distinct types. The same is true of fields:
 * `decl1.Field[A]` and `decl2.Field[A]` are distinct, and can only be used with the corresponding
 * Instance.
 *
 * The `put` method attaches a field with a value to a DynamicRecord.Instance. Scala's type system
 * doesn't provide a convenient mechanism to encode the prior presence or absence of a particular
 * field (this would be handled elegantly by [[https://www.cs.cmu.edu/~neelk/rows.pdf row
 * polymorphism]], as implemented in [[https://realworldocaml.org/v1/en/html/objects.html OCaml]],
 * whereas Scala's traits and structural types seem to fall short). So, if there is already a value
 * assigned for a given field in a given instance, `put` will throw a runtime exception.
 *
 * DynamicRecord.Instance also provides an `update` method, which does allow values to be mutated,
 * but only for [[com.twitter.collection.DynamicRecord.MutableField MutableField]]s. The distinction
 * between Field and MutableField is enforced at compile time, so you can't unintentionally `update`
 * an immutable field.
 *
 * Lastly, a field may declare a default value, which is returned from `get` and `apply` when there
 * is no value associated with that field in a record. This is analogous to default constructor
 * parameter values for Scala classes.
 */
final class DynamicRecord {

  /**
   * DynamicRecord.Instance is a first-class ''instance'' of a
   * [[com.twitter.collection.DynamicRecord DynamicRecord]] declaration.
   */
  final class Instance private[DynamicRecord] () {
    import collection.mutable

    private[this] val fields = mutable.Map.empty[Field[Any], Any]

    /**
     * @param field the field to access in this instance
     * @return the value associated with `field` if present in this instance, or `field`'s default
     *         value if defined, or `None`
     */
    def get[A](field: Field[A]): Option[A] =
      fields.get(field).asInstanceOf[Option[A]] orElse field.default

    /**
     * @param field the field to access in this instance
     * @return the value associated with `field` if present in this instance, or `field`'s default
     *         value if defined
     * @throws NoSuchElementException
     */
    def apply[A](field: Field[A]): A =
      get(field).get

    /**
     * @param field the field to put in this instance
     * @param value the value to associate with `field` in this instance
     * @return this instance, mutated to include the new field with its value
     * @throws IllegalStateException
     */
    def put[A](field: Field[A], value: A): Instance =
      fields.get(field) match {
        case Some(oldValue) =>
          throw new java.lang.IllegalStateException(s"attempt to put field with value $value, previously defined with value $oldValue")
        case None =>
          fields.update(field, value)
          this
      }

    /**
     * @param field the field to put or update in this instance
     * @param value the new value to associate with `field` in this instance
     * @return this instance, mutated to include or update the field with its new value
     */
    def update[A](field: MutableField[A], value: A): Instance = {
      fields.update(field, value)
      this
    }
  }

  /**
   * [[com.twitter.collection.DynamicRecord DynamicRecord]] uses Field as a handle — or a map key —
   * to access some corresponding value in an [[com.twitter.collection.DynamicRecord.Instance
   * Instance]]. A field may also declare a default value, which is returned from `get` and `apply`
   * when there is no value associated with this field in a record.
   *
   * The identity semantics of Field are intentionally inherited from [[java.lang.Object]].
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   */
  sealed class Field[+A] private[DynamicRecord] (val default: Option[A])

  /*
   * MutableField is a [[com.twitter.collection.DynamicRecord.Field Field]] which can be `update`d
   * in a [[com.twitter.collection.DynamicRecord.Instance DynamicRecord.Instance]].
   *
   * The identity semantics of MutableField are intentionally inherited from [[java.lang.Object]].
   *
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   */
  final class MutableField[+A] private[DynamicRecord] (default: Option[A]) extends Field(default)

  /**
   * @return a new [[com.twitter.collection.DynamicRecord.Instance DynamicRecord.Instance]]
   */
  def newInstance: Instance = new Instance

  /**
   * @return an immutable [[com.twitter.collection.DynamicRecord.Field Field]] with no default value
   */
  def newField[A]: Field[A] = new Field(None)

  /**
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   * @return an immutable [[com.twitter.collection.DynamicRecord.Field Field]] with the given
   *         `default` value
   */
  def newField[A](default: A): Field[A] = new Field(Some(default))

  /**
   * @return a [[com.twitter.collection.DynamicRecord.MutableField MutableField]] with no default
   *         value
   */
  def newMutableField[A]: MutableField[A] = new MutableField(None)

  /**
   * @param default the default value to use, when there is no value associated with this field in a
   *        given record
   * @return a [[com.twitter.collection.DynamicRecord.MutableField MutableField]] with the given
   *         `default` value
   */
  def newMutableField[A](default: A): MutableField[A] = new MutableField(Some(default))

}
