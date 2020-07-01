package com.twitter.app.lifecycle

/**
 * Represents the phase in the application lifecycle that an event occurs in.
 *
 * App's Lifecycle comprises the following phases
 *
 *   [ Register ] -> [ Load Bindings ] -> [ Init ] -> [ Parse Args ] -> [ Pre Main ] ->
 *            [ Main ] -> [ Post Main ] -> [ Close ] -> [ CloseExitLast ]
 *
 * @see [[https://twitter.github.io/twitter-server/Features.html#lifecycle-management TwitterServer]]
 * @see [[https://twitter.github.io/finatra/user-guide/getting-started/lifecycle.html Finatra]]
 */
private[twitter] sealed trait Event

private[twitter] object Event {

  /* com.twitter.app.App + com.twitter.server.Server events */
  case object Register extends Event
  case object LoadBindings extends Event
  case object Init extends Event
  case object ParseArgs extends Event
  case object PreMain extends Event
  case object Main extends Event
  case object PostMain extends Event
  case object Close extends Event
  case object CloseExitLast extends Event

  /* com.twitter.server.Lifecycle.Warmup events */
  case object PrebindWarmup extends Event
  case object WarmupComplete extends Event

  /* com.twitter.inject.App + com.twitter.inject.TwitterServer events */
  /*
   * [ Register ] -> [ Load Bindings] -> [ Init ] -> [ Parse Args ] -> [ Pre Main ] ->
   *   [ Main // the following events take place within main()
   *      ( Post Injector Startup ) -> ( Warmup ) -> ( Before Post Warmup ) -> ( Post Warmup ) ->
   *      ( After Post Warmup )
   *   ] -> [ Post Main ] -> [ Close ] -> [ CloseExitLast ]
   */
  case object PostInjectorStartup extends Event
  case object Warmup extends Event
  case object BeforePostWarmup extends Event
  case object PostWarmup extends Event
  case object AfterPostWarmup extends Event
}
