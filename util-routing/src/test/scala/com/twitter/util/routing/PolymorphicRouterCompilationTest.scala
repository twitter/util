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
          override def apply(label: String, routes: Iterable[StatusHandler]): StatusRouter =
            StatusRouter(label, routes)
        })
  }

  case class StatusRouter(label: String, routes: Iterable[StatusHandler])
      extends Router[String, StatusHandler] {
    override protected def find(input: String): Option[StatusHandler] = routes.find(_.str == input)
  }

  val statusRouter: StatusRouter = StatusRouter.newBuilder
    .withRoute(StatusHandlerA("success", Success))
    .withRoute(StatusHandlerB("fail", Failure))
    .newRouter()

  def checkStatusRouterType: Router[String, StatusHandler] = statusRouter

  val covariantStatusRouter: Router[String, StatusHandler] = new Router[String, StatusHandlerA] {
    override def label: String = "covariant-status"
    override def routes: Iterable[StatusHandlerA] = Iterable.empty
    override protected def find(input: String): Option[StatusHandlerA] = None
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
        override def apply(label: String, routes: Iterable[CommandHandler]): CommandRouter =
          CommandRouter(label, routes)
      })
  }

  case class CommandRouter(label: String, routes: Iterable[CommandHandler])
      extends Router[Command, CommandHandler] {
    override protected def find(input: Command): Option[CommandHandler] =
      routes.find(_.canHandle(input))
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
    assert(statusRouter("success") == Some(StatusHandlerA("success", Success)))
    assert(statusRouter("fail") == Some(StatusHandlerB("fail", Failure)))
    assert(statusRouter("meh") == None)
  }

  test("Supports routing with polymorphic inputs") {
    assert(commandRouter(IntCommand(1)) == Some(CommandHandlerA))
    assert(commandRouter(StringCommand("1")) == Some(CommandHandlerB))
    assert(commandRouter(NullCommand) == None)
  }

  test("Input type cannot be covariant") {
    assertDoesNotCompile {
      """
        |  val contravariantInputRouter: Router[Command, CommandHandler] = new Router[IntCommand, CommandHandler] {
        |    override def label: String = "int-command-router"
        |    override def routes: Iterable[CommandHandler] = Iterable.empty
        |    override protected def find(input: IntCommand): Option[CommandHandler] = None
        |  }
        |""".stripMargin
    }
  }

  test("Input type cannot be contravariant") {
    assertDoesNotCompile {
      """
        |  val contravariantInputRouter: Router[IntCommand, CommandHandler] = new Router[Command, CommandHandler] {
        |    override def label: String = "int-command-router"
        |    override def routes: Iterable[CommandHandler] = Iterable.empty
        |    override protected def find(input: Command): Option[CommandHandler] = None
        |  }
        |""".stripMargin
    }
  }

  test("Destination type cannot be contravariant") {
    assertDoesNotCompile {
      """
        |  val contravariantStatusRouter: Router[String, StatusHandlerA] = new Router[String, StatusHandler] {
        |    override def label: String = "contravariant-status"
        |    override def routes: Iterable[StatusHandler] = Iterable.empty
        |    override protected def find(input: String): Option[StatusHandler] = None
        |  }
        |""".stripMargin
    }
  }

}
