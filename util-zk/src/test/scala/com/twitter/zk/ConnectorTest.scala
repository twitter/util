package com.twitter.zk

import org.junit.runner.RunWith
import org.mockito.Mockito._
import org.scalatest.WordSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.mockito.MockitoSugar

import com.twitter.util.Future

@RunWith(classOf[JUnitRunner])
class ConnectorTest extends WordSpec with MockitoSugar {
  "Connector.RoundRobin" should {
    "require underlying connections" in {
      intercept[Exception] {
        Connector.RoundRobin()
      }
    }

    "dispatch requests across underlying connectors" should {
      class ConnectorSpecHelper {
        def mockConnector = {
          val connector = mock[Connector]
          when(connector.apply()).thenReturn(Future.never)
          when(connector.release()).thenReturn(Future.never)
          connector
        }
        val nConnectors = 3
        val connectors = 1 to nConnectors map { _ =>
          mockConnector
        }
        val connector = Connector.RoundRobin(connectors: _*)
      }

      "apply" in {
        val h = new ConnectorSpecHelper
        import h._

        connectors foreach { x =>
          assert(x.apply() == Future.never)
        }
        (1 to 2 * nConnectors) foreach { _ =>
          connector()
        }
        connectors foreach { c =>
          verify(c, times(3)).apply()
        }
      }

      "release" in {
        val h = new ConnectorSpecHelper
        import h._

        connectors foreach { x =>
          assert(x.release() == Future.never)
        }
        (1 to 2) foreach { _ =>
          connector.release()
        }
        connectors foreach { c =>
          verify(c, times(3)).release()
        }
      }
    }
  }
}
