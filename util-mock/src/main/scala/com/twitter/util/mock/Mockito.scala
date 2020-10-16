package com.twitter.util.mock

import org.mockito.{ArgumentMatchersSugar, IdiomaticMockito}

/**
 * Helper for Mockito Scala sugar with [[https://github.com/mockito/mockito-scala#idiomatic-mockito idiomatic stubbing]].
 * Java users are encouraged to use `org.mockito.Mockito` directly.
 *
 * Note that the Specs2 `smartMock[]` or `mock[].smart` is the default behavior
 * for [[https://github.com/mockito/mockito-scala Mockito Scala]].
 *
 * =Usage=
 *
 * This trait uses `org.mockito.IdiomaticMockito` which is heavily influenced by ScalaTest Matchers.
 *
 * To use, mix in the [[com.twitter.util.mock.Mockito]] trait where desired.
 *
 * ==Create a new mock==
 *
 * {{{
 *
 * trait Foo {
 *   def bar: String
 *   def bar(v: Int): Int
 * }
 *
 *  class MyTest extends AnyFunSuite with Mockito {
 *     val aMock = mock[Foo]
 *  }
 * }}}
 *
 * ==Expect behavior==
 *
 * {{{
 *   // "when" equivalents
 *   aMock.bar returns "mocked!"
 *   aMock.bar returns "mocked!" andThen "mocked again!"
 *   aMock.bar shouldCall realMethod
 *   aMock.bar.shouldThrow[IllegalArgumentException]
 *   aMock.bar throws new IllegalArgumentException
 *   aMock.bar answers "mocked!"
 *   aMock.bar(*) answers ((i: Int) => i * 10)
 *
 *   // "do-when" equivalents
 *   "mocked!" willBe returned by aMock.bar
 *   "mocked!" willBe answered by aMock.bar
 *   ((i: Int) => i * 10) willBe answered by aMock.bar(*)
 *   theRealMethod willBe called by aMock.bar
 *   new IllegalArgumentException willBe thrown by aMock.bar
 *   aMock.bar.doesNothing() // doNothing().when(aMock).bar
 *
 *   // verifications
 *   aMock wasNever called          // verifyZeroInteractions(aMock)
 *   aMock.bar was called
 *   aMock.bar(*) was called        // '*' is shorthand for 'any()' or 'any[T]'
 *   aMock.bar(any[Int]) was called // same as above but with typed input matcher
 *
 *   aMock.bar wasCalled onlyHere
 *   aMock.bar wasNever called
 *
 *   aMock.bar wasCalled twice
 *   aMock.bar wasCalled 2.times
 *
 *   aMock.bar wasCalled fourTimes
 *   aMock.bar wasCalled 4.times
 *
 *   aMock.bar wasCalled atLeastFiveTimes
 *   aMock.bar wasCalled atLeast(fiveTimes)
 *   aMock.bar wasCalled atLeast(5.times)
 *
 *   aMock.bar wasCalled atMostSixTimes
 *   aMock.bar wasCalled atMost(sixTimes)
 *   aMock.bar wasCalled atMost(6.times)
 *
 *   aMock.bar wasCalled (atLeastSixTimes within 2.seconds) // verify(aMock, timeout(2000).atLeast(6)).bar
 *
 *   aMock wasNever calledAgain // verifyNoMoreInteractions(aMock)
 *
 *   InOrder(mock1, mock2) { implicit order =>
 *     mock2.someMethod() was called
 *     mock1.anotherMethod() was called
 *   }
 * }}}
 *
 * Note the 'dead code' warning that can happen when using 'any' or '*'
 * [[https://github.com/mockito/mockito-scala#dead-code-warning matchers]].
 *
 * ==Mixing and matching matchers==
 *
 * Using the idiomatic syntax also allows for mixing argument matchers with real values. E.g., you
 * are no longer forced to use argument matchers for all parameters as soon as you use one. E.g.,
 *
 * {{{
 *   trait Foo {
 *     def bar(v: Int, v2: Int, v3: Int = 42): Int
 *   }
 *
 *   class MyTest extends AnyFunSuite with Mockito {
 *     val aMock = mock[Foo]
 *
 *     aMock.bar(1, 2) returns "mocked!"
 *     aMock.bar(1, *) returns "mocked!"
 *     aMock.bar(1, any[Int]) returns "mocked!"
 *     aMock.bar(*, *) returns "mocked!"
 *     aMock.bar(any[Int], any[Int]) returns "mocked!"
 *     aMock.bar(*, *, 3) returns "mocked!"
 *     aMock.bar(any[Int], any[Int], 3) returns "mocked!"
 *
 *     "mocked!" willBe returned by aMock.bar(1, 2)
 *     "mocked!" willBe returned by aMock.bar(1, *)
 *     "mocked!" willBe returned by aMock.bar(1, any[Int])
 *     "mocked!" willBe returned by aMock.bar(*, *)
 *     "mocked!" willBe returned by aMock.bar(any[Int], any[Int])
 *     "mocked!" willBe returned by aMock.bar(*, *, 3)
 *     "mocked!" willBe returned by aMock.bar(any[Int], any[Int], 3)
 *
 *     aMock.bar(1, 2) was called
 *     aMock.bar(1, *) was called
 *     aMock.bar(1, any[Int]) was called
 *     aMock.bar(*, *) was called
 *     aMock.bar(any[Int], any[Int]) was called
 *     aMock.bar(*, *, 3) was called
 *     aMock.bar(any[Int], any[Int], 3) was called
 *   }
 * }}}
 *
 * See [[https://github.com/mockito/mockito-scala#mixing-normal-values-with-argument-matchers Mix-and-Match]]
 * for more information including a caveat around curried functions with default arguments.
 *
 * ==Numeric Matchers==
 *
 * Numeric comparisons are possible for argument matching, e.g.,
 *
 * {{{
 *   aMock.method(5)
 *
 *   aMock.method(n > 4.99) was called
 *   aMock.method(n >= 5) was called
 *   aMock.method(n < 5.1) was called
 *   aMock.method(n <= 5) was called
 * }}}
 *
 * See [[https://github.com/mockito/mockito-scala#numeric-matchers Numeric Matchers]].
 *
 * ==Vargargs==
 *
 * Most matches will deal with varargs out of the box, just note when using the 'eqTo' matcher to
 * apply it to all the arguments as one (not individually).
 *
 * See [[https://github.com/mockito/mockito-scala#varargs Varargs]].
 *
 * ==More Information==
 *
 * See the [[https://github.com/mockito/mockito-scala#idiomatic-mockito IdiomaticMockito]] documentation
 * for more specific information and the [[https://github.com/mockito/mockito-scala Mockito Scala]]
 * [[https://github.com/mockito/mockito-scala#getting-started Getting Started]] documentation for general
 * information.
 *
 * see `org.mockito.IdiomaticMockito`
 * see `org.mockito.ArgumentMatchersSugar`
 */
trait Mockito extends IdiomaticMockito with ArgumentMatchersSugar

/**
 * Simple object to allow the usage of the trait without mixing it in
 */
object Mockito extends Mockito
