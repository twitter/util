package com.twitter.zk

import com.google.common.collect.ImmutableSet
import com.twitter.common.net.pool.DynamicHostSet.HostChangeMonitor
import com.twitter.common.zookeeper.{ServerSet => CommonServerSet, ServerSetImpl, ZooKeeperClient}
import com.twitter.concurrent.{Broker, Offer}
import com.twitter.thrift.{Status => CommonStatus, ServiceInstance}
import com.twitter.util.{Future, FuturePool}
import java.net.InetSocketAddress
import scala.collection.{Map, Set}
import scala.collection.JavaConverters._

/**
 * Wraps a common.zookeeper.ServerSetImpl to be asynchronous using a FuturePool.
 */
class ServerSet(
    val underlying: ServerSetImpl,
    val path: String,
    pool: FuturePool) {
  import ServerSet._

  /** Join a ServerSet */
  def join(serviceEndpoint: InetSocketAddress,
           additionalEndpoints: Map[String, InetSocketAddress] = Map.empty,
           status: CommonStatus = CommonStatus.ALIVE): Future[EndpointStatus] = pool {
    underlying.join(serviceEndpoint, additionalEndpoints.asJava, status)  // blocks
  } map { new EndpointStatus(_, pool) }  // wrap for async updates

  /**
   * Monitor the ServerSet
   *
   * An Offer is returned asynchronously if the serverset can be monitored.
   */
  def monitor(): Future[Offer[Set[ServiceInstance]]] = pool {
    val broker = new InstanceBroker
    underlying.monitor(broker)  // blocks until monitor is initialized, or throws an exception
    broker.recv
  }
}

object ServerSet {
  def apply(underlying: ServerSetImpl, path: String, pool: FuturePool): ServerSet = {
    new ServerSet(underlying, path, pool)
  }

  def apply(client: ZooKeeperClient, path: String, pool: FuturePool): ServerSet = {
    apply(new ServerSetImpl(client, path), path, pool)
  }

  /** Wraps a common Status in a matchable way. */
  class Status(val underlying: CommonStatus) {
    def apply(): CommonStatus = underlying
    def unapply(status: CommonStatus): Boolean = { status == underlying }
  }

  /** Status matchers */
  object Status {
    object Alive    extends Status(CommonStatus.ALIVE)
    object Dead     extends Status(CommonStatus.DEAD)
    object Starting extends Status(CommonStatus.STARTING)
    object Stopping extends Status(CommonStatus.STOPPING)
    object Stopped  extends Status(CommonStatus.STOPPED)
    object Warning  extends Status(CommonStatus.WARNING)
  }

  /** Asynchronous wrapper for a common EndpointStatus.  A FuturePool should be provided. */
  class EndpointStatus(val underlying: CommonServerSet.EndpointStatus, pool: FuturePool) {
    def update(status: Status): Future[Unit] = pool { underlying.update(status()) }
  }

  /** ServerSet monitor that publishes updates on a Broker. */
  class InstanceBroker extends Broker[Set[ServiceInstance]]
      with HostChangeMonitor[ServiceInstance] {
    def onChange(instances: ImmutableSet[ServiceInstance]) {
      this ! instances.asScala
    }
  }
}
