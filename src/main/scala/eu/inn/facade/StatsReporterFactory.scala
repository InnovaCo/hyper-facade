package eu.inn.facade

import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}

import com.codahale.metrics
import com.codahale.metrics.graphite.Graphite
import com.codahale.metrics.graphite.GraphiteReporter
import com.codahale.metrics.Gauge
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class StatsReporterFactory(val config: Config) {
  val conf = config.getConfig("inn.util.graphite")

  def createStats(prefix: String*): StatsReporter#StatsWrapper =
    startReporter.createStats(prefix: _*)

  /**
   * Start graphite reporter
   */
  private[facade] lazy val startReporter = {

    StatsReporter.init(
      enabled        = conf.getBoolean("enabled"),
      host           = conf.getString("host"),
      port           = conf.getInt("port"),
      prefix         = conf.getString("prefix"),
      hostSuffix     = optionString("host-suffix").filter(_.trim.nonEmpty),
      reportPeriodMs = conf.getDuration("report-period", TimeUnit.MILLISECONDS)
    )
  }

  private def optionString(path: String): Option[String] = {
    if (conf.hasPath(path)) Some(conf.getString(path))
    else None
  }
}

object StatsReporter {
  def init(enabled: Boolean, host: String, port: Int, prefix: String, hostSuffix: Option[String], reportPeriodMs: Long) =
    new StatsReporter(new metrics.MetricRegistry, enabled, host, port, prefix, hostSuffix, reportPeriodMs)
}


class StatsReporter(metricRegistry: metrics.MetricRegistry, enabled: Boolean, host: String, port: Int, prefix: String, hostSuffix: Option[String], reportPeriodMs: Long) {

  val log = LoggerFactory.getLogger(StatsReporter.getClass.getName)
  private val gauges = new ConcurrentHashMap[String, GaugeWrapper]

  if (enabled) {
    log.info(s"Start reporting metrics to Graphite: $host:$port/$prefix each $reportPeriodMs ms")

    val graphite = new Graphite(host, port)

    val reporter = GraphiteReporter.forRegistry(metricRegistry)
      .prefixedWith(Seq(prefix, Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-host").replaceAll("\\.", "-") + hostSuffix.fold("")("-" + _)).filter(_.trim.nonEmpty).mkString("."))
      .build(graphite)

    reporter.start(reportPeriodMs, TimeUnit.MILLISECONDS)
  }

  def createStats(prefix: String*): StatsWrapper =
    if (enabled) {
      new StatsWrapper(prefix.head + prefix.tail.foldLeft("")(_ + "." + _.replaceAll("[^\\w]+", "-")))
    } else {
      NoopStatsWrapper
    }


  class StatsWrapper private[facade] (prefix: String) {

    def counter(name: String): metrics.Counter =
      metricRegistry.counter(prefix + ".counter." + name)

    def meter(name: String): metrics.Meter =
      metricRegistry.meter(prefix + ".meter." + name)

    def histogram(name: String): metrics.Histogram =
      metricRegistry.histogram(prefix + ".histogram." + name)

    def getTimer(name: String): TimerContext =
      new TimerContextImpl(metricRegistry.timer(prefix + ".timer." + name).time)

    def timer[A](name: String)(f: ⇒ A): A = {
      val timer = getTimer(name)
      try f finally { timer.stop() }
    }

    def futureTimer[T](name: String)(f: ⇒ Future[T])(implicit ec: ExecutionContext): Future[T] = {
      val timer = getTimer(name)
      f andThen { case _ ⇒ timer.stop() }
    }

    def newGauge(name: String)(f: ⇒ Long): Unit =
      metricRegistry.register(prefix + ".gauge." + name, new Gauge[Long] {
        override def getValue = f
      })

    def gauge(name: String): GaugeWrapper = {
      val fullName = prefix + ".gauge." + name
      val existing = gauges.putIfAbsent(fullName, new GaugeWrapper)

      if (existing == null) {
        val gaugeWrapper = gauges.get(fullName)
        newGauge(name)(gaugeWrapper.get)
        gaugeWrapper
      } else {
        existing
      }
    }
  }


  protected sealed trait TimerContext extends Closeable {
    def stop(): Unit
    def close(): Unit = stop()
  }


  protected class TimerContextImpl(ctx: metrics.Timer.Context) extends TimerContext {
    def stop(): Unit = ctx.stop()
  }


  protected class GaugeWrapper {
    private val value = new AtomicLong()
    private[facade] def get: Long = value.get()

    def update(newValue: Long): Unit = value.set(newValue)
  }


  /**
   * No-op implementation
   */
  object NoopStatsWrapper extends StatsWrapper("noop") {
    private val noopCounter = new metrics.Counter {
      override def inc(n: Long) {}
      override def dec(n: Long) {}
    }

    private val noopMeter = new metrics.Meter {
      override def mark(n: Long) {}
    }

    private val noopTimerCtx = new TimerContext {
      def stop() {}
    }

    private val noopGauge = new GaugeWrapper {
      override def update(newValue: Long) {}
    }

    override def counter(name: String): metrics.Counter = noopCounter
    override def meter(name: String): metrics.Meter = noopMeter
    override def getTimer(name: String): TimerContext = noopTimerCtx
    override def timer[A](name: String)(f: ⇒ A): A = f
    override def futureTimer[T](name: String)(f: ⇒ Future[T])(implicit ec: ExecutionContext): Future[T] = f
    override def gauge(name: String): GaugeWrapper = noopGauge
  }
}
