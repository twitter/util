package com.twitter.app.lifecycle

/**
 * A trait for observing lifecycle event notifications
 */
private[twitter] trait Observer {

  /** notification that the [[event]] lifecycle phase has begun */
  def onEntry(event: Event): Unit = ()

  /** notification that the [[event]] lifecycle phase has completed successfully */
  def onSuccess(event: Event): Unit

  /** notification that the [[event]] lifecycle phase has encountered [[throwable]] error */
  def onFailure(stage: Event, throwable: Throwable): Unit
}
