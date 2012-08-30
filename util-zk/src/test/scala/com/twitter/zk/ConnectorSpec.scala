package com.twitter.zk

import org.specs.SpecificationWithJUnit
import org.specs.mock.Mockito
import com.twitter.util.Future

class ConnectorSpec extends SpecificationWithJUnit with Mockito {
  "Connector.RoundRobin" should {
    "require underlying connections" in {
      Connector.RoundRobin() must throwAn[Exception]
    }

    "dispatch requests across underlying connectors" in {
      val nConnectors = 3
      val connectors = 1 to nConnectors map { _ => mock[Connector] }
      val connector = Connector.RoundRobin(connectors: _*)

      "apply" in {
        connectors foreach {
          _ apply() returns Future.never
        }
        (1 to 2 * nConnectors) foreach { _ =>
          connector()
        }
        connectors foreach { c =>
          there were two(c).apply()
        }
      }

      "release" in {
        connectors foreach {
          _ release() returns Future.never
        }
        (1 to 2) foreach { _ =>
          connector.release()
        }
        connectors foreach { c =>
          there were two(c).release()
        }
      }
    }
  }
}
