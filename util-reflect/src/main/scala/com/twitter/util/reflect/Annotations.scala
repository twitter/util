package com.twitter.util.reflect

import com.twitter.util.{Return, Throw, Try}
import java.lang.annotation.Annotation
import java.lang.reflect.{Field, Parameter}
import scala.reflect.{ClassTag, classTag}

/** Utility methods for dealing with [[java.lang.annotation.Annotation]] */
object Annotations {

  /**
   * Filter a list of annotations discriminated on whether the
   * annotation is itself annotated with the passed annotation.
   *
   * @param annotations the list of [[Annotation]] instances to filter.
   * @tparam A the type of the annotation by which to filter.
   *
   * @return the filtered list of matching annotations.
   */
  def filterIfAnnotationPresent[A <: Annotation: ClassTag](
    annotations: Array[Annotation]
  ): Array[Annotation] = annotations.filter(isAnnotationPresent[A])

  /**
   * Filters a list of annotations by annotation type discriminated by the set of given annotations.
   * @param filterSet the Set of [[Annotation]] classes by which to filter.
   * @param annotations the list [[Annotation]] instances to filter.
   *
   * @return the filtered list of matching annotations.
   */
  def filterAnnotations(
    filterSet: Set[Class[_ <: Annotation]],
    annotations: Array[Annotation]
  ): Array[Annotation] =
    annotations.filter(a => filterSet.contains(a.annotationType))

  /**
   * Find an [[Annotation]] within a given list of annotations of the given target.
   * annotation type.
   * @param target the class of the [[Annotation]] to find.
   * @param annotations the list of [[Annotation]] instances to search.
   *
   * @return the matching [[Annotation]] instance if found, otherwise None.
   */
  // optimized
  def findAnnotation(
    target: Class[_ <: Annotation],
    annotations: Array[Annotation]
  ): Option[Annotation] = {
    var found: Option[Annotation] = None
    var index = 0
    while (index < annotations.length && found.isEmpty) {
      val annotation = annotations(index)
      if (annotation.annotationType() == target) found = Some(annotation)
      index += 1
    }
    found
  }

  /**
   * Find an [[Annotation]] within a given list of annotations annotated by the given type param.
   * @param annotations the list of [[Annotation]] instances to search.
   * @tparam A the type of the [[Annotation]] to find.
   *
   * @return the matching [[Annotation]] instance if found, otherwise None.
   */
  // optimized
  def findAnnotation[A <: Annotation: ClassTag](annotations: Array[Annotation]): Option[A] = {
    val size = annotations.length
    val annotationType = classTag[A].runtimeClass.asInstanceOf[Class[A]]
    var found: Option[A] = None
    var index = 0
    while (found.isEmpty && index < size) {
      val annotation = annotations(index)
      if (annotation.annotationType() == annotationType) found = Some(annotation.asInstanceOf[A])
      index += 1
    }
    found
  }

  /**
   * Attempts to map fields to array of annotations. This is intended to work around the Scala default
   * of not carrying annotation information on annotated constructor fields annotated without also
   * specifying a @meta annotation (e.g., `@field`). Thus, this method scans the constructor and
   * then all declared fields in order to build up the mapping of field name to `Array[java.lang.Annotation]`.
   * When the given class has a single constructor, that constructor will be used. Otherwise, the
   * given parameter types are used to locate a constructor.
   * Steps:
   *   - walk the constructor and collect annotations on all parameters.
   *   - for each declared field in the class, collect declared annotations. If a constructor parameter
   *     is overridden as a declared field, the annotations on the declared field take precedence.
   *   - for each super interface, walk each declared method, collect declared method annotations IF
   *     the declared method is the same name of a declared field from step two. That is find only
   *     super interface methods which are implemented by declared fields in the given class in order
   *     to locate inherited annotations for the declared field.
   *
   * @param clazz the `Class` to inspect. This should represent a Scala case class.
   * @param parameterTypes an optional list of parameter class types in order to locate the appropriate
   *                       case class constructor when the class has multiple constructors. If a class
   *                       has multiple constructors and parameter types are not specified, an
   *                       [[IllegalArgumentException]] is thrown. Likewise, if a suitable constructor
   *                       cannot be located by the given parameter types an [[IllegalArgumentException]]
   *                       is thrown.
   *
   * @see [[https://www.scala-lang.org/api/current/scala/annotation/meta/index.html]]
   * @return a map of field name to associated annotations. An entry in the map only occurs if the
   *         associated annotations are non-empty. That is fields without any found annotations are
   *         not returned.
   */
  // optimized
  def findAnnotations(
    clazz: Class[_],
    parameterTypes: Seq[Class[_]] = Seq.empty
  ): scala.collection.Map[String, Array[Annotation]] = {
    val clazzAnnotations = scala.collection.mutable.HashMap[String, Array[Annotation]]()
    val constructorCount = clazz.getConstructors.length
    val constructor = if (constructorCount > 1) {
      if (parameterTypes.isEmpty)
        throw new IllegalArgumentException(
          "Multiple constructors detected and no parameter types specified.")
      Try(clazz.getConstructor(parameterTypes: _*)) match {
        case Return(con) => con
        case Throw(t: NoSuchMethodException) =>
          // translate from the reflection exception to a runtime exception
          throw new IllegalArgumentException(t)
        case Throw(t) => throw t
      }
    } else clazz.getConstructors.head

    val fields: Array[Parameter] = constructor.getParameters
    val clazzConstructorAnnotations: Array[Array[Annotation]] = constructor.getParameterAnnotations

    var i = 0
    while (i < fields.length) {
      val field = fields(i)
      val fieldAnnotations = clazzConstructorAnnotations(i)
      if (fieldAnnotations.nonEmpty) clazzAnnotations.put(field.getName, fieldAnnotations)
      i += 1
    }

    val declaredFields: Array[Field] = clazz.getDeclaredFields
    var j = 0
    while (j < declaredFields.length) {
      val field = declaredFields(j)
      if (field.getDeclaredAnnotations.nonEmpty) {
        if (!clazzAnnotations.contains(field.getName)) {
          clazzAnnotations.put(field.getName, field.getDeclaredAnnotations)
        } else {
          val existing = clazzAnnotations(field.getName)
          // prefer field annotations over constructor annotation
          mergeAnnotationArrays(field.getDeclaredAnnotations, existing)
        }
      }

      j += 1
    }

    // find inherited annotations for declared fields
    findDeclaredAnnotations(
      clazz,
      declaredFields.map(_.getName).toSet,
      clazzAnnotations
    )
  }

  /**
   * Determines if the given [[Annotation]] has an annotation type of the given type param.
   * @param annotation the [[Annotation]] to match.
   * @tparam A the type to match against.
   *
   * @return true if the given [[Annotation]] is of type [[A]], false otherwise.
   */
  def equals[A <: Annotation: ClassTag](annotation: Annotation): Boolean =
    annotation.annotationType() == classTag[A].runtimeClass.asInstanceOf[Class[A]]

  /**
   * Determines if the given [[Annotation]] is annotated by an [[Annotation]] of the given
   * type param.
   * @param annotation the [[Annotation]] to match.
   * @tparam A the type of the [[Annotation]] to determine if is annotated on the [[Annotation]].
   *
   * @return true if the given [[Annotation]] is annotated with an [[Annotation]] of type [[A]],
   *         false otherwise.
   */
  def isAnnotationPresent[A <: Annotation: ClassTag](annotation: Annotation): Boolean =
    annotation.annotationType.isAnnotationPresent(classTag[A].runtimeClass.asInstanceOf[Class[A]])

  /**
   * Determines if the given [[A]] is annotated by an [[Annotation]] of the given
   * type param [[ToFindAnnotation]].
   *
   * @tparam ToFindAnnotation the [[Annotation]] to match.
   * @tparam A the type of the [[Annotation]] to determine if is annotated on the [[A]].
   *
   * @return true if the given [[Annotation]] is annotated with an [[Annotation]] of type [[ToFindAnnotation]],
   *         false otherwise.
   */
  def isAnnotationPresent[
    ToFindAnnotation <: Annotation: ClassTag,
    A <: Annotation: ClassTag
  ]: Boolean = {
    val annotationToFindClazz: Class[Annotation] =
      classTag[ToFindAnnotation].runtimeClass
        .asInstanceOf[Class[Annotation]]

    classTag[A].runtimeClass
      .asInstanceOf[Class[A]].isAnnotationPresent(annotationToFindClazz)
  }

  /**
   * Attempts to return the result of invoking `method()` of a given [[Annotation]] which should be
   * annotated by an [[Annotation]] of type [[A]].
   *
   * If the given [[Annotation]] is not annotated by an [[Annotation]] of type [[A]] or if the
   * `Annotation#method` cannot be invoked then [[None]] is returned.
   *
   * @param annotation the [[Annotation]] to process.
   * @param method the method of the [[Annotation]] to invoke to obtain a value.
   * @tparam A the [[Annotation]] type to match against before trying to invoke `annotation#method`.
   *
   * @return the result of invoking `annotation#method()` or None.
   */
  def getValueIfAnnotatedWith[A <: Annotation: ClassTag](
    annotation: Annotation,
    method: String = "value"
  ): Option[String] = {
    if (isAnnotationPresent[A](annotation)) {
      getValue(annotation, method)
    } else None
  }

  /**
   * Attempts to return the result of invoking `method()` of a given [[Annotation]]. If the
   * `Annotation#method` cannot be invoked then [[None]] is returned.
   *
   * @param annotation the [[Annotation]] to process.
   * @param method the method of the [[Annotation]] to invoke to obtain a value.
   *
   * @return the result of invoking `annotation#method()` or None.
   */
  def getValue(
    annotation: Annotation,
    method: String = "value"
  ): Option[String] = {
    for {
      method <- annotation.getClass.getDeclaredMethods.find(_.getName == method)
      value <- Try(method.invoke(annotation).asInstanceOf[String]).toOption
    } yield value
  }

  // optimized
  private[twitter] def findDeclaredAnnotations(
    clazz: Class[_],
    declaredFields: Set[String], // `.contains` on a Set should be faster than on an Array
    acc: scala.collection.mutable.Map[String, Array[Annotation]]
  ): scala.collection.Map[String, Array[Annotation]] = {
    val methods = clazz.getDeclaredMethods
    var i = 0
    while (i < methods.length) {
      val method = methods(i)
      val methodAnnotations = method.getDeclaredAnnotations
      if (methodAnnotations.nonEmpty && declaredFields.contains(method.getName)) {
        acc.get(method.getName) match {
          case Some(existing) =>
            acc.put(method.getName, mergeAnnotationArrays(existing, methodAnnotations))
          case _ =>
            acc.put(method.getName, methodAnnotations)
        }
      }
      i += 1
    }

    val interfaces = clazz.getInterfaces
    var j = 0
    while (j < interfaces.length) {
      val interface = interfaces(j)
      findDeclaredAnnotations(interface, declaredFields, acc)
      j += 1
    }

    acc
  }

  /** Prefer values in A over B */
  private[this] def mergeAnnotationArrays(
    a: Array[Annotation],
    b: Array[Annotation]
  ): Array[Annotation] =
    a ++ b.filterNot(bAnnotation => a.exists(_.annotationType() == bAnnotation.annotationType()))
}
