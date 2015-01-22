package com.twitter.util.testing

import java.util.{List => JList}
import org.mockito.ArgumentCaptor
import org.mockito.exceptions.Reporter
import scala.collection.JavaConversions._
import scala.reflect._

trait ArgumentCapture {
  /**
   * Enables capturingOne to be implemented over capturingAll with the same behavior as ArgumentCaptor.getValue
   */
  private[this] def noArgWasCaptured: Nothing = {
    new Reporter().noArgumentValueWasCaptured() // this always throws an exception
    throw new RuntimeException("this should be unreachable, but allows the method to be of type Nothing")
  }

  private[this] def zip4[T, U, V, W](args: (JList[T], JList[U], JList[V], JList[W])): Seq[(T, U, V, W)] = {
    (args._1, args._2, args._3).zipped.toIterator
        .zip(args._4.toIterator)
        .map { case ((a, b, c), d) => (a, b, c, d) }
        .toSeq
  }

  private[this] def zip5[T, U, V, W, X](args: (JList[T], JList[U], JList[V], JList[W], JList[X])): Seq[(T, U, V, W, X)] = {
    (args._1, args._2, args._3).zipped.toIterator
      .zip((args._4, args._5).zipped.toIterator)
      .map { case ((a, b, c), (d, e)) => (a, b, c, d, e) }
      .toSeq
  }

  /**
   * Capture an argument from a verify(mock).method(arg) call.
   *
   * Example:
   *   val request = capturingOne(verify(myAPIEndpoint).authenticate _)
   *   request.userId must equal (123L)
   *   request.password must equal ("reallySecurePassword")
   */
  def capturingOne[T: ClassTag](f: T => _): T =
    capturingAll[T](f).lastOption.getOrElse(noArgWasCaptured)

  def capturingOne[T: ClassTag, U: ClassTag](f: (T, U) => _): (T, U) =
    capturingAll[T, U](f).lastOption.getOrElse(noArgWasCaptured)

  def capturingOne[T: ClassTag, U: ClassTag, V: ClassTag](f: (T, U, V) => _): (T, U, V) =
    capturingAll[T, U, V](f).lastOption.getOrElse(noArgWasCaptured)

  def capturingOne[T: ClassTag, U: ClassTag, V: ClassTag, W: ClassTag](f: (T, U, V, W) => _): (T, U, V, W) =
    capturingAll[T, U, V, W](f).lastOption.getOrElse(noArgWasCaptured)

  def capturingOne[T: ClassTag, U: ClassTag, V: ClassTag, W: ClassTag, X: ClassTag](f: (T, U, V, W, X) => _): (T, U, V, W, X) =
    capturingAll[T, U, V, W, X](f).lastOption.getOrElse(noArgWasCaptured)

  /**
   * Capture all the invocations from a verify(mock).method(arg) call.
   *
   * Example:
   *   val requests = capturingAll(verify(myAPIEndpoint, times(4)).authenticate _)
   *   requests.length must equal (4)
   */
  def capturingAll[T: ClassTag](f: T => _): Seq[T] = {
    val argCaptor = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    f(argCaptor.capture())
    argCaptor.getAllValues.toSeq
  }

  def capturingAll[T: ClassTag, U: ClassTag](f: (T, U) => _): Seq[(T, U)] = {
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    f(argCaptorT.capture(), argCaptorU.capture())
    val t = argCaptorT.getAllValues
    val u = argCaptorU.getAllValues
    (t, u).zipped.toSeq
  }

  def capturingAll[T: ClassTag, U: ClassTag, V: ClassTag](f: (T, U, V) => _): Seq[(T, U, V)] = {
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    val argCaptorV = ArgumentCaptor.forClass(classTag[V].runtimeClass.asInstanceOf[Class[V]])
    f(argCaptorT.capture(), argCaptorU.capture(), argCaptorV.capture())
    val t = argCaptorT.getAllValues
    val u = argCaptorU.getAllValues
    val v = argCaptorV.getAllValues
    (t, u, v).zipped.toSeq
  }

  def capturingAll[T: ClassTag, U: ClassTag, V: ClassTag, W: ClassTag](f: (T, U, V, W) => _): Seq[(T, U, V, W)] = {
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    val argCaptorV = ArgumentCaptor.forClass(classTag[V].runtimeClass.asInstanceOf[Class[V]])
    val argCaptorW = ArgumentCaptor.forClass(classTag[W].runtimeClass.asInstanceOf[Class[W]])
    f(argCaptorT.capture(), argCaptorU.capture(), argCaptorV.capture(), argCaptorW.capture())
    val t = argCaptorT.getAllValues
    val u = argCaptorU.getAllValues
    val v = argCaptorV.getAllValues
    val w = argCaptorW.getAllValues
    zip4((t, u, v, w))
  }

  def capturingAll[T: ClassTag, U: ClassTag, V: ClassTag, W: ClassTag, X: ClassTag](f: (T, U, V, W, X) => _): Seq[(T, U, V, W, X)] = {
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    val argCaptorV = ArgumentCaptor.forClass(classTag[V].runtimeClass.asInstanceOf[Class[V]])
    val argCaptorW = ArgumentCaptor.forClass(classTag[W].runtimeClass.asInstanceOf[Class[W]])
    val argCaptorX = ArgumentCaptor.forClass(classTag[W].runtimeClass.asInstanceOf[Class[X]])
    f(argCaptorT.capture(), argCaptorU.capture(), argCaptorV.capture(), argCaptorW.capture(), argCaptorX.capture())
    val t = argCaptorT.getAllValues
    val u = argCaptorU.getAllValues
    val v = argCaptorV.getAllValues
    val w = argCaptorW.getAllValues
    val x = argCaptorX.getAllValues
    zip5((t, u, v, w, x))
  }
}
