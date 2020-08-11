package com.twitter.mock

import com.twitter.conversions.DurationOps._
import com.twitter.util.{Return, Throw}
import com.twitter.util.{Await, Awaitable, Future, Monitor}
import java.io.{File, FileOutputStream, ObjectOutputStream}
import java.util.concurrent.atomic.AtomicInteger
import org.junit.runner.RunWith
import org.mockito.MockitoSugar
import org.mockito.captor.ArgCaptor
import org.mockito.exceptions.verification.WantedButNotInvoked
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.{CallsRealMethods, DefaultAnswer, ScalaFirstStubbing}
import org.scalatest.concurrent.ScalaFutures.{FutureConcept, whenReady}
import org.scalatestplus.junit.JUnitRunner
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import scala.annotation.varargs
import scala.language.implicitConversions
import scala.reflect.io.AbstractFile

object MockitoTraitTest {

  /* Test classes pulled from MockitoScala:
  https://github.com/mockito/mockito-scala/blob/release/1.x/scalatest/src/test/scala/user/org/mockito/TestModel.scala*/

  class X {
    def bar = "not mocked"
    def iHaveByNameArgs(normal: String, byName: => String, byName2: => String): String =
      "not mocked"
    def iHaveSomeDefaultArguments(noDefault: String, default: String = "default value"): String =
      "not mocked"
    def iStartWithByNameArgs(byName: => String, normal: String): String = "not mocked"
    def iHavePrimitiveByNameArgs(byName: => Int, normal: String): String = "not mocked"
    def iHaveByNameAndVarArgs(
      byName: => String,
      normal: String,
      byName2: => String,
      normal2: String,
      vararg: String*
    )(
      byName3: => String,
      normal3: String,
      vararg3: String*
    ): String = "not mocked"

    def iHaveFunction0Args(normal: String, f0: () => String): String = "not mocked"
    def iHaveByNameAndFunction0Args(normal: String, f0: () => String, byName: => String): String =
      "not mocked"
    def returnBar: Bar = new Bar
    def doSomethingWithThisIntAndString(v: Int, v2: String): ValueCaseClassInt = ValueCaseClassInt(
      v)
    def returnsValueCaseClass: ValueCaseClassInt = ValueCaseClassInt(-1)
    def returnsValueCaseClass(i: Int): ValueCaseClassInt = ValueCaseClassInt(i)
    def baz(i: Int, b: Baz2): String = "not mocked"
  }

  trait Y {
    def varargMethod(s: String, arg: Int*): Int = -1
    def varargMethod(arg: Int*): Int = -1
    @varargs def javaVarargMethod(arg: Int*): Int = -1
    def byNameMethod(arg: => Int): Int = -1
    def traitMethod(arg: Int): ValueCaseClassInt = ValueCaseClassInt(arg)
    def traitMethodWithDefaultArgs(defaultArg: Int = 30, anotherDefault: String = "hola"): Int = -1
  }

  class XWithY extends X with Y

  class Complex extends X

  class Foo {
    def bar(a: String) = "bar"
  }

  class Foo2 {
    def valueClass(n: Int, v: ValueClass): String = ???
    def valueCaseClass(n: Int, v: ValueCaseClassInt): String = ???
  }

  class Bar {
    def iAlsoHaveSomeDefaultArguments(
      noDefault: String,
      default: String = "default value"
    ): String = "not mocked"
    def iHaveDefaultArgs(v: String = "default"): String = "not mocked"
  }

  trait Baz {
    def varargMethod(s: String, arg: Int*): Int = -1
    def varargMethod(arg: Int*): Int = -1
    @varargs def javaVarargMethod(arg: Int*): Int = -1
    def byNameMethod(arg: => Int): Int = -1
    def traitMethod(arg: Int): ValueCaseClassInt = ValueCaseClassInt(arg)
    def traitMethodWithDefaultArgs(defaultArg: Int = 30, anotherDefault: String = "hola"): Int = -1
  }

  class ValueClass(val v: String) extends AnyVal {
    override def toString = s"ValueClass($v)"
  }
  case class ValueCaseClassInt(v: Int) extends AnyVal
  case class ValueCaseClassString(v: String) extends AnyVal
  case class GenericValue[T](v: Int) extends AnyVal

  class HigherKinded[F[_]] {
    def method: F[Either[String, String]] = null.asInstanceOf[F[Either[String, String]]]
    def method2: F[Either[String, String]] = null.asInstanceOf[F[Either[String, String]]]
  }

  class ConcreteHigherKinded extends HigherKinded[Option]

  class Implicit[T]

  class Org {
    def bar = "not mocked"
    def baz = "not mocked"

    def defaultParams(a: String, defaultParam1: Boolean = false, defaultParam2: Int = 1): String =
      "not mocked"
    def curriedDefaultParams(
      a: String,
      defaultParam1: Boolean = false
    )(
      defaultParam2: Int = a.length
    ): String = "not mocked"

    def doSomethingWithThisInt(v: Int): Int = -1
    def doSomethingWithThisIntAndString(v: Int, v2: String): String = "not mocked"
    def doSomethingWithThisIntAndStringAndBoolean(v: Int, v2: String, v3: Boolean): String =
      "not mocked"
    def returnBar: Bar = new Bar
    def highOrderFunction(f: Int => String): String = "not mocked"
    def iReturnAFunction(v: Int): Int => String = i => (i * v).toString
    def iBlowUp(v: Int, v2: String): String = throw new IllegalArgumentException("I was called!")
    def iHaveTypeParamsAndImplicits[A, B](a: A, b: B)(implicit v3: Implicit[A]): String =
      "not mocked"
    def valueClass(n: Int, v: ValueClass): String = "not mocked"
    def valueCaseClass(n: Int, v: ValueCaseClassInt): String = "not mocked"
    def returnsValueCaseClassInt: ValueCaseClassInt = ValueCaseClassInt(-1)
    def returnsValueCaseClassString: ValueCaseClassString = ValueCaseClassString("not mocked")
    def takesManyValueClasses(
      v: ValueClass,
      v1: ValueCaseClassInt,
      v2: ValueCaseClassString
    ): String = "not mocked"
    def baz(i: Int, b: Baz): String = "not mocked"
    def fooWithVarArg(bells: String*): Unit = ()
    def fooWithActualArray(bells: Array[String]): Unit = ()
    def fooWithSecondParameterList(bell: String)(awaitable: Awaitable[Int]): Unit = ()
    def fooWithVarArgAndSecondParameterList(bells: String*)(awaitable: Awaitable[Int]): Unit =
      ()
    def fooWithActualArrayAndSecondParameterList(
      bells: Array[String]
    )(
      awaitable: Awaitable[Int]
    ): Unit = ()
    def valueClassWithVarArg(monitors: Monitor*): Unit = ()
    def valueClassWithSecondParameterList(monitor: Monitor)(awaitable: Awaitable[Int]): Unit = ()
    def valueClassWithVarArgAndSecondParameterList(
      monitors: Monitor*
    )(
      awaitable: Awaitable[Int]
    ): Unit = ()

    def option: Option[String] = None
    def option(a: String, b: Int): Option[String] = None
    def printGenericValue(value: GenericValue[String]): String = "not mocked"
    def unit(): Unit = ???
  }

  case class Baz2(param1: Int, param2: String)

  trait ParameterizedTrait[+E] { def m(): E }
  class ParameterizedTraitInt extends ParameterizedTrait[Int] { def m() = -1 }
}

@RunWith(classOf[JUnitRunner])
class MockitoTraitTest extends AnyFunSuite with Matchers /*with WhenReadyMixin*/ with Mockito {
  import MockitoTraitTest._

  test("mocks are called with the right arguments") {
    val foo = mock[Foo]

    foo.bar(any[String]) returns "mocked"
    foo.bar("alice") should equal("mocked")
    foo.bar("alice") was called
  }

  test("create a mock with name") {
    val aMock = mock[Complex]("Named Mock")
    aMock.toString should be("Named Mock")
  }

  test("work with mixins") {
    val aMock = mock[XWithY]

    aMock.bar returns "mocked!"
    aMock.traitMethod(any[Int]) returns ValueCaseClassInt(69)

    aMock.bar should be("mocked!")
    aMock.traitMethod(30) should be(ValueCaseClassInt(69))

    MockitoSugar.verify(aMock).traitMethod(30)
  }

  test("work with inline mixins") {
    val aMock: X with Baz = mock[X with Baz]

    aMock.bar returns "mocked!"
    aMock.traitMethod(any[Int]) returns ValueCaseClassInt(69)
    aMock.varargMethod(1, 2, 3) returns 42
    aMock.javaVarargMethod(1, 2, 3) returns 42
    aMock.byNameMethod(69) returns 42

    aMock.bar should be("mocked!")
    aMock.traitMethod(30) should be(ValueCaseClassInt(69))
    aMock.varargMethod(1, 2, 3) should equal(42)
    aMock.javaVarargMethod(1, 2, 3) should equal(42)
    aMock.byNameMethod(69) should equal(42)

    MockitoSugar.verify(aMock).traitMethod(30)
    MockitoSugar.verify(aMock).varargMethod(1, 2, 3)
    MockitoSugar.verify(aMock).javaVarargMethod(1, 2, 3)
    MockitoSugar.verify(aMock).byNameMethod(69)
    a[WantedButNotInvoked] should be thrownBy MockitoSugar.verify(aMock).varargMethod(1, 2)
    a[WantedButNotInvoked] should be thrownBy MockitoSugar.verify(aMock).javaVarargMethod(1, 2)
    a[WantedButNotInvoked] should be thrownBy MockitoSugar.verify(aMock).byNameMethod(71)
  }

  test("work with java varargs") {
    val aMock = mock[JavaFoo]

    aMock.varargMethod(1, 2, 3) returns 42
    aMock.varargMethod(1, 2, 3) should equal(42)

    MockitoSugar.verify(aMock).varargMethod(1, 2, 3)
    a[WantedButNotInvoked] should be thrownBy MockitoSugar.verify(aMock).varargMethod(1, 2)
  }

  test("work when getting varargs from collections") {
    val aMock = mock[Baz]

    aMock.varargMethod("Hello", List(1, 2, 3): _*) returns 42
    aMock.varargMethod("Hello", 1, 2, 3) should equal(42)
    MockitoSugar.verify(aMock).varargMethod("Hello", 1, 2, 3)
  }

  test("work when getting varargs from collections (with matchers)") {
    val aMock = mock[Baz]

    aMock.varargMethod(eqTo("Hello"), eqTo(List(1, 2, 3)): _*) returns 42
    aMock.varargMethod("Hello", 1, 2, 3) should equal(42)
    MockitoSugar.verify(aMock).varargMethod("Hello", 1, 2, 3)
  }

  test("be serializable") {
    val list = mock[java.util.List[String]](withSettings.name("list1").serializable())
    list.get(eqTo(3)) returns "mocked"
    list.get(3) should be("mocked")

    val file = File.createTempFile("mock", "tmp")
    file.deleteOnExit()

    val oos = new ObjectOutputStream(new FileOutputStream(file))
    try {
      oos.writeObject(list)
    } finally {
      oos.close()
    }
  }

  test("work with type aliases") {
    type MyType = String

    val aMock = mock[MyType => String]

    aMock("Hello") returns "Goodbye"
    aMock("Hello") should be("Goodbye")
  }

  test("not mock final methods") {
    val abstractFile = mock[AbstractFile]

    abstractFile.path returns "SomeFile.scala"
    abstractFile.path should be("SomeFile.scala")
  }

  test("create a valid spy for lambdas and anonymous classes") {
    val aSpy = spyLambda((arg: String) => s"Got: $arg")

    aSpy.apply(any[String]) returns "mocked!"
    aSpy("hi!") should be("mocked!")
    MockitoSugar.verify(aSpy).apply("hi!")
  }

  test("eqTo syntax") {
    val aMock = mock[Foo2]

    aMock.valueClass(1, eqTo(new ValueClass("meh"))) returns "mocked!"
    aMock.valueClass(1, new ValueClass("meh")) should be("mocked!")
    aMock.valueClass(1, eqTo(new ValueClass("meh"))) was called

    aMock.valueClass(any[Int], new ValueClass("moo")) returns "mocked!"
    aMock.valueClass(11, new ValueClass("moo")) should be("mocked!")
    aMock.valueClass(any[Int], new ValueClass("moo")) was called

    val valueClass = new ValueClass("blah")
    aMock.valueClass(1, eqTo(valueClass)) returns "mocked!"
    aMock.valueClass(1, valueClass) should be("mocked!")
    aMock.valueClass(1, eqTo(valueClass)) was called

    aMock.valueCaseClass(2, eqTo(ValueCaseClassInt(100))) returns "mocked!"
    aMock.valueCaseClass(2, ValueCaseClassInt(100)) should be("mocked!")
    aMock.valueCaseClass(2, eqTo(ValueCaseClassInt(100))) was called

    val caseClassValue = ValueCaseClassInt(100)
    aMock.valueCaseClass(3, eqTo(caseClassValue)) returns "mocked!"
    aMock.valueCaseClass(3, caseClassValue) should be("mocked!")
    aMock.valueCaseClass(3, eqTo(caseClassValue)) was called

    aMock.valueCaseClass(any[Int], ValueCaseClassInt(200)) returns "mocked!"
    aMock.valueCaseClass(4, ValueCaseClassInt(200)) should be("mocked!")
    aMock.valueCaseClass(any[Int], ValueCaseClassInt(200)) was called
  }

  test("create a mock with default answer") {
    val aMock = mock[Complex](CallsRealMethods)

    aMock.bar should be("not mocked")
  }

  test("create a mock with default answer from implicit scope") {
    implicit val defaultAnswer: DefaultAnswer = CallsRealMethods

    val aMock = mock[Complex]
    aMock.bar should be("not mocked")
  }

  test("stub a value class return value") {
    val aMock = mock[Complex]

    aMock.returnsValueCaseClass returns ValueCaseClassInt(100) andThen ValueCaseClassInt(200)

    aMock.returnsValueCaseClass should be(ValueCaseClassInt(100))
    aMock.returnsValueCaseClass should be(ValueCaseClassInt(200))
  }

  test("stub a map") {
    val mocked = mock[Map[String, String]]
    mocked(any[String]) returns "123"
    mocked("key") should be("123")
  }

  test("work with stubbing value type parameters") {
    val aMock = mock[ParameterizedTraitInt]

    aMock.m() returns 100

    aMock.m() should be(100)
    aMock.m() was called
  }

  test("deal with verifying value type parameters") {
    val aMock = mock[ParameterizedTraitInt]
    //this has to be done separately as the verification in the other test would return the stubbed value so the
    //null problem on the primitive would not happen
    aMock.m() wasNever called
  }

  test("stub a spy that would fail if the real impl is called") {
    val aSpy = spy(new Org)

    an[IllegalArgumentException] should be thrownBy {
      aSpy.iBlowUp(any[Int], any[String]) returns "mocked!"
    }

    "mocked!" willBe returned by aSpy.iBlowUp(any[Int], "ok")

    aSpy.iBlowUp(1, "ok") should be("mocked!")
    aSpy.iBlowUp(2, "ok") should be("mocked!")

    an[IllegalArgumentException] should be thrownBy {
      aSpy.iBlowUp(2, "not ok")
    }
  }

  test("work with parameterized return types") {
    val aMock = mock[HigherKinded[Option]]

    aMock.method returns Some(Right("Mocked!"))
    aMock.method.get.right.get should be("Mocked!")
    aMock.method2
  }

  test("work with parameterized return types declared in parents") {
    val aMock = mock[ConcreteHigherKinded]

    aMock.method returns Some(Right("Mocked!"))

    aMock.method.get.right.get should be("Mocked!")
    aMock.method2
  }

  test("work with higher kinded types and auxiliary methods") {
    def whenGetById[F[_]](algebra: HigherKinded[F]): ScalaFirstStubbing[F[Either[String, String]]] =
      MockitoSugar.when(algebra.method)

    val aMock = mock[HigherKinded[Option]]

    whenGetById(aMock) thenReturn Some(Right("Mocked!"))
    aMock.method.get.right.get should be("Mocked!")
  }

  test("stub a spy with an answer") {
    val aSpy = spy(new Org)

    ((i: Int) => i * 10 + 2) willBe answered by aSpy.doSomethingWithThisInt(any[Int])
    ((i: Int, s: String) => (i * 10 + s.toInt).toString) willBe answered by aSpy
      .doSomethingWithThisIntAndString(any[Int], any[String])
    (
      (
        i: Int,
        s: String,
        boolean: Boolean
      ) => (i * 10 + s.toInt).toString + boolean
    ) willBe answered by aSpy
      .doSomethingWithThisIntAndStringAndBoolean(any[Int], any[String], v3 = true)
    val counter = new AtomicInteger(1)
    (() => counter.getAndIncrement().toString) willBe answered by aSpy.bar
    counter.getAndIncrement().toString willBe answered by aSpy.baz

    counter.get should equal(1)
    aSpy.bar should be("1")
    counter.get should equal(2)
    aSpy.baz should be("2")
    counter.get should equal(3)

    aSpy.doSomethingWithThisInt(4) should equal(42)
    aSpy.doSomethingWithThisIntAndString(4, "2") should be("42")
    aSpy.doSomethingWithThisIntAndStringAndBoolean(4, "2", v3 = true) should be("42true")
    aSpy.doSomethingWithThisIntAndStringAndBoolean(4, "2", v3 = false) should be("not mocked")
  }

  test("create a mock where with mixed matchers and normal parameters (answer)") {
    val org = mock[Org]

    org.doSomethingWithThisIntAndString(*, "test") answers "mocked!"

    org.doSomethingWithThisIntAndString(3, "test") should be("mocked!")
    org.doSomethingWithThisIntAndString(5, "test") should be("mocked!")
    org.doSomethingWithThisIntAndString(5, "est") should not be "mocked!"
  }

  test("create a mock with nice answer API (multiple params)") {
    val aMock = mock[Complex]

    aMock.doSomethingWithThisIntAndString(any[Int], any[String]) answers ((i: Int, s: String) =>
      ValueCaseClassInt(i * 10 + s.toInt)) andThenAnswer ((i: Int, _: String) =>
      ValueCaseClassInt(i))

    aMock.doSomethingWithThisIntAndString(4, "2") should be(ValueCaseClassInt(42))
    aMock.doSomethingWithThisIntAndString(4, "2") should be(ValueCaseClassInt(4))
  }

  test("simplify answer API (invocation usage)") {
    val org = mock[Org]

    org.doSomethingWithThisInt(any[Int]) answers ((i: InvocationOnMock) => i.arg[Int](0) * 10 + 2)
    org.doSomethingWithThisInt(4) should equal(42)
  }

  test("chain answers") {
    val org = mock[Org]

    org.doSomethingWithThisInt(any[Int]) answers ((i: Int) => i * 10 + 2) andThenAnswer ((i: Int) =>
      i * 15 + 9)

    org.doSomethingWithThisInt(4) should equal(42)
    org.doSomethingWithThisInt(4) should equal(69)
  }

  test("chain answers (invocation usage)") {
    val org = mock[Org]

    org.doSomethingWithThisInt(any[Int]) answers ((i: InvocationOnMock) =>
      i.arg[Int](0) * 10 + 2) andThenAnswer ((i: InvocationOnMock) => i.arg[Int](0) * 15 + 9)

    org.doSomethingWithThisInt(4) should equal(42)
    org.doSomethingWithThisInt(4) should equal(69)
  }

  test("allow using less params than method on answer stubbing") {
    val org = mock[Org]

    org.doSomethingWithThisIntAndStringAndBoolean(any[Int], any[String], any[Boolean]) answers (
      (i: Int, s: String) => (i * 10 + s.toInt).toString)
    org.doSomethingWithThisIntAndStringAndBoolean(4, "2", v3 = true) should be("42")
  }

  test("stub a mock inline that has default args") {
    val aMock = mock[Org]

    aMock.returnBar returns mock[Bar] andThen mock[Bar]

    aMock.returnBar should be(a[Bar])
    aMock.returnBar should be(a[Bar])
  }

  test("stub a high order function") {
    val org = mock[Org]

    org.highOrderFunction(any[Int => String]) returns "mocked!"

    org.highOrderFunction(_.toString) should be("mocked!")
  }

  test("stub a method that returns a function") {
    val org = mock[Org]

    org
      .iReturnAFunction(any[Int]).shouldReturn(_.toString).andThen(i =>
        (i * 2).toString).andThenCallRealMethod()

    org.iReturnAFunction(0)(42) should be("42")
    org.iReturnAFunction(0)(42) should be("84")
    org.iReturnAFunction(3)(3) should be("9")
  }

  //useful if we want to delay the evaluation of whatever we are returning until the method is called
  test("simplify stubbing an answer where we don't care about any param") {
    val org = mock[Complex]

    val counter = new AtomicInteger(1)
    org.bar answers counter.getAndIncrement().toString

    counter.get should equal(1)
    org.bar should be("1")
    counter.get should equal(2)
    org.bar should be("2")
  }

  test("create a mock while stubbing another") {
    val aMock = mock[Complex]

    aMock.returnBar returns mock[Bar]
    aMock.returnBar should be(a[Bar])
  }

  test("stub a runtime exception instance to be thrown") {
    val aMock = mock[Foo]

    aMock.bar(any[String]) throws new IllegalArgumentException
    an[IllegalArgumentException] should be thrownBy aMock.bar("alice")
  }

  test("stub a checked exception instance to be thrown") {
    val aMock = mock[Foo]

    aMock.bar(any[String]) throws new Exception
    an[Exception] should be thrownBy aMock.bar("fred")
  }

  test("stub a multiple exceptions instance to be thrown") {
    val aMock = mock[Complex]

    aMock.bar throws new Exception andThenThrow new IllegalArgumentException

    an[Exception] should be thrownBy aMock.bar
    an[IllegalArgumentException] should be thrownBy aMock.bar

    aMock.returnBar throws new IllegalArgumentException andThenThrow new Exception

    an[IllegalArgumentException] should be thrownBy aMock.returnBar
    an[Exception] should be thrownBy aMock.returnBar
  }

  test("default answer should deal with default arguments") {
    val aMock = mock[Complex]

    aMock.iHaveSomeDefaultArguments("I will not pass the second argument")
    aMock.iHaveSomeDefaultArguments("I will pass the second argument", "second argument")

    MockitoSugar
      .verify(aMock)
      .iHaveSomeDefaultArguments("I will not pass the second argument", "default value")
    MockitoSugar
      .verify(aMock).iHaveSomeDefaultArguments("I will pass the second argument", "second argument")
  }

  test("work with by-name arguments (argument order doesn't matter when not using matchers)") {
    val aMock = mock[Complex]

    aMock.iStartWithByNameArgs("arg1", "arg2") returns "mocked!"

    aMock.iStartWithByNameArgs("arg1", "arg2") should be("mocked!")
    aMock.iStartWithByNameArgs("arg111", "arg2") should not be "mocked!"

    MockitoSugar.verify(aMock).iStartWithByNameArgs("arg1", "arg2")
    MockitoSugar.verify(aMock).iStartWithByNameArgs("arg111", "arg2")
  }

  test("work with primitive by-name arguments") {
    val aMock = mock[Complex]

    aMock.iHavePrimitiveByNameArgs(1, "arg2") returns "mocked!"

    aMock.iHavePrimitiveByNameArgs(1, "arg2") should be("mocked!")
    aMock.iHavePrimitiveByNameArgs(2, "arg2") should not be "mocked!"

    MockitoSugar.verify(aMock).iHavePrimitiveByNameArgs(1, "arg2")
    MockitoSugar.verify(aMock).iHavePrimitiveByNameArgs(2, "arg2")
  }

  test("function0 arguments") {
    val aMock = mock[Complex]

    aMock.iHaveFunction0Args(eqTo("arg1"), function0("arg2")) returns "mocked!"

    aMock.iHaveFunction0Args("arg1", () => "arg2") should be("mocked!")
    aMock.iHaveFunction0Args("arg1", () => "arg3") should not be "mocked!"

    MockitoSugar.verify(aMock).iHaveFunction0Args(eqTo("arg1"), function0("arg2"))
    MockitoSugar.verify(aMock).iHaveFunction0Args(eqTo("arg1"), function0("arg3"))
  }

  test("ignore the calls to the methods that provide default arguments") {
    val aMock = mock[Complex]

    aMock.iHaveSomeDefaultArguments("I will not pass the second argument")

    MockitoSugar
      .verify(aMock)
      .iHaveSomeDefaultArguments("I will not pass the second argument", "default value")
    verifyNoMoreInteractions(aMock)
  }

  test("support an ArgCaptor and deal with default arguments") {
    val aMock = mock[Complex]

    aMock.iHaveSomeDefaultArguments("I will not pass the second argument")

    val captor1 = ArgCaptor[String]
    val captor2 = ArgCaptor[String]
    MockitoSugar.verify(aMock).iHaveSomeDefaultArguments(captor1, captor2)

    captor1 hasCaptured "I will not pass the second argument"
    captor2 hasCaptured "default value"
  }

  test("work with Future types 1") {
    val mockFn = mock[() => Int]
    mockFn() returns 42

    Future(mockFn.apply()).map { v =>
      v should equal(42)
      mockFn() was called
    }
  }

  test("work with Future types 2") {
    val mockFutureFn = mock[() => Future[Int]]
    mockFutureFn() returns Future(42)

    Await.result(mockFutureFn(), 2.seconds) should equal(42)
  }

  test("works with Future whenReady 1") {
    val mockFn = mock[() => Unit]

    val f = Future(mockFn.apply())
    whenReady(f)(_ => mockFn() was called)
  }

  test("works with Future whenReady 2") {
    val mockFutureFn = mock[() => Future[Int]]
    mockFutureFn() returns Future(42)

    whenReady(mockFutureFn())(result => result should equal(42))
  }

  test("works with Function types") {
    val expected = Future("Hello, world")
    val mockFunction = mock[Int => Future[String]]
    mockFunction(any[Int]) returns expected

    mockFunction(42) should equal(expected)
  }

  test("works with Function types no sugar") {
    val expected = Future("Hello, world")
    val mockFunction = mock[Function1[Int, Future[String]]]
    mockFunction(any[Int]) returns expected

    mockFunction(42) should equal(expected)
  }

  test("works with Function whenReady") {
    val mockFunction = mock[Int => Future[String]]
    mockFunction(any[Int]) returns Future("Hello, world")

    whenReady(mockFunction(42))(result => result should equal("Hello, world"))
  }

  test("works with Function whenReady no sugar") {
    val mockFunction = mock[Function1[Int, Future[String]]]
    mockFunction(any[Int]) returns Future("Hello, world")

    whenReady(mockFunction(42))(result => result should equal("Hello, world"))
  }

  test("disallow passing traits in the settings") {
    a[IllegalArgumentException] should be thrownBy {
      mock[Foo](withSettings.extraInterfaces(classOf[Baz]))
    }
  }

  private final implicit def convertTwitterFuture[T](twitterFuture: Future[T]): FutureConcept[T] =
    new FutureConcept[T] {
      def eitherValue: Option[Either[Throwable, T]] =
        twitterFuture.poll.map {
          case Return(result) => Right(result)
          case Throw(e) => Left(e)
        }
      // As per the ScalaTest docs if the underlying future does not support these they
      // must return false.
      def isExpired: Boolean = false
      def isCanceled: Boolean = false
    }
}
