package com.twitter.jvm

/**
 * Information about the JVM's safepoint
 *
 * @param syncTimeMillis Cumulative time, in milliseconds, spent
 * getting all threads to safepoint states
 *
 * @param totalTimeMillis Cumulative time, in milliseconds, that the
 * application has been stopped for safepoint operations
 *
 * @param count The number of safepoints taken place since
 * the JVM started
 */
case class Safepoint(syncTimeMillis: Long, totalTimeMillis: Long, count: Long)
