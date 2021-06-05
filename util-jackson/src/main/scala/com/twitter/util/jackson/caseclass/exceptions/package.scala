package com.twitter.util.jackson.caseclass

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException

package object exceptions {

  implicit class RichJsonProcessingException[E <: JsonProcessingException](val self: E)
      extends AnyVal {

    /**
     * Converts a [[JsonProcessingException]] to a informative error message that can be returned
     * to callers. The goal is to not leak internals of the underlying types.
     * @return a useful [[String]] error message
     */
    def errorMessage: String = {
      self match {
        case exc: JsonMappingException =>
          exc.getOriginalMessage // want to return the original message without and location reference
        case _ if self.getCause == null =>
          self.getOriginalMessage // jackson threw the original error
        case _ =>
          self.getCause.getMessage // custom deserialization code threw the exception (e.g., enum deserialization)
      }
    }
  }
}
