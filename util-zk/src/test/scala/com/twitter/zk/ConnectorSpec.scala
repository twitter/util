package com.twitter.zk


import org.scalatest.{WordSpec, Matchers}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import com.twitter.util.Future

class ConnectorSpec extends WordSpec with Matchers with MockitoSugar {
  "Connector.RoundRobin" should  {
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
        val connectors = 1 to nConnectors map { _ => mockConnector }
        val connector = Connector.RoundRobin(connectors: _*)
      }

      "apply" in {
        val h = new ConnectorSpecHelper
        import h._

        connectors foreach {
          _ apply() shouldBe Future.never
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

        connectors foreach {
          _ release() shouldBe Future.never
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
