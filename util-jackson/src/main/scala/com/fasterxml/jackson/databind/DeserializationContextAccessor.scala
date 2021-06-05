package com.fasterxml.jackson.databind

/** INTERNAL API ONLY */
class DeserializationContextAccessor(context: DeserializationContext) {
  // Workaround to get access to the DeserializationContext#injectableValues member
  def getInjectableValues: Option[InjectableValues] = Option(context._injectableValues)
}
