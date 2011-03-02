package com.twitter.util.reflect

import java.lang.reflect.Method
import java.io.Serializable
import net.sf.cglib.proxy._
import net.sf.cglib.proxy.{MethodInterceptor => CGMethodInterceptor}
import com.twitter.util.Future


object Proxy {
  def apply[I <: AnyRef : Manifest](f: MethodCall[I] => AnyRef) = {
    new ProxyFactory[I](f).apply()
  }

  def apply[I <: AnyRef : Manifest](target: I, f: MethodCall[I] => AnyRef) = {
    new ProxyFactory[I](f).apply(target)
  }
}

object ProxyFactory {
  private[reflect] object NoOpInterceptor extends MethodInterceptor[AnyRef](None, _())

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

  final val interface = implicitly[Manifest[I]].erasure

  protected final lazy val proto = {
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
  final def intercept(p: AnyRef, m: Method, args: Array[AnyRef], methodProxy: MethodProxy) = {
    callback(new MethodCall(target, m, args, methodProxy))
  }
}

class MethodCall[T <: AnyRef](
  var target: Option[T],
  val method: Method,
  var args: Array[AnyRef],
  methodProxy: MethodProxy)
extends (() => AnyRef) {

  def clazz          = method.getDeclaringClass
  def clazzName      = clazz.getName
  def className      = clazzName
  def parameterTypes = method.getParameterTypes
  def name           = method.getName
  def returnsUnit    = method.getReturnType eq classOf[Unit]
  def returnsFuture  = method.getReturnType eq classOf[Future[_]]

  def apply() = methodProxy.invoke(target.get, args)
}
