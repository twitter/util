package org.hibernate.validator.internal.properties.javabean

import java.lang.reflect.{Method, TypeVariable}
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement.ConstrainedElementKind

class MethodCallable(method: Method)
    extends ExecutableCallable[Method](method, method.getGenericReturnType != classOf[Unit]) {

  def getConstrainedElementKind: ConstrainedElementKind = ConstrainedElementKind.METHOD

  def getTypeParameters: Array[TypeVariable[Class[_]]] =
    method.getReturnType.getTypeParameters.asInstanceOf[Array[TypeVariable[Class[_]]]]
}
