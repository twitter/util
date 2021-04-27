package org.hibernate.validator.internal.properties.javabean

import com.twitter.util.reflect.Classes
import jakarta.validation.ValidationException
import java.lang.reflect.{Constructor, Executable, Method, Modifier, Parameter, Type, TypeVariable}
import org.hibernate.validator.internal.properties.{Callable, Signature}
import org.hibernate.validator.internal.util.{
  ExecutableHelper,
  ExecutableParameterNameProvider,
  ReflectionHelper,
  TypeHelper
}
import scala.collection.mutable

private object ExecutableCallable {

  def getParameters(executable: Executable): Seq[JavaBeanParameter] = {
    if (executable.getParameterCount == 0) Seq.empty[JavaBeanParameter]
    else {
      val parameters = new mutable.ArrayBuffer[JavaBeanParameter](executable.getParameterCount)

      val parameterArray = executable.getParameters
      val parameterTypes = executable.getParameterTypes
      val genericParameterTypes = executable.getGenericParameterTypes
      if (parameterTypes.length == genericParameterTypes.length) {
        var index = 0;
        val length = parameterArray.length
        while (index < length) {
          parameters.append(
            new JavaBeanParameter(
              index,
              parameterArray(index),
              parameterTypes(index),
              getErasedTypeIfTypeVariable(genericParameterTypes(index))))

          index += 1
        }
      } else {
        // in this case, we have synthetic or implicit parameters
        val hasParameterModifierInfo = isAnyParameterCarryingMetadata(parameterArray)
        if (!hasParameterModifierInfo)
          throw new ValidationException("")

        var explicitlyDeclaredParameterIndex = 0
        var index = 0;
        val length = parameterArray.length
        while (index < length) {
          if (explicitlyDeclaredParameterIndex < genericParameterTypes.length
            && isExplicit(parameterArray(index))
            && parameterTypesMatch(
              parameterTypes(index),
              genericParameterTypes(explicitlyDeclaredParameterIndex))) {
            // in this case we have a parameter that is present and matches ("most likely") to the one in the generic parameter types list
            parameters.append(
              new JavaBeanParameter(
                index,
                parameterArray(index),
                parameterTypes(index),
                getErasedTypeIfTypeVariable(genericParameterTypes(explicitlyDeclaredParameterIndex))
              )
            )
            explicitlyDeclaredParameterIndex += 1
          } else {
            // in this case, the parameter is not present in genericParameterTypes, or the types doesn't match
            parameters.append(
              new JavaBeanParameter(
                index,
                parameterArray(index),
                parameterTypes(index),
                parameterTypes(index)))
          }

          index += 1
        }
      }

      parameters.toSeq
    }
  }

  private[this] def getErasedTypeIfTypeVariable(genericType: Type): Type = genericType match {
    case _: TypeVariable[_] =>
      TypeHelper.getErasedType(genericType)
    case _ =>
      genericType
  }

  private[this] def isAnyParameterCarryingMetadata(parameterArray: Array[Parameter]): Boolean = {
    parameterArray.exists(p => p.isSynthetic || p.isImplicit)
  }

  private[this] def parameterTypesMatch(paramType: Class[_], genericParamType: Type): Boolean = {
    TypeHelper.getErasedType(genericParamType).equals(paramType)
  }

  private[this] def isExplicit(parameter: Parameter): Boolean = {
    !parameter.isSynthetic && !parameter.isImplicit
  }
}

abstract class ExecutableCallable[T <: Executable](
  val executable: T,
  hasReturnValue: Boolean)
    extends Callable {

  private val `type`: Type = ReflectionHelper.typeOf(executable)
  private val parameters: Seq[JavaBeanParameter] = ExecutableCallable.getParameters(executable)
  private val typeForValidatorResolution: Type = ReflectionHelper.boxedType(this.`type`)

  def getParameters: Seq[JavaBeanParameter] = this.parameters
  override def getName: String = executable.getName
  override def getDeclaringClass: Class[_] = executable.getDeclaringClass
  override def getTypeForValidatorResolution: Type = this.typeForValidatorResolution
  override def getType: Type = this.`type`
  override def hasReturnValue: Boolean = hasReturnValue
  override def hasParameters: Boolean = this.parameters.nonEmpty
  override def getParameterCount: Int = this.parameters.size
  override def getParameterGenericType(index: Int): Type = this.parameters(index).getGenericType
  override def getParameterTypes: Array[Class[_]] = executable.getParameterTypes
  override def getParameterName(
    parameterNameProvider: ExecutableParameterNameProvider,
    parameterIndex: Int
  ): String = parameterNameProvider.getParameterNames(executable).get(parameterIndex)

  override def isPrivate: Boolean = Modifier.isPrivate(executable.getModifiers)
  override def getSignature: Signature = {
    val simpleName = executable match {
      case c: Constructor[_] =>
        Classes.simpleName(c.getDeclaringClass)
      case _ => executable.getName
    }
    ExecutableHelper.getSignature(simpleName, executable.getParameterTypes)
  }

  override def overrides(
    executableHelper: ExecutableHelper,
    superTypeMethod: Callable
  ): Boolean = executableHelper.overrides(
    executable.asInstanceOf[Method],
    superTypeMethod.asInstanceOf[ExecutableCallable[T]].executable.asInstanceOf[Method]
  )

  override def isResolvedToSameMethodInHierarchy(
    executableHelper: ExecutableHelper,
    mainSubType: Class[_],
    superTypeMethod: Callable
  ): Boolean =
    executableHelper
      .isResolvedToSameMethodInHierarchy(
        mainSubType,
        executable.asInstanceOf[Method],
        superTypeMethod.asInstanceOf[ExecutableCallable[T]].executable.asInstanceOf[Method]
      )

  override def equals(o: Any): Boolean = {
    if (this == o) {
      true
    } else if (o != null && this.getClass == o.getClass) {
      val that: ExecutableCallable[T] = o.asInstanceOf[ExecutableCallable[T]]
      if (this.hasReturnValue != that.hasReturnValue) {
        false
      } else if (!this.executable.equals(that.executable)) {
        false
      } else {
        if (this.typeForValidatorResolution != that.typeForValidatorResolution) false
        else this.`type` == that.`type`
      }
    } else {
      false
    }
  }

  override def hashCode: Int = {
    var result = executable.hashCode
    result = 31 * result + this.typeForValidatorResolution.hashCode
    result = 31 * result + (if (this.hasReturnValue) 1 else 0)
    result = 31 * result + this.`type`.hashCode
    result
  }

  override def toString: String =
    ExecutableHelper.getExecutableAsString(
      this.getDeclaringClass.getSimpleName + "#" + executable.getName,
      executable.getParameterTypes: _*
    )
}
