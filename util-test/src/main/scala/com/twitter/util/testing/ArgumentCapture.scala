package com.twitter.util.testing

import java.util.{List => JList}
import org.mockito.ArgumentCaptor
import org.mockito.exceptions.Reporter
import scala.collection.JavaConverters._
import scala.reflect._

// This file was generated from codegen/util-test/ArgumentCapture.scala.mako

trait ArgumentCapture {
  /**
   * Enables capturingOne to be implemented over capturingAll with the same behavior as ArgumentCaptor.getValue
   */
  private[this] def noArgWasCaptured(): Nothing = {
    new Reporter().noArgumentValueWasCaptured() // this always throws an exception
    throw new RuntimeException("this should be unreachable, but allows the method to be of type Nothing")
  }

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
    argCaptor.getAllValues.asScala.toSeq
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
    capturingAll[T](f).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 2 iterables together into a Seq of 2-tuples. */
  private[this] def zipN[A, B](arg0: Iterable[A], arg1: Iterable[B]): Seq[(A, B)] = {
    arg0.zip(arg1).toSeq
  }

  /** Capture all invocations of a mocked 2-ary method */
  def capturingAll[A: ClassTag, B: ClassTag](func: (A, B) => _): Seq[(A, B)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    func(argCaptorA.capture(), argCaptorB.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    zipN(argsA, argsB)
  }

  /** Capture one invocation of a mocked 2-ary method */
  def capturingOne[A: ClassTag, B: ClassTag](func: (A, B) => _): (A, B) =
    capturingAll[A, B](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 3 iterables together into a Seq of 3-tuples. */
  private[this] def zipN[A, B, C](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C]): Seq[(A, B, C)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .map({ case ((a, b), c) => (a, b, c) })
      .toSeq
  }

  /** Capture all invocations of a mocked 3-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag](func: (A, B, C) => _): Seq[(A, B, C)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    zipN(argsA, argsB, argsC)
  }

  /** Capture one invocation of a mocked 3-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag](func: (A, B, C) => _): (A, B, C) =
    capturingAll[A, B, C](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 4 iterables together into a Seq of 4-tuples. */
  private[this] def zipN[A, B, C, D](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D]): Seq[(A, B, C, D)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .map({ case (((a, b), c), d) => (a, b, c, d) })
      .toSeq
  }

  /** Capture all invocations of a mocked 4-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag](func: (A, B, C, D) => _): Seq[(A, B, C, D)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD)
  }

  /** Capture one invocation of a mocked 4-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag](func: (A, B, C, D) => _): (A, B, C, D) =
    capturingAll[A, B, C, D](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 5 iterables together into a Seq of 5-tuples. */
  private[this] def zipN[A, B, C, D, E](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E]): Seq[(A, B, C, D, E)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .map({ case ((((a, b), c), d), e) => (a, b, c, d, e) })
      .toSeq
  }

  /** Capture all invocations of a mocked 5-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag](func: (A, B, C, D, E) => _): Seq[(A, B, C, D, E)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE)
  }

  /** Capture one invocation of a mocked 5-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag](func: (A, B, C, D, E) => _): (A, B, C, D, E) =
    capturingAll[A, B, C, D, E](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 6 iterables together into a Seq of 6-tuples. */
  private[this] def zipN[A, B, C, D, E, F](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F]): Seq[(A, B, C, D, E, F)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .map({ case (((((a, b), c), d), e), f) => (a, b, c, d, e, f) })
      .toSeq
  }

  /** Capture all invocations of a mocked 6-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag](func: (A, B, C, D, E, F) => _): Seq[(A, B, C, D, E, F)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF)
  }

  /** Capture one invocation of a mocked 6-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag](func: (A, B, C, D, E, F) => _): (A, B, C, D, E, F) =
    capturingAll[A, B, C, D, E, F](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 7 iterables together into a Seq of 7-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G]): Seq[(A, B, C, D, E, F, G)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .map({ case ((((((a, b), c), d), e), f), g) => (a, b, c, d, e, f, g) })
      .toSeq
  }

  /** Capture all invocations of a mocked 7-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag](func: (A, B, C, D, E, F, G) => _): Seq[(A, B, C, D, E, F, G)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG)
  }

  /** Capture one invocation of a mocked 7-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag](func: (A, B, C, D, E, F, G) => _): (A, B, C, D, E, F, G) =
    capturingAll[A, B, C, D, E, F, G](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 8 iterables together into a Seq of 8-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H]): Seq[(A, B, C, D, E, F, G, H)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .map({ case (((((((a, b), c), d), e), f), g), h) => (a, b, c, d, e, f, g, h) })
      .toSeq
  }

  /** Capture all invocations of a mocked 8-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag](func: (A, B, C, D, E, F, G, H) => _): Seq[(A, B, C, D, E, F, G, H)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH)
  }

  /** Capture one invocation of a mocked 8-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag](func: (A, B, C, D, E, F, G, H) => _): (A, B, C, D, E, F, G, H) =
    capturingAll[A, B, C, D, E, F, G, H](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 9 iterables together into a Seq of 9-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I]): Seq[(A, B, C, D, E, F, G, H, I)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .map({ case ((((((((a, b), c), d), e), f), g), h), i) => (a, b, c, d, e, f, g, h, i) })
      .toSeq
  }

  /** Capture all invocations of a mocked 9-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag](func: (A, B, C, D, E, F, G, H, I) => _): Seq[(A, B, C, D, E, F, G, H, I)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI)
  }

  /** Capture one invocation of a mocked 9-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag](func: (A, B, C, D, E, F, G, H, I) => _): (A, B, C, D, E, F, G, H, I) =
    capturingAll[A, B, C, D, E, F, G, H, I](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 10 iterables together into a Seq of 10-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J]): Seq[(A, B, C, D, E, F, G, H, I, J)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .map({ case (((((((((a, b), c), d), e), f), g), h), i), j) => (a, b, c, d, e, f, g, h, i, j) })
      .toSeq
  }

  /** Capture all invocations of a mocked 10-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag](func: (A, B, C, D, E, F, G, H, I, J) => _): Seq[(A, B, C, D, E, F, G, H, I, J)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ)
  }

  /** Capture one invocation of a mocked 10-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag](func: (A, B, C, D, E, F, G, H, I, J) => _): (A, B, C, D, E, F, G, H, I, J) =
    capturingAll[A, B, C, D, E, F, G, H, I, J](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 11 iterables together into a Seq of 11-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K]): Seq[(A, B, C, D, E, F, G, H, I, J, K)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .map({ case ((((((((((a, b), c), d), e), f), g), h), i), j), k) => (a, b, c, d, e, f, g, h, i, j, k) })
      .toSeq
  }

  /** Capture all invocations of a mocked 11-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK)
  }

  /** Capture one invocation of a mocked 11-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K) => _): (A, B, C, D, E, F, G, H, I, J, K) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 12 iterables together into a Seq of 12-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .map({ case (((((((((((a, b), c), d), e), f), g), h), i), j), k), l) => (a, b, c, d, e, f, g, h, i, j, k, l) })
      .toSeq
  }

  /** Capture all invocations of a mocked 12-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL)
  }

  /** Capture one invocation of a mocked 12-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L) => _): (A, B, C, D, E, F, G, H, I, J, K, L) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 13 iterables together into a Seq of 13-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .map({ case ((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m) => (a, b, c, d, e, f, g, h, i, j, k, l, m) })
      .toSeq
  }

  /** Capture all invocations of a mocked 13-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM)
  }

  /** Capture one invocation of a mocked 13-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 14 iterables together into a Seq of 14-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .map({ case (((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n) })
      .toSeq
  }

  /** Capture all invocations of a mocked 14-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN)
  }

  /** Capture one invocation of a mocked 14-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 15 iterables together into a Seq of 15-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .map({ case ((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o) })
      .toSeq
  }

  /** Capture all invocations of a mocked 15-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO)
  }

  /** Capture one invocation of a mocked 15-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 16 iterables together into a Seq of 16-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .map({ case (((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) })
      .toSeq
  }

  /** Capture all invocations of a mocked 16-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP)
  }

  /** Capture one invocation of a mocked 16-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 17 iterables together into a Seq of 17-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .map({ case ((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q) })
      .toSeq
  }

  /** Capture all invocations of a mocked 17-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ)
  }

  /** Capture one invocation of a mocked 17-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 18 iterables together into a Seq of 18-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q], arg17: Iterable[R]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .zip(arg17)
      .map({ case (((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r) })
      .toSeq
  }

  /** Capture all invocations of a mocked 18-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    val argCaptorR = ArgumentCaptor.forClass(classTag[R].runtimeClass.asInstanceOf[Class[R]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture(), argCaptorR.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    val argsR = argCaptorR.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR)
  }

  /** Capture one invocation of a mocked 18-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 19 iterables together into a Seq of 19-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q], arg17: Iterable[R], arg18: Iterable[S]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .zip(arg17)
      .zip(arg18)
      .map({ case ((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s) })
      .toSeq
  }

  /** Capture all invocations of a mocked 19-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    val argCaptorR = ArgumentCaptor.forClass(classTag[R].runtimeClass.asInstanceOf[Class[R]])
    val argCaptorS = ArgumentCaptor.forClass(classTag[S].runtimeClass.asInstanceOf[Class[S]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture(), argCaptorR.capture(), argCaptorS.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    val argsR = argCaptorR.getAllValues.asScala
    val argsS = argCaptorS.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS)
  }

  /** Capture one invocation of a mocked 19-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 20 iterables together into a Seq of 20-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q], arg17: Iterable[R], arg18: Iterable[S], arg19: Iterable[T]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .zip(arg17)
      .zip(arg18)
      .zip(arg19)
      .map({ case (((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s), t) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t) })
      .toSeq
  }

  /** Capture all invocations of a mocked 20-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    val argCaptorR = ArgumentCaptor.forClass(classTag[R].runtimeClass.asInstanceOf[Class[R]])
    val argCaptorS = ArgumentCaptor.forClass(classTag[S].runtimeClass.asInstanceOf[Class[S]])
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture(), argCaptorR.capture(), argCaptorS.capture(), argCaptorT.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    val argsR = argCaptorR.getAllValues.asScala
    val argsS = argCaptorS.getAllValues.asScala
    val argsT = argCaptorT.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS, argsT)
  }

  /** Capture one invocation of a mocked 20-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 21 iterables together into a Seq of 21-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q], arg17: Iterable[R], arg18: Iterable[S], arg19: Iterable[T], arg20: Iterable[U]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .zip(arg17)
      .zip(arg18)
      .zip(arg19)
      .zip(arg20)
      .map({ case ((((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u) })
      .toSeq
  }

  /** Capture all invocations of a mocked 21-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag, U: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    val argCaptorR = ArgumentCaptor.forClass(classTag[R].runtimeClass.asInstanceOf[Class[R]])
    val argCaptorS = ArgumentCaptor.forClass(classTag[S].runtimeClass.asInstanceOf[Class[S]])
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture(), argCaptorR.capture(), argCaptorS.capture(), argCaptorT.capture(), argCaptorU.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    val argsR = argCaptorR.getAllValues.asScala
    val argsS = argCaptorS.getAllValues.asScala
    val argsT = argCaptorT.getAllValues.asScala
    val argsU = argCaptorU.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS, argsT, argsU)
  }

  /** Capture one invocation of a mocked 21-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag, U: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](func).lastOption.getOrElse(noArgWasCaptured())

  /** Zip 22 iterables together into a Seq of 22-tuples. */
  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](arg0: Iterable[A], arg1: Iterable[B], arg2: Iterable[C], arg3: Iterable[D], arg4: Iterable[E], arg5: Iterable[F], arg6: Iterable[G], arg7: Iterable[H], arg8: Iterable[I], arg9: Iterable[J], arg10: Iterable[K], arg11: Iterable[L], arg12: Iterable[M], arg13: Iterable[N], arg14: Iterable[O], arg15: Iterable[P], arg16: Iterable[Q], arg17: Iterable[R], arg18: Iterable[S], arg19: Iterable[T], arg20: Iterable[U], arg21: Iterable[V]): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] = {
    arg0
      .zip(arg1)
      .zip(arg2)
      .zip(arg3)
      .zip(arg4)
      .zip(arg5)
      .zip(arg6)
      .zip(arg7)
      .zip(arg8)
      .zip(arg9)
      .zip(arg10)
      .zip(arg11)
      .zip(arg12)
      .zip(arg13)
      .zip(arg14)
      .zip(arg15)
      .zip(arg16)
      .zip(arg17)
      .zip(arg18)
      .zip(arg19)
      .zip(arg20)
      .zip(arg21)
      .map({ case (((((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u), v) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u, v) })
      .toSeq
  }

  /** Capture all invocations of a mocked 22-ary method */
  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag, U: ClassTag, V: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => _): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    val argCaptorH = ArgumentCaptor.forClass(classTag[H].runtimeClass.asInstanceOf[Class[H]])
    val argCaptorI = ArgumentCaptor.forClass(classTag[I].runtimeClass.asInstanceOf[Class[I]])
    val argCaptorJ = ArgumentCaptor.forClass(classTag[J].runtimeClass.asInstanceOf[Class[J]])
    val argCaptorK = ArgumentCaptor.forClass(classTag[K].runtimeClass.asInstanceOf[Class[K]])
    val argCaptorL = ArgumentCaptor.forClass(classTag[L].runtimeClass.asInstanceOf[Class[L]])
    val argCaptorM = ArgumentCaptor.forClass(classTag[M].runtimeClass.asInstanceOf[Class[M]])
    val argCaptorN = ArgumentCaptor.forClass(classTag[N].runtimeClass.asInstanceOf[Class[N]])
    val argCaptorO = ArgumentCaptor.forClass(classTag[O].runtimeClass.asInstanceOf[Class[O]])
    val argCaptorP = ArgumentCaptor.forClass(classTag[P].runtimeClass.asInstanceOf[Class[P]])
    val argCaptorQ = ArgumentCaptor.forClass(classTag[Q].runtimeClass.asInstanceOf[Class[Q]])
    val argCaptorR = ArgumentCaptor.forClass(classTag[R].runtimeClass.asInstanceOf[Class[R]])
    val argCaptorS = ArgumentCaptor.forClass(classTag[S].runtimeClass.asInstanceOf[Class[S]])
    val argCaptorT = ArgumentCaptor.forClass(classTag[T].runtimeClass.asInstanceOf[Class[T]])
    val argCaptorU = ArgumentCaptor.forClass(classTag[U].runtimeClass.asInstanceOf[Class[U]])
    val argCaptorV = ArgumentCaptor.forClass(classTag[V].runtimeClass.asInstanceOf[Class[V]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture(), argCaptorH.capture(), argCaptorI.capture(), argCaptorJ.capture(), argCaptorK.capture(), argCaptorL.capture(), argCaptorM.capture(), argCaptorN.capture(), argCaptorO.capture(), argCaptorP.capture(), argCaptorQ.capture(), argCaptorR.capture(), argCaptorS.capture(), argCaptorT.capture(), argCaptorU.capture(), argCaptorV.capture())
    val argsA = argCaptorA.getAllValues.asScala
    val argsB = argCaptorB.getAllValues.asScala
    val argsC = argCaptorC.getAllValues.asScala
    val argsD = argCaptorD.getAllValues.asScala
    val argsE = argCaptorE.getAllValues.asScala
    val argsF = argCaptorF.getAllValues.asScala
    val argsG = argCaptorG.getAllValues.asScala
    val argsH = argCaptorH.getAllValues.asScala
    val argsI = argCaptorI.getAllValues.asScala
    val argsJ = argCaptorJ.getAllValues.asScala
    val argsK = argCaptorK.getAllValues.asScala
    val argsL = argCaptorL.getAllValues.asScala
    val argsM = argCaptorM.getAllValues.asScala
    val argsN = argCaptorN.getAllValues.asScala
    val argsO = argCaptorO.getAllValues.asScala
    val argsP = argCaptorP.getAllValues.asScala
    val argsQ = argCaptorQ.getAllValues.asScala
    val argsR = argCaptorR.getAllValues.asScala
    val argsS = argCaptorS.getAllValues.asScala
    val argsT = argCaptorT.getAllValues.asScala
    val argsU = argCaptorU.getAllValues.asScala
    val argsV = argCaptorV.getAllValues.asScala
    zipN(argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS, argsT, argsU, argsV)
  }

  /** Capture one invocation of a mocked 22-ary method */
  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag, U: ClassTag, V: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V](func).lastOption.getOrElse(noArgWasCaptured())

}

