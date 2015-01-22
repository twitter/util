package com.twitter.util.testing

import java.util.{List => JList}
import org.mockito.ArgumentCaptor
import org.mockito.exceptions.Reporter
import scala.collection.JavaConverters._
import scala.reflect._

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

  def capturingAll[A: ClassTag, B: ClassTag](func: (A, B) => _): Seq[(A, B)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    func(argCaptorA.capture(), argCaptorB.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    zipN((argsA, argsB))
  }

  def capturingOne[A: ClassTag, B: ClassTag](func: (A, B) => _): (A, B) =
    capturingAll[A, B](func).lastOption.getOrElse(noArgWasCaptured())

  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag](func: (A, B, C) => _): Seq[(A, B, C)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    zipN((argsA, argsB, argsC))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag](func: (A, B, C) => _): (A, B, C) =
    capturingAll[A, B, C](func).lastOption.getOrElse(noArgWasCaptured())

  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag](func: (A, B, C, D) => _): Seq[(A, B, C, D)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    zipN((argsA, argsB, argsC, argsD))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag](func: (A, B, C, D) => _): (A, B, C, D) =
    capturingAll[A, B, C, D](func).lastOption.getOrElse(noArgWasCaptured())

  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag](func: (A, B, C, D, E) => _): Seq[(A, B, C, D, E)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag](func: (A, B, C, D, E) => _): (A, B, C, D, E) =
    capturingAll[A, B, C, D, E](func).lastOption.getOrElse(noArgWasCaptured())

  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag](func: (A, B, C, D, E, F) => _): Seq[(A, B, C, D, E, F)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag](func: (A, B, C, D, E, F) => _): (A, B, C, D, E, F) =
    capturingAll[A, B, C, D, E, F](func).lastOption.getOrElse(noArgWasCaptured())

  def capturingAll[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag](func: (A, B, C, D, E, F, G) => _): Seq[(A, B, C, D, E, F, G)] = {
    val argCaptorA = ArgumentCaptor.forClass(classTag[A].runtimeClass.asInstanceOf[Class[A]])
    val argCaptorB = ArgumentCaptor.forClass(classTag[B].runtimeClass.asInstanceOf[Class[B]])
    val argCaptorC = ArgumentCaptor.forClass(classTag[C].runtimeClass.asInstanceOf[Class[C]])
    val argCaptorD = ArgumentCaptor.forClass(classTag[D].runtimeClass.asInstanceOf[Class[D]])
    val argCaptorE = ArgumentCaptor.forClass(classTag[E].runtimeClass.asInstanceOf[Class[E]])
    val argCaptorF = ArgumentCaptor.forClass(classTag[F].runtimeClass.asInstanceOf[Class[F]])
    val argCaptorG = ArgumentCaptor.forClass(classTag[G].runtimeClass.asInstanceOf[Class[G]])
    func(argCaptorA.capture(), argCaptorB.capture(), argCaptorC.capture(), argCaptorD.capture(), argCaptorE.capture(), argCaptorF.capture(), argCaptorG.capture())
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag](func: (A, B, C, D, E, F, G) => _): (A, B, C, D, E, F, G) =
    capturingAll[A, B, C, D, E, F, G](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag](func: (A, B, C, D, E, F, G, H) => _): (A, B, C, D, E, F, G, H) =
    capturingAll[A, B, C, D, E, F, G, H](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag](func: (A, B, C, D, E, F, G, H, I) => _): (A, B, C, D, E, F, G, H, I) =
    capturingAll[A, B, C, D, E, F, G, H, I](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag](func: (A, B, C, D, E, F, G, H, I, J) => _): (A, B, C, D, E, F, G, H, I, J) =
    capturingAll[A, B, C, D, E, F, G, H, I, J](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K) => _): (A, B, C, D, E, F, G, H, I, J, K) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L) => _): (A, B, C, D, E, F, G, H, I, J, K, L) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    val argsQ = argCaptorQ.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    val argsQ = argCaptorQ.getAllValues
    val argsR = argCaptorR.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    val argsQ = argCaptorQ.getAllValues
    val argsR = argCaptorR.getAllValues
    val argsS = argCaptorS.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    val argsQ = argCaptorQ.getAllValues
    val argsR = argCaptorR.getAllValues
    val argsS = argCaptorS.getAllValues
    val argsT = argCaptorT.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS, argsT))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](func).lastOption.getOrElse(noArgWasCaptured())

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
    val argsA = argCaptorA.getAllValues
    val argsB = argCaptorB.getAllValues
    val argsC = argCaptorC.getAllValues
    val argsD = argCaptorD.getAllValues
    val argsE = argCaptorE.getAllValues
    val argsF = argCaptorF.getAllValues
    val argsG = argCaptorG.getAllValues
    val argsH = argCaptorH.getAllValues
    val argsI = argCaptorI.getAllValues
    val argsJ = argCaptorJ.getAllValues
    val argsK = argCaptorK.getAllValues
    val argsL = argCaptorL.getAllValues
    val argsM = argCaptorM.getAllValues
    val argsN = argCaptorN.getAllValues
    val argsO = argCaptorO.getAllValues
    val argsP = argCaptorP.getAllValues
    val argsQ = argCaptorQ.getAllValues
    val argsR = argCaptorR.getAllValues
    val argsS = argCaptorS.getAllValues
    val argsT = argCaptorT.getAllValues
    val argsU = argCaptorU.getAllValues
    zipN((argsA, argsB, argsC, argsD, argsE, argsF, argsG, argsH, argsI, argsJ, argsK, argsL, argsM, argsN, argsO, argsP, argsQ, argsR, argsS, argsT, argsU))
  }

  def capturingOne[A: ClassTag, B: ClassTag, C: ClassTag, D: ClassTag, E: ClassTag, F: ClassTag, G: ClassTag, H: ClassTag, I: ClassTag, J: ClassTag, K: ClassTag, L: ClassTag, M: ClassTag, N: ClassTag, O: ClassTag, P: ClassTag, Q: ClassTag, R: ClassTag, S: ClassTag, T: ClassTag, U: ClassTag](func: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) => _): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) =
    capturingAll[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](func).lastOption.getOrElse(noArgWasCaptured())

  private[this] def zipN[A, B](args: (JList[A], JList[B])): Seq[(A, B)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .map({ case (a, b) => (a, b) })
        .toSeq
  }

  private[this] def zipN[A, B, C](args: (JList[A], JList[B], JList[C])): Seq[(A, B, C)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .map({ case ((a, b), c) => (a, b, c) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D](args: (JList[A], JList[B], JList[C], JList[D])): Seq[(A, B, C, D)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .map({ case (((a, b), c), d) => (a, b, c, d) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E](args: (JList[A], JList[B], JList[C], JList[D], JList[E])): Seq[(A, B, C, D, E)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .map({ case ((((a, b), c), d), e) => (a, b, c, d, e) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F])): Seq[(A, B, C, D, E, F)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .map({ case (((((a, b), c), d), e), f) => (a, b, c, d, e, f) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G])): Seq[(A, B, C, D, E, F, G)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .map({ case ((((((a, b), c), d), e), f), g) => (a, b, c, d, e, f, g) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H])): Seq[(A, B, C, D, E, F, G, H)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .map({ case (((((((a, b), c), d), e), f), g), h) => (a, b, c, d, e, f, g, h) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I])): Seq[(A, B, C, D, E, F, G, H, I)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .map({ case ((((((((a, b), c), d), e), f), g), h), i) => (a, b, c, d, e, f, g, h, i) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J])): Seq[(A, B, C, D, E, F, G, H, I, J)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .map({ case (((((((((a, b), c), d), e), f), g), h), i), j) => (a, b, c, d, e, f, g, h, i, j) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K])): Seq[(A, B, C, D, E, F, G, H, I, J, K)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .map({ case ((((((((((a, b), c), d), e), f), g), h), i), j), k) => (a, b, c, d, e, f, g, h, i, j, k) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .map({ case (((((((((((a, b), c), d), e), f), g), h), i), j), k), l) => (a, b, c, d, e, f, g, h, i, j, k, l) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .map({ case ((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m) => (a, b, c, d, e, f, g, h, i, j, k, l, m) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .map({ case (((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .map({ case ((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .map({ case (((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P], JList[Q])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .zip(args._17.asScala)
        .map({ case ((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P], JList[Q], JList[R])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .zip(args._17.asScala)
        .zip(args._18.asScala)
        .map({ case (((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P], JList[Q], JList[R], JList[S])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .zip(args._17.asScala)
        .zip(args._18.asScala)
        .zip(args._19.asScala)
        .map({ case ((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P], JList[Q], JList[R], JList[S], JList[T])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .zip(args._17.asScala)
        .zip(args._18.asScala)
        .zip(args._19.asScala)
        .zip(args._20.asScala)
        .map({ case (((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s), t) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t) })
        .toSeq
  }

  private[this] def zipN[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U](args: (JList[A], JList[B], JList[C], JList[D], JList[E], JList[F], JList[G], JList[H], JList[I], JList[J], JList[K], JList[L], JList[M], JList[N], JList[O], JList[P], JList[Q], JList[R], JList[S], JList[T], JList[U])): Seq[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] = {
    args._1.asScala
        .zip(args._2.asScala)
        .zip(args._3.asScala)
        .zip(args._4.asScala)
        .zip(args._5.asScala)
        .zip(args._6.asScala)
        .zip(args._7.asScala)
        .zip(args._8.asScala)
        .zip(args._9.asScala)
        .zip(args._10.asScala)
        .zip(args._11.asScala)
        .zip(args._12.asScala)
        .zip(args._13.asScala)
        .zip(args._14.asScala)
        .zip(args._15.asScala)
        .zip(args._16.asScala)
        .zip(args._17.asScala)
        .zip(args._18.asScala)
        .zip(args._19.asScala)
        .zip(args._20.asScala)
        .zip(args._21.asScala)
        .map({ case ((((((((((((((((((((a, b), c), d), e), f), g), h), i), j), k), l), m), n), o), p), q), r), s), t), u) => (a, b, c, d, e, f, g, h, i, j, k, l, m, n, o, p, q, r, s, t, u) })
        .toSeq
  }
}
