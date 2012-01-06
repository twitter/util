package com.twitter.zk

import com.google.common.collect.ImmutableSet
import com.twitter.conversions.time._
import com.twitter.common.zookeeper.{ServerSet => CommonServerSet, ServerSetImpl}
import com.twitter.thrift.{Endpoint, ServiceInstance, Status}
import com.twitter.util.{Future, FuturePool, JavaTimer, Promise}
import java.net.InetSocketAddress
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import scala.collection.JavaConverters._
import scala.collection.{Map, Set}

object ServerSetSpec extends Specification with JMocker with ClassMocker {
  "ServerSet" should {
    val pool = FuturePool.immediatePool
    val address = new InetSocketAddress("localhost", 34245)
    val commonServerSet = mock[ServerSetImpl]
    val serverSet = ServerSet(commonServerSet, "/services", pool)

    "join" in {
      val commonEndpointStatus = mock[CommonServerSet.EndpointStatus]
      expect {
        one(commonServerSet).join(a[InetSocketAddress],
            equal(Map.empty[String, InetSocketAddress].asJava),
            equal(Status.ALIVE)) willReturn commonEndpointStatus
        one(commonEndpointStatus).update(equal(Status.DEAD))
      }
      val endpointStatus = serverSet.join(address).apply()
      endpointStatus must be[ServerSet.EndpointStatus]
      endpointStatus.update(ServerSet.Status.Dead).apply()
    }

    "monitor" in {
      val timeout = 2.seconds
      implicit val timer = new JavaTimer
      doAfter { timer.stop() }

      val basePort = 20000
      val instances = 1 to 5 map { i =>
        0 until i map { p =>
          new Endpoint("localhost", basePort + p)
        } map { endpoint =>
          new ServiceInstance(endpoint, Map.empty.asJava, Status.ALIVE)
        } toSet
      }
      val promises = instances map { _ => new Promise[Set[ServiceInstance]] }
      expect {
        val cb = capturingParam[ServerSet.InstanceBroker]
        one(commonServerSet).monitor(cb.capture(0)) willReturn cb.map { monitor =>
          // configure promises to be passed on to the callback
          promises.map { promise =>
            promise map { instances =>
              new ImmutableSet.Builder[ServiceInstance].addAll(instances.asJava).build()
            } onSuccess(monitor.onChange)
          }.head.apply()  // block until the first completes
          null
        }
      }

      promises(0).setValue(instances(0))
      val offer = serverSet.monitor().apply(timeout)
      offer().apply(timeout) mustEqual instances(0)

      val fin = Future.collect {
        instances.tail map { i => offer() onSuccess { _ mustEqual i } }
      }
      instances.tail zip(promises.tail) foreach { case (i, p) => p.setValue(i) }
      fin apply(timeout)
    }
  }
}
