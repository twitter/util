package com.twitter.util.validation.internal.metadata.descriptor

import jakarta.validation.groups.Default
import java.lang.annotation.Annotation
import java.lang.reflect.Type
import org.hibernate.validator.internal.engine.ValidatorFactoryInspector
import org.hibernate.validator.internal.metadata.core.ConstraintOrigin
import org.hibernate.validator.internal.metadata.descriptor.{
  ConstraintDescriptorImpl => HibernateConstraintDescriptorImpl
}
import org.hibernate.validator.internal.metadata.descriptor.ConstraintDescriptorImpl.ConstraintType
import org.hibernate.validator.internal.metadata.location.ConstraintLocation.ConstraintLocationKind
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement
import org.hibernate.validator.internal.metadata.raw.ConstrainedElement.ConstrainedElementKind
import org.hibernate.validator.internal.properties.Constrainable
import org.hibernate.validator.internal.util.annotation.ConstraintAnnotationDescriptor

private[validation] class ConstraintDescriptorFactory(
  validatorFactory: ValidatorFactoryInspector) {

  def newConstraintDescriptor(
    name: String,
    clazz: Class[_],
    declaringClazz: Class[_],
    annotation: Annotation,
    constrainedElementKind: ConstrainedElementKind = ConstrainedElementKind.FIELD
  ) = new HibernateConstraintDescriptorImpl(
    validatorFactory.constraintHelper,
    mkConstrainable(name, clazz, declaringClazz, constrainedElementKind),
    new ConstraintAnnotationDescriptor.Builder(annotation).build(),
    ConstraintLocationKind.FIELD,
    classOf[Default],
    ConstraintOrigin.DEFINED_LOCALLY,
    ConstraintType.GENERIC
  )

  /* Private */

  private[this] def mkConstrainable(
    name: String,
    clazz: Class[_],
    declaringClazz: Class[_],
    constrainedElementKind: ConstrainedElementKind = ConstrainedElementKind.FIELD
  ): Constrainable = new Constrainable {
    override def getName: String = name
    override def getDeclaringClass: Class[_] = declaringClazz
    override def getTypeForValidatorResolution: Type = clazz
    override def getType: Type = clazz
    override def getConstrainedElementKind: ConstrainedElement.ConstrainedElementKind =
      constrainedElementKind
  }
}
