package com.twitter.util.validation.conversions

import jakarta.validation.Path
import org.hibernate.validator.internal.engine.path.PathImpl

object PathOps {

  implicit class RichPath(val self: Path) extends AnyVal {
    def getLeafNode: Path.Node = self match {
      case pathImpl: PathImpl =>
        pathImpl.getLeafNode
      case _ =>
        var node = self.iterator().next()
        while (self.iterator().hasNext) {
          node = self.iterator().next()
        }
        node
    }
  }
}
