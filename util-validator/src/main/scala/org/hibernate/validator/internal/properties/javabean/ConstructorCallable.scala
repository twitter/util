package org.hibernate.validator.internal.properties.javabean

import com.twitter.util.reflect.Classes
import java.lang.reflect.{Constructor, Method, TypeVariable}
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement.ConstrainedElementKind

class ConstructorCallable(constructor: Constructor[_])
    extends ExecutableCallable[Constructor[_]](constructor, true) {

  override def getName: String = Classes.simpleName(getDeclaringClass)

  def getConstrainedElementKind: ConstrainedElementKind = ConstrainedElementKind.CONSTRUCTOR

  def getTypeParameters: Array[TypeVariable[Class[_]]] =
    executable
      .asInstanceOf[Method]
      .getReturnType
      .getTypeParameters
      .asInstanceOf[Array[TypeVariable[Class[_]]]]
}
