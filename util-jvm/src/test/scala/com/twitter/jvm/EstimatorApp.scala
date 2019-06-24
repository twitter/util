package com.twitter.jvm

/**
 * Take a GC log produced by:
 *
 * {{{
 * $ jstat -gc \$PID 250 ...
 * }}}
 *
 * And report on GC prediction accuracy. Time is
 * indexed by the jstat output, and the columns are,
 * in order: current time, next gc, estimated next GC.
 */
object EstimatorApp extends App {
  import com.twitter.conversions.StorageUnitOps._

  val estimator = args match {
    case Array("kalman", n, error) =>
      new KalmanGaussianError(n.toInt, error.toDouble)
    case Array("windowed", n, windows) =>
      new WindowedMeans(
        n.toInt,
        windows.split(",").iterator.map { w =>
          w.split(":") match {
            case Array(w, i) => (w.toInt, i.toInt)
            case _ => throw new IllegalArgumentException("bad weight, count pair " + w)
          }
        }.toSeq
      )
    case Array("load", interval) =>
      new LoadAverage(interval.toDouble)
    case _ => throw new IllegalArgumentException("bad args ")
  }

  val lines = scala.io.Source.stdin.getLines().drop(1)
  val states = lines.toArray map (_.split(" ") filter (_ != "") map (_.toDouble)) collect {
    case Array(s0c, s1c, s0u, s1u, ec, eu, oc, ou, pc, pu, ygc, ygct, fgc, fgct, gct) =>
      PoolState(ygc.toLong, ec.toLong.bytes, eu.toLong.bytes)
  }

  var elapsed = 1
  for (List(begin, end) <- states.toList.sliding(2)) {
    val allocated = (end - begin).used
    estimator.measure(allocated.inBytes)
    val r = end.capacity - end.used
    val i = (r.inBytes / estimator.estimate.toLong) + elapsed
    val j = states.indexWhere(_.numCollections > end.numCollections)

    if (j > 0)
      println("%d %d %d".format(elapsed, j, i))

    elapsed += 1
  }
}

/*

The following script is useful for plotting
results from EstimatorApp:

	% scala ... com.twitter.jvm.EstimatorApp [ARGS] > /tmp/out

#!/usr/bin/env gnuplot

set terminal png size 800,600
set title "GC predictor"

set macros

set grid
set timestamp "Generated on %Y-%m-%d by `whoami`" font "Helvetica-Oblique, 8pt"
set noclip
set xrange [0:1100]

set key reverse left Left # box

set ylabel "Time of GC" textcolor lt 3
set yrange [0:1100]
set mytics 5
set y2tics

set xlabel "Time" textcolor lt 4
set mxtics 5

set boxwidth 0.5
set style fill transparent pattern 4 bo

plot "< awk '{print $1 \" \" $2}' /tmp/out" title "Actual", \
	"< awk '{print $1 \" \" $3}' /tmp/out" title "Predicted", \
	"< awk '{print $1 \" \" $1}' /tmp/out" title "time" with lines

 */
