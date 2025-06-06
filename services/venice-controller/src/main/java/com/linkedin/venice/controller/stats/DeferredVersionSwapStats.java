package com.linkedin.venice.controller.stats;

import com.linkedin.venice.stats.AbstractVeniceStats;
import io.tehuti.metrics.MetricsRepository;
import io.tehuti.metrics.Sensor;
import io.tehuti.metrics.stats.Count;
import io.tehuti.metrics.stats.Gauge;


public class DeferredVersionSwapStats extends AbstractVeniceStats {
  private final Sensor deferredVersionSwapErrorSensor;
  private final Sensor deferredVersionSwapThrowableSensor;
  private final Sensor deferredVersionSwapFailedRollForwardSensor;
  private final Sensor deferredVersionSwapStalledVersionSwapSensor;
  private final static String DEFERRED_VERSION_SWAP_ERROR = "deferred_version_swap_error";
  private final static String DEFERRED_VERSION_SWAP_THROWABLE = "deferred_version_swap_throwable";
  private final static String DEFERRED_VERSION_SWAP_FAILED_ROLL_FORWARD = "deferred_version_swap_failed_roll_forward";
  private static final String DEFERRED_VERSION_SWAP_STALLED_VERSION_SWAP = "deferred_version_swap_stalled_version_swap";

  public DeferredVersionSwapStats(MetricsRepository metricsRepository) {
    super(metricsRepository, "DeferredVersionSwap");
    deferredVersionSwapErrorSensor = registerSensorIfAbsent(DEFERRED_VERSION_SWAP_ERROR, new Count());
    deferredVersionSwapThrowableSensor = registerSensorIfAbsent(DEFERRED_VERSION_SWAP_THROWABLE, new Count());
    deferredVersionSwapFailedRollForwardSensor =
        registerSensorIfAbsent(DEFERRED_VERSION_SWAP_FAILED_ROLL_FORWARD, new Count());
    deferredVersionSwapStalledVersionSwapSensor =
        registerSensorIfAbsent(DEFERRED_VERSION_SWAP_STALLED_VERSION_SWAP, new Gauge());
  }

  public void recordDeferredVersionSwapErrorSensor() {
    deferredVersionSwapErrorSensor.record();
  }

  public void recordDeferredVersionSwapThrowableSensor() {
    deferredVersionSwapThrowableSensor.record();
  }

  public void recordDeferredVersionSwapFailedRollForwardSensor() {
    deferredVersionSwapFailedRollForwardSensor.record();
  }

  public void recordDeferredVersionSwapStalledVersionSwapSensor(double value) {
    deferredVersionSwapStalledVersionSwapSensor.record(value);
  }
}
