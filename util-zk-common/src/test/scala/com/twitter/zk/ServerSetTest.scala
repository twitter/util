package com.twitter.zk

import com.twitter.common.zookeeper.{ServerSet => CommonServerSet}

/*class ServerSetTest extends WordSpec with Matchers with JMocker with ClassMocker {
  "ServerSet" should {
    val pool = FuturePool.immediatePool
    val port = RandomSocket.nextPort()
    val address = new InetSocketAddress("localhost", port)
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
      assert(endpointStatus == a[ServerSet.EndpointStatus])
      endpointStatus.update(ServerSet.Status.Dead).apply()
    }

    "monitor" in {
      val timeout = 2.seconds
      implicit val timer = new JavaTimer
      after { timer.stop() }

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
      assert(offer().apply(timeout) == instances(0))

      val fin = Future.collect {
        assert(instances.tail map { i => offer() onSuccess { _ == i } })
      }
      instances.tail zip(promises.tail) foreach { case (i, p) => p.setValue(i) }
      fin apply(timeout)
    }
  }
}
*/
