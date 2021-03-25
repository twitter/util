package com.twitter.util.reflect

import com.twitter.util.reflect.testclasses._
import java.lang.annotation.Annotation
import org.junit.runner.RunWith
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner
import scala.collection.Map

@RunWith(classOf[JUnitRunner])
class AnnotationsTest extends AnyFunSuite with Matchers {

  test("Annotations#filterIfAnnotationPresent") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassOneTwo])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray

    val found = Annotations.filterIfAnnotationPresent[MarkerAnnotation](annotations)
    found.length should be(1)
    found.head.annotationType should equal(classOf[Annotation2])
  }

  test("Annotations#filterAnnotations") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassThreeFour])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val filterSet: Set[Class[_ <: Annotation]] = Set(classOf[Annotation4])
    val found = Annotations.filterAnnotations(filterSet, annotations)
    found.length should be(1)
    found.head.annotationType should equal(classOf[Annotation4])
  }

  test("Annotations#findAnnotation") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassThreeFour])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation(classOf[Annotation1], annotations) should be(None) // not found
    val found = Annotations.findAnnotation(classOf[Annotation3], annotations)
    found.isDefined should be(true)
    found.get.annotationType() should equal(classOf[Annotation3])
  }

  test("Annotations#findAnnotation by type") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassOneTwoThreeFour])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation2](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation3](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation4](annotations).isDefined should be(true)
  }

  test("Annotations#equals") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassOneTwoThreeFour])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val found = Annotations.findAnnotation[Annotation1](annotations)
    found.isDefined should be(true)

    val annotation = found.get
    Annotations.equals[Annotation1](annotation) should be(true)
  }

  test("Annotations#isAnnotationPresent") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassOneTwoThreeFour])
    annotationMap.isEmpty should be(false)
    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val annotation1 = Annotations.findAnnotation[Annotation1](annotations).get
    val annotation2 = Annotations.findAnnotation[Annotation2](annotations).get
    val annotation3 = Annotations.findAnnotation[Annotation3](annotations).get
    val annotation4 = Annotations.findAnnotation[Annotation4](annotations).get

    Annotations.isAnnotationPresent[MarkerAnnotation](annotation1) should be(false)
    Annotations.isAnnotationPresent[MarkerAnnotation](annotation2) should be(true)
    Annotations.isAnnotationPresent[MarkerAnnotation](annotation3) should be(true)
    Annotations.isAnnotationPresent[MarkerAnnotation](annotation4) should be(false)
  }

  test("Annotations#findAnnotations") {
    var found: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[WithThings])
    found.isEmpty should be(false)
    var annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(4)

    found = Annotations.findAnnotations(classOf[WithWidgets])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(4)

    found = Annotations.findAnnotations(classOf[CaseClassOneTwo])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(2)

    found = Annotations.findAnnotations(classOf[CaseClassThreeFour])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(2)

    found = Annotations.findAnnotations(classOf[CaseClassOneTwoThreeFour])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(4)

    found = Annotations.findAnnotations(classOf[CaseClassOneTwoWithFields])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(2)

    found = Annotations.findAnnotations(classOf[CaseClassOneTwoWithAnnotatedField])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(3)

    found = Annotations.findAnnotations(classOf[CaseClassThreeFourAncestorOneTwo])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(4)

    found = Annotations.findAnnotations(classOf[CaseClassAncestorOneTwo])
    found.isEmpty should be(false)
    annotations = found.flatMap { case (_, annotations) => annotations.toList }.toArray
    annotations.length should equal(3)
  }

  test("Annotations.findAnnotations error") {
    // parameter types are ignored
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(
        classOf[CaseClassOneTwoWithFields],
        Seq(classOf[Boolean], classOf[Double]))
    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
  }

  test("Annotations#getValueIfAnnotatedWith") {
    val annotationsMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[WithThings])
    annotationsMap.isEmpty should be(false)
    val annotations = annotationsMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val annotation1 = Annotations.findAnnotation[Annotation1](annotations).get
    val annotation2 = Annotations.findAnnotation[Annotation2](annotations).get

    // @Annotation1 is not annotated with @MarkerAnnotation
    Annotations.getValueIfAnnotatedWith[MarkerAnnotation](annotation1).isDefined should be(false)
    // @Annotation2 is annotated with @MarkerAnnotation but does not define a value() method
    Annotations.getValueIfAnnotatedWith[MarkerAnnotation](annotation2).isDefined should be(false)

    val things = Annotations.filterAnnotations(Set(classOf[Thing]), annotations)
    things.foreach { thing =>
      Annotations.getValueIfAnnotatedWith[MarkerAnnotation](thing).isDefined should be(true)
    }

    val fooThingAnnotation = Things.named("foo")
    val result = Annotations.getValueIfAnnotatedWith[MarkerAnnotation](fooThingAnnotation)
    result.isDefined should be(true)
    result.get should equal("foo")
  }

  test("Annotations#getValue") {
    val annotationsMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[CaseClassThreeFour])
    annotationsMap.isEmpty should be(false)
    val annotations = annotationsMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val annotation3 = Annotations.findAnnotation[Annotation3](annotations).get
    val annotation4 = Annotations.findAnnotation[Annotation4](annotations).get

    val annotation3Value = Annotations.getValue(annotation3)
    annotation3Value.isDefined should be(true)
    annotation3Value.get should equal("annotation3")

    // Annotation4 has no value
    Annotations.getValue(annotation4).isDefined should be(false)

    val clazzFiveAnnotationsMap = Annotations.findAnnotations(classOf[CaseClassFive])
    val clazzFiveAnnotations = clazzFiveAnnotationsMap.flatMap {
      case (_, annotations) => annotations.toList
    }.toArray
    val annotation5 =
      Annotations.findAnnotation[Annotation5](clazzFiveAnnotations).get
    // Annotation5 has a uniquely named method
    val annotation5Value = Annotations.getValue(annotation5, "discriminator")
    annotation5Value.isDefined should be(true)
    annotation5Value.get should equal("annotation5")

    // directly look up a value on an annotation
    val fooThingAnnotation = Things.named("foo")
    val fooThingAnnotationValue = Annotations.getValue(fooThingAnnotation)
    fooThingAnnotationValue.isDefined should be(true)
    fooThingAnnotationValue.get should equal("foo")
  }

  test("Annotations#secondaryConstructor") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(
        classOf[WithSecondaryConstructor],
        Seq(classOf[String], classOf[String]))

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("three").length should equal(1)
    annotationMap("three").head.annotationType() should equal(classOf[Annotation3])
    annotationMap("four").length should equal(1)
    annotationMap("four").head.annotationType() should equal(classOf[Annotation4])
    annotationMap.get("one") should be(None) // not found
    annotationMap.get("two") should be(None) // not found

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation2](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation3](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation4](annotations).isDefined should be(true)
  }

  test("Annotations#secondaryConstructor 1") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(
        classOf[WithSecondaryConstructor],
        Seq(classOf[Int], classOf[Int]))

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
    annotationMap.get("three") should be(None) // not found
    annotationMap.get("four") should be(None) // not found

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation2](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation3](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation4](annotations) should be(None) // not found
  }

  test("Annotations#secondaryConstructor 2") {
    val annotationMap: Map[String, Array[Annotation]] = Annotations.findAnnotations(
      classOf[StaticSecondaryConstructor],
      Seq(classOf[String], classOf[String])
    )
    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
  }

  test("Annotations#secondaryConstructor 3") {
    val annotationMap: Map[String, Array[Annotation]] = Annotations.findAnnotations(
      classOf[StaticSecondaryConstructor],
      Seq(classOf[Int], classOf[Int])
    )

    // secondary int "constructor" is not used/scanned as it is an Object apply method which
    // is a factory method and not a constructor.
    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
  }

  test("Annotations#secondaryConstructor 4") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[StaticSecondaryConstructor])

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation2](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation3](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation4](annotations) should be(None) // not found
  }

  test("Annotations#secondaryConstructor 5") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[StaticSecondaryConstructorWithMethodAnnotation])
    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
    annotationMap.get("widget1") should be(None)

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    val annotation1 = Annotations.findAnnotation[Annotation1](annotations).get
    val annotation2 = Annotations.findAnnotation[Annotation2](annotations).get

    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations) should be(Some(annotation1))
    Annotations.findAnnotation[Annotation2](annotations) should be(Some(annotation2))
    Annotations.findAnnotation[Annotation3](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation4](annotations) should be(None) // not found
    val widgetAnnotationOpt = Annotations.findAnnotation[Widget](annotations)
    widgetAnnotationOpt.isDefined should be(false)
  }

  test("Annotations#secondaryConstructor 6") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[StaticSecondaryConstructorWithMethodAnnotation])

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])
    annotationMap.get("widget1") should be(None)

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation2](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation3](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation4](annotations) should be(None) // not found
    val widgetAnnotationOpt = Annotations.findAnnotation[Widget](annotations)
    widgetAnnotationOpt.isDefined should be(false)
  }

  test("Annotations#no constructor should not fail") {
    // we expressly want this to no-op rather than fail outright
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(classOf[NoConstructor])

    annotationMap.isEmpty should be(true)
  }

  test("Annotations#generic types") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(
        classOf[GenericTestCaseClass[Int]],
        Seq(classOf[Object])
      ) // generic types resolve to Object

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(1)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
  }

  test("Annotations#generic types 1") {
    val annotationMap: Map[String, Array[Annotation]] =
      Annotations.findAnnotations(
        classOf[GenericTestCaseClassWithMultipleArgs[Int]],
        Seq(classOf[Object], classOf[Int])
      ) // generic types resolve to Object

    annotationMap.isEmpty should be(false)
    annotationMap.size should equal(2)
    annotationMap("one").length should equal(1)
    annotationMap("one").head.annotationType() should equal(classOf[Annotation1])
    annotationMap("two").length should equal(1)
    annotationMap("two").head.annotationType() should equal(classOf[Annotation2])

    val annotations = annotationMap.flatMap { case (_, annotations) => annotations.toList }.toArray
    Annotations.findAnnotation[MarkerAnnotation](annotations) should be(None) // not found
    Annotations.findAnnotation[Annotation1](annotations).isDefined should be(true)
    Annotations.findAnnotation[Annotation2](annotations).isDefined should be(true)
  }
}
