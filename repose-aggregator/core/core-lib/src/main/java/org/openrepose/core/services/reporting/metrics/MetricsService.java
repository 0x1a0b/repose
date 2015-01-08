package org.openrepose.core.services.reporting.metrics;

import org.openrepose.commons.utils.Destroyable;
import org.openrepose.core.services.reporting.metrics.impl.MeterByCategorySum;
import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Timer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * This service creates all necessary classes to track repose performance through JMX & Graphite.
 */
public interface MetricsService extends Destroyable {

    Meter newMeter(Class klass, String name, String scope, String eventType, TimeUnit unit);

    MeterByCategory newMeterByCategory(Class klass, String scope, String eventType, TimeUnit unit);

    MeterByCategorySum newMeterByCategorySum(Class klass, String scope, String eventType, TimeUnit unit);

    Counter newCounter(Class klass, String name, String scope);

    Timer newTimer(Class klass, String name, String scope, TimeUnit duration, TimeUnit rate);

    TimerByCategory newTimerByCategory(Class klass, String scope, TimeUnit duration, TimeUnit rate);
}
