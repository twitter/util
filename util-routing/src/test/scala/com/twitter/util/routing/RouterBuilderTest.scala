package com.twitter.util.routing

import com.twitter.util.routing.simple.{SimpleRoute, SimpleRouter}
import org.scalatest.FunSuite

class RouterBuilderTest extends FunSuite {

  private object TestRouter {
    def newBuilder(): TestRouterBuilder = new TestRouterBuilder()
  }

  private class TestRouterBuilder private[RouterBuilderTest] ()
      extends RouterBuilder[String, SimpleRoute, SimpleRouter] {

    protected def newRouter(routes: Iterable[SimpleRoute]): SimpleRouter =
      new SimpleRouter(routes.map { r => r.in -> r }.toMap)

  }

  private class ValidatingTestRouterBuilder private[RouterBuilderTest] ()
      extends RouterBuilder[String, SimpleRoute, SimpleRouter](routeValidator = r =>
        if (r.in == "invalid") throw new IllegalArgumentException("INVALID!")) {

    protected def newRouter(routes: Iterable[SimpleRoute]): SimpleRouter =
      new SimpleRouter(routes.map { r => r.in -> r }.toMap)

  }

  test("can build routes") {
    val router = TestRouter
      .newBuilder()
      .withRoute(SimpleRoute("x", true))
      .withRoute(SimpleRoute("y", false))
      .withRoute(SimpleRoute("z", true))
      .newRouter()

    assert(router("x") == Some(SimpleRoute("x", true)))
    assert(router("y") == Some(SimpleRoute("y", false)))
    assert(router("z") == Some(SimpleRoute("z", true)))
    assert(router("a") == None)
  }

  test("throws when invalid route is passed") {
    val router = new ValidatingTestRouterBuilder()
      .withRoute(SimpleRoute("x", true))
      .withRoute(SimpleRoute("y", false))
      .withRoute(SimpleRoute("z", true))

    intercept[IllegalArgumentException] {
      router.withRoute(SimpleRoute("invalid", false))
    }
  }

}
