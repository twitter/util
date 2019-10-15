package com.twitter.util.reflect

import java.io.Serializable
import java.lang.reflect.Method

import net.sf.cglib.proxy.{MethodInterceptor => CGMethodInterceptor, _}

import com.twitter.util.Future

class NonexistentTargetException extends Exception("MethodCall was invoked without a valid target.")

object Proxy {
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply[I <: AnyRef: Manifest](f: MethodCall[I] => AnyRef): I = {
    new ProxyFactory[I](f).apply()
  }

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply[I <: AnyRef: Manifest](target: I, f: MethodCall[I] => AnyRef): I = {
    new ProxyFactory[I](f).apply(target)
  }
}

object ProxyFactory {
  private[reflect] object NoOpInterceptor extends MethodInterceptor[AnyRef](None, m => null)

  private[reflect] object IgnoredMethodFilter extends CallbackFilter {
    def accept(m: Method): Int = {
      m.getName match {
        case "hashCode" => 1
        case "equals" => 1
        case "toString" => 1
        case _ => 0
      }
    }
  }
}

class AbstractProxyFactory[I <: AnyRef: Manifest] {
  import ProxyFactory._

  final val interface: Class[_] = implicitly[Manifest[I]].runtimeClass

  protected final val proto: Factory = {
    val e = new Enhancer
    e.setCallbackFilter(IgnoredMethodFilter)
    e.setCallbacks(Array(NoOpInterceptor, NoOp.INSTANCE))
    e.setSuperclass(interface)
    e.create.asInstanceOf[Factory]
  }

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  protected final def newWithCallback(f: MethodCall[I] => AnyRef): I = {
    proto.newInstance(Array(new MethodInterceptor(None, f), NoOp.INSTANCE)).asInstanceOf[I]
  }

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  protected final def newWithCallback[T <: I](target: T, f: MethodCall[I] => AnyRef): I = {
    proto.newInstance(Array(new MethodInterceptor(Some(target), f), NoOp.INSTANCE)).asInstanceOf[I]
  }
}

class ProxyFactory[I <: AnyRef: Manifest](f: MethodCall[I] => AnyRef)
    extends AbstractProxyFactory[I] {
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply[T <: I](target: T): I = newWithCallback(target, f)
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply(): I = newWithCallback(f)
}

private[reflect] class MethodInterceptor[I <: AnyRef](
  target: Option[I],
  callback: MethodCall[I] => AnyRef)
    extends CGMethodInterceptor
    with Serializable {
  val targetRef: I = target.getOrElse(null).asInstanceOf[I]

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  final def intercept(
    p: AnyRef,
    m: Method,
    args: Array[AnyRef],
    methodProxy: MethodProxy
  ): Object = {
    callback(new MethodCall(targetRef, m, args, methodProxy))
  }
}

final class MethodCall[T <: AnyRef] private[reflect] (
  targetRef: T,
  val method: Method,
  val args: Array[AnyRef],
  methodProxy: MethodProxy)
    extends (() => AnyRef) {

  lazy val target: Option[T] = if (targetRef ne null) Some(targetRef) else None

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def clazz: Class[_] = method.getDeclaringClass
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def clazzName: String = clazz.getName
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def className: String = clazzName
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def parameterTypes: Array[Class[_]] = method.getParameterTypes
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def name: String = method.getName
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def returnsUnit: Boolean = {
    val rt = method.getReturnType
    (rt eq classOf[Unit]) || (rt eq classOf[Null]) || (rt eq java.lang.Void.TYPE)
  }
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def returnsFuture: Boolean = classOf[Future[_]] isAssignableFrom method.getReturnType

  private def getTarget = if (targetRef ne null) targetRef else throw new NonexistentTargetException

  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply(): AnyRef = methodProxy.invoke(getTarget, args)
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply(newTarget: T): Object = methodProxy.invoke(newTarget, args)
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply(newArgs: Array[AnyRef]): Object = methodProxy.invoke(getTarget, newArgs)
  @deprecated("Legacy code that shouldn't be used for new services", "2018-05-25")
  def apply(newTarget: T, newArgs: Array[AnyRef]): Object = methodProxy.invoke(newTarget, newArgs)
}
