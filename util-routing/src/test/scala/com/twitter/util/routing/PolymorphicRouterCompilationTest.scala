package com.twitter.util.routing

import org.scalatest.funsuite.AnyFunSuite

private object PolymorphicRouterCompilationTest {

  /* polymorphic output type router */

  sealed trait Status
  case object Success extends Status
  case object Failure extends Status

  sealed trait StatusHandler {
    def str: String
    def status: Status
  }
  case class StatusHandlerA(str: String, status: Status) extends StatusHandler
  case class StatusHandlerB(str: String, status: Status) extends StatusHandler

  object StatusRouter {
    def newBuilder: RouterBuilder[String, StatusHandler, StatusRouter] =
      RouterBuilder.newBuilder[String, StatusHandler, StatusRouter](
        new Generator[String, StatusHandler, StatusRouter] {
          def apply(labelAndRoutes: RouterInfo[StatusHandler]): StatusRouter =
            new StatusRouter(labelAndRoutes.label, labelAndRoutes.routes)
        })
  }

  class StatusRouter(label: String, routes: Iterable[StatusHandler])
      extends Router[String, StatusHandler](label, routes) {
    protected def find(input: String): Result = {
      routes.find(_.str == input) match {
        case Some(route) => Found(input, route)
        case _ => NotFound
      }
    }
  }

  val statusRouter: StatusRouter = StatusRouter.newBuilder
    .withRoute(StatusHandlerA("success", Success))
    .withRoute(StatusHandlerB("fail", Failure))
    .newRouter()

  def checkStatusRouterType: Router[String, StatusHandler] = statusRouter

  val covariantStatusRouter: Router[String, StatusHandler] =
    new Router[String, StatusHandlerA]("covariant-status", Iterable.empty) {
      protected def find(input: String): Result = NotFound
    }

  /* polymorphic input type router */

  sealed trait Command
  case class IntCommand(value: Int) extends Command
  case class StringCommand(value: String) extends Command
  case object NullCommand extends Command

  case class CommandHandler(canHandle: Command => Boolean, status: Status)

  object CommandRouter {
    def newBuilder: RouterBuilder[Command, CommandHandler, CommandRouter] =
      RouterBuilder.newBuilder(new Generator[Command, CommandHandler, CommandRouter] {
        def apply(labelAndRoutes: RouterInfo[CommandHandler]): CommandRouter =
          new CommandRouter(labelAndRoutes.label, labelAndRoutes.routes)
      })
  }

  class CommandRouter(label: String, routes: Iterable[CommandHandler])
      extends Router[Command, CommandHandler](label, routes) {
    protected def find(input: Command): Result =
      routes.find(_.canHandle(input)) match {
        case Some(r) => Found(input, r)
        case _ => NotFound
      }
  }

  val CommandHandlerA: CommandHandler = CommandHandler(_.isInstanceOf[IntCommand], Success)
  val CommandHandlerB: CommandHandler = CommandHandler(_.isInstanceOf[StringCommand], Failure)

  val commandRouter: CommandRouter = CommandRouter.newBuilder
    .withRoute(CommandHandlerA)
    .withRoute(CommandHandlerB)
    .newRouter()

  def checkCommandRouterType: Router[Command, CommandHandler] = commandRouter

}

// these tests ensure that we properly support polymorphic Router and RouterBuilder types
class PolymorphicRouterCompilationTest extends AnyFunSuite {
  import PolymorphicRouterCompilationTest._

  test("Supports routing with polymorphic destinations") {
    assert(statusRouter("success") == Found("success", StatusHandlerA("success", Success)))
    assert(statusRouter("fail") == Found("fail", StatusHandlerB("fail", Failure)))
    assert(statusRouter("meh") == NotFound)
  }

  test("Supports routing with polymorphic inputs") {
    assert(commandRouter(IntCommand(1)) == Found(IntCommand(1), CommandHandlerA))
    assert(commandRouter(StringCommand("1")) == Found(StringCommand("1"), CommandHandlerB))
    assert(commandRouter(NullCommand) == NotFound)
  }

  test("Input type cannot be covariant") {
    assertDoesNotCompile {
      """
        |  val contravariantInputRouter: Router[Command, CommandHandler] = new Router[IntCommand, CommandHandler]("int-command-router", Iterable.empty) {
        |    protected def find(input: IntCommand): Result = NotFound
        |  }
        |""".stripMargin
    }
  }

  test("Input type cannot be contravariant") {
    assertDoesNotCompile {
      """
        |  val contravariantInputRouter: Router[IntCommand, CommandHandler] = new Router[Command, CommandHandler]("int-command-router", Iterable.empty) {
        |    protected def find(input: Command): Result = NotFound
        |  }
        |""".stripMargin
    }
  }

  test("Destination type cannot be contravariant") {
    assertDoesNotCompile {
      """
        |  val contravariantStatusRouter: Router[String, StatusHandlerA] = new Router[String, StatusHandler]("contravariant-status", Iterable.empty[StatusHandler]) {
        |    protected def find(input: String): Result = NotFound
        |  }
        |""".stripMargin
    }
  }

}
