package com.twitter.util.routing

import com.twitter.util.routing.simple.{SimpleRoute, SimpleRouter}
import org.scalatest.funsuite.AnyFunSuite

class RouterBuilderTest extends AnyFunSuite {

  private object TestRouter {
    def newBuilder(): RouterBuilder[String, SimpleRoute, SimpleRouter] =
      RouterBuilder.newBuilder(new Generator[String, SimpleRoute, SimpleRouter] {
        def apply(labelAndRoutes: RouterInfo[SimpleRoute]): SimpleRouter =
          new SimpleRouter(labelAndRoutes.routes.map { r => r.in -> r }.toMap)
      })
  }

  private object ValidatingTestRouterBuilder {
    def newBuilder(): RouterBuilder[String, SimpleRoute, SimpleRouter] =
      TestRouter.newBuilder.withValidator(
        new Validator[SimpleRoute] {
          def apply(routes: Iterable[SimpleRoute]): Iterable[ValidationError] =
            routes.collect {
              case r if r.in == "invalid" => ValidationError(s"INVALID @ $r")
            }
        }
      )
  }

  test("can build routes") {
    val router = TestRouter
      .newBuilder()
      .withRoute(SimpleRoute("x", true))
      .withRoute(SimpleRoute("y", false))
      .withRoute(SimpleRoute("z", true))
      .newRouter()

    assert(router("x") == Found("x", SimpleRoute("x", true)))
    assert(router("y") == Found("y", SimpleRoute("y", false)))
    assert(router("z") == Found("z", SimpleRoute("z", true)))
    assert(router("a") == NotFound)
  }

  test("throws when invalid route is passed") {
    val router = ValidatingTestRouterBuilder.newBuilder
      .withRoute(SimpleRoute("x", true))
      .withRoute(SimpleRoute("y", false))
      .withRoute(SimpleRoute("z", true))

    router.newRouter() // everything is valid up to here

    val err = intercept[ValidationException] {
      router.withRoute(SimpleRoute("invalid", false)).newRouter()
    }
    assert(
      err.getMessage == "Route Validation Failed! Errors encountered: [INVALID @ SimpleRoute(invalid,false)]")
  }

  test("throws when multiple invalid routes are passed") {
    val router = ValidatingTestRouterBuilder.newBuilder
      .withRoute(SimpleRoute("x", true))
      .withRoute(SimpleRoute("y", false))
      .withRoute(SimpleRoute("z", true))

    router.newRouter() // everything is valid up to here

    val err = intercept[ValidationException] {
      router
        .withRoute(SimpleRoute("invalid", false))
        .withRoute(SimpleRoute("invalid", true))
        .newRouter()
    }
    assert(
      err.getMessage == "Route Validation Failed! Errors encountered: [INVALID @ SimpleRoute(invalid,false), INVALID @ SimpleRoute(invalid,true)]")
  }

  test("support contravariant builder") {
    assertCompiles {
      """
        |    val typed: RouterBuilder[String, SimpleRoute, SimpleRouter] = ValidatingTestRouterBuilder.newBuilder
        |    val generic: RouterBuilder[String, SimpleRoute, Router[String, SimpleRoute]] = typed
        |    val simpleRouter: SimpleRouter = typed.newRouter()
        |    val simpleRouterB: Router[String, SimpleRoute] = generic.newRouter()
        |    val simpleRouterC: Router[String, SimpleRoute] = typed.newRouter()
        |""".stripMargin
    }
  }

}
