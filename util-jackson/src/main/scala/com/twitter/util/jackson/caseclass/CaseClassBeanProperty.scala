package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.databind.BeanProperty

private[twitter] case class CaseClassBeanProperty(
  valueObj: Option[JacksonInject.Value],
  property: BeanProperty)
