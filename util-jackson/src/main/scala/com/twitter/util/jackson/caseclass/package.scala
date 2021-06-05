package com.twitter.util.jackson

import com.fasterxml.jackson.annotation.JacksonAnnotation
import com.fasterxml.jackson.databind.deser.impl.ValueInjector
import com.fasterxml.jackson.databind.introspect.{
  AnnotatedMember,
  AnnotatedParameter,
  AnnotatedWithParams,
  AnnotationMap,
  TypeResolutionContext
}
import com.fasterxml.jackson.databind.{BeanProperty, DeserializationContext, JavaType, PropertyName}
import java.lang.annotation.Annotation
import org.json4s.reflect.ClassDescriptor

package object caseclass {

  /** Resolves a subtype from a given optional class */
  private[twitter] def resolveSubType(
    context: DeserializationContext,
    baseType: JavaType,
    subClazz: Option[Class[_]]
  ): JavaType = {
    subClazz match {
      case Some(subTypeClazz) if isNotAssignableFrom(subTypeClazz, baseType.getRawClass) =>
        context.resolveSubType(baseType, subTypeClazz.getName)
      case _ =>
        baseType
    }
  }

  /** Returns true if the class is not null and not assignable to "other" */
  private[twitter] def isNotAssignableFrom(clazz: Class[_], other: Class[_]): Boolean = {
    clazz != null && !isAssignableFrom(other, clazz)
  }

  /** boxes both sides for comparison -- no way to turn the from into the to type */
  private[twitter] def isAssignableFrom(fromType: Class[_], toType: Class[_]): Boolean =
    Types.wrapperType(toType).isAssignableFrom(Types.wrapperType(fromType))

  /** Create a new [[CaseClassBeanProperty]]. Returns the [[CaseClassBeanProperty]] and the JacksonInject.Value */
  private[twitter] def newBeanProperty(
    context: DeserializationContext,
    javaType: JavaType,
    optionalJavaType: Option[JavaType],
    annotatedParameter: AnnotatedParameter,
    annotations: Iterable[Annotation],
    name: String,
    index: Int
  ): CaseClassBeanProperty = {
    // to support deserialization with JsonFormat and other Jackson annotations on fields,
    // the Jackson annotations must be carried in the mutator of the ValueInjector.
    val jacksonAnnotations = new AnnotationMap()
    val contextAnnotationsMap = new AnnotationMap()
    annotations.foreach { annotation =>
      if (annotation.annotationType().isAnnotationPresent(classOf[JacksonAnnotation])) {
        jacksonAnnotations.add(annotation)
      } else {
        contextAnnotationsMap.add(annotation)
      }
    }

    val mutator: AnnotatedMember = optionalJavaType match {
      case Some(optJavaType) =>
        newAnnotatedParameter(
          context,
          annotatedParameter,
          AnnotationMap.merge(jacksonAnnotations, contextAnnotationsMap),
          optJavaType,
          index)
      case _ =>
        newAnnotatedParameter(
          context,
          annotatedParameter,
          AnnotationMap.merge(jacksonAnnotations, contextAnnotationsMap),
          javaType,
          index)
    }

    val jacksonInjectValue = context.getAnnotationIntrospector.findInjectableValue(mutator)
    val beanProperty: BeanProperty = new ValueInjector(
      /* propName = */ new PropertyName(name),
      /* type =     */ optionalJavaType.getOrElse(javaType),
      /* mutator =  */ mutator,
      /* valueId =  */ if (jacksonInjectValue == null) null else jacksonInjectValue.getId
    ) {
      // ValueInjector no longer supports passing contextAnnotations as an
      // argument as of jackson 2.9.x
      // https://github.com/FasterXML/jackson-databind/commit/76381c528c9b75265e8b93bf6bb4532e4aa8e957#diff-dbcd29e987f27d95f964a674963a9066R24
      private[this] val contextAnnotations = contextAnnotationsMap

      override def getContextAnnotation[A <: Annotation](clazz: Class[A]): A = {
        contextAnnotations.get[A](clazz)
      }
    }

    CaseClassBeanProperty(valueObj = Option(jacksonInjectValue), property = beanProperty)
  }

  /** Create an [[AnnotatedMember]] for use in a [[CaseClassBeanProperty]] */
  private[jackson] def newAnnotatedParameter(
    context: DeserializationContext,
    base: AnnotatedParameter,
    annotations: AnnotationMap,
    javaType: JavaType,
    index: Int
  ): AnnotatedMember =
    newAnnotatedParameter(
      new TypeResolutionContext.Basic(context.getTypeFactory, javaType.getBindings),
      if (base == null) null else base.getOwner,
      annotations,
      javaType,
      index
    )

  /** Create an [[AnnotatedMember]] for use in a [[CaseClassBeanProperty]] */
  private[jackson] def newAnnotatedParameter(
    typeResolutionContext: TypeResolutionContext,
    owner: AnnotatedWithParams,
    annotations: AnnotationMap,
    javaType: JavaType,
    index: Int
  ): AnnotatedMember = {
    new AnnotatedParameter(
      /* owner = */ owner,
      /* type = */ javaType,
      /* typeResolutionContext = */ typeResolutionContext,
      /* annotations = */ annotations,
      /* index = */ index
    )
  }

  private[jackson] def defaultMethod(
    clazzDescriptor: ClassDescriptor,
    constructorParamIdx: Int
  ): Option[() => Object] = {
    val defaultMethodArgNum = constructorParamIdx + 1
    for {
      singletonDescriptor <- clazzDescriptor.companion
      companionObjectClass = singletonDescriptor.erasure.erasure
      companionObject = singletonDescriptor.instance
      method <- companionObjectClass.getMethods.find(
        _.getName == "$lessinit$greater$default$" + defaultMethodArgNum)
    } yield { () =>
      method.invoke(companionObject)
    }
  }
}
