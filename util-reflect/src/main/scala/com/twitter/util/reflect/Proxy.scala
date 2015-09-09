package com.twitter.util.reflect

import java.io.Serializable
import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor => CGMethodInterceptor, _}

import com.twitter.util.Future

class NonexistentTargetException extends Exception("MethodCall was invoked without a valid target.")

object Proxy {
  def apply[I <: AnyRef : Manifest](f: MethodCall[I] => AnyRef) = {
    new ProxyFactory[I](f).apply()
  }

  def apply[I <: AnyRef : Manifest](target: I, f: MethodCall[I] => AnyRef) = {
    new ProxyFactory[I](f).apply(target)
  }
}

object ProxyFactory {
  private[reflect] object NoOpInterceptor extends MethodInterceptor[AnyRef](None, m => null)

  private[reflect] object IgnoredMethodFilter extends CallbackFilter {
    def accept(m: Method) = {
      m.getName match {
        case "hashCode" => 1
        case "equals"   => 1
        case "toString" => 1
        case _          => 0
      }
    }
  }
}

class AbstractProxyFactory[I <: AnyRef : Manifest] {
  import ProxyFactory._

  final val interface = implicitly[Manifest[I]].runtimeClass

  protected final val proto = {
    val e = new Enhancer
    e.setCallbackFilter(IgnoredMethodFilter)
    e.setCallbacks(Array(NoOpInterceptor, NoOp.INSTANCE))
    e.setSuperclass(interface)
    e.create.asInstanceOf[Factory]
  }

  protected final def newWithCallback(f: MethodCall[I] => AnyRef) = {
    proto.newInstance(Array(new MethodInterceptor(None, f), NoOp.INSTANCE)).asInstanceOf[I]
  }

  protected final def newWithCallback[T <: I](target: T, f: MethodCall[I] => AnyRef) = {
    proto.newInstance(Array(new MethodInterceptor(Some(target), f), NoOp.INSTANCE)).asInstanceOf[I]
  }
}

class ProxyFactory[I <: AnyRef : Manifest](f: MethodCall[I] => AnyRef) extends AbstractProxyFactory[I] {
  def apply[T <: I](target: T) = newWithCallback(target, f)
  def apply()                  = newWithCallback(f)
}

private[reflect] class MethodInterceptor[I <: AnyRef](target: Option[I], callback: MethodCall[I] => AnyRef)
extends CGMethodInterceptor with Serializable {
  val targetRef = target.getOrElse(null).asInstanceOf[I]

  final def intercept(p: AnyRef, m: Method, args: Array[AnyRef], methodProxy: MethodProxy) = {
    callback(new MethodCall(targetRef, m, args, methodProxy))
  }
}

final class MethodCall[T <: AnyRef] private[reflect] (
  targetRef: T,
  val method: Method,
  val args: Array[AnyRef],
  methodProxy: MethodProxy)
extends (() => AnyRef) {

  lazy val target = if (targetRef ne null) Some(targetRef) else None

  def clazz          = method.getDeclaringClass
  def clazzName      = clazz.getName
  def className      = clazzName
  def parameterTypes = method.getParameterTypes
  def name           = method.getName
  def returnsUnit    = {
    val rt = method.getReturnType
    (rt eq classOf[Unit]) || (rt eq classOf[Null]) || (rt eq java.lang.Void.TYPE)
  }
  def returnsFuture  = classOf[Future[_]] isAssignableFrom method.getReturnType

  private def getTarget = if (targetRef ne null) targetRef else throw new NonexistentTargetException

  def apply() = methodProxy.invoke(getTarget, args)

  def apply(newTarget: T) = methodProxy.invoke(newTarget, args)
  def apply(newArgs: Array[AnyRef]) = methodProxy.invoke(getTarget, newArgs)
  def apply(newTarget: T, newArgs: Array[AnyRef]) = methodProxy.invoke(newTarget, newArgs)
}
