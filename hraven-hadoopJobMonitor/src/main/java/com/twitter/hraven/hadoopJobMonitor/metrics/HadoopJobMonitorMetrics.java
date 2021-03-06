/*
Copyright 2014 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.hraven.hadoopJobMonitor.metrics;

import static org.apache.hadoop.metrics2.impl.MsInfo.ProcessName;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.metrics2.MetricsSource;
import org.apache.hadoop.metrics2.MetricsSystem;
import org.apache.hadoop.metrics2.annotation.Metric;
import org.apache.hadoop.metrics2.annotation.Metrics;
import org.apache.hadoop.metrics2.lib.DefaultMetricsSystem;
import org.apache.hadoop.metrics2.lib.MetricsAnnotations;
import org.apache.hadoop.metrics2.lib.MetricsRegistry;
import org.apache.hadoop.metrics2.lib.MetricsSourceBuilder;
import org.apache.hadoop.metrics2.lib.MutableCounterLong;

import com.twitter.hraven.hadoopJobMonitor.ClusterStatusChecker;

/**
 * This class is for maintaining the various HadoopJobMonitor activity statistics and
 * publishing them through the metrics interfaces as well as the web interface.
 */
@XmlRootElement(name = "cleanerMetrics")
@XmlAccessorType(XmlAccessType.FIELD)
@Metrics(name = "HadoopJobMonitorActivity", about = "HadoopJobMonitor service metrics", context = "yarn")
public class HadoopJobMonitorMetrics {
  public static final Log LOG = LogFactory.getLog(HadoopJobMonitorMetrics.class);
  @XmlTransient
  private final MetricsRegistry registry = new MetricsRegistry("HadoopJobMonitor");

  enum Singleton {
    INSTANCE;
    HadoopJobMonitorMetrics impl;

    synchronized HadoopJobMonitorMetrics init(Configuration conf) {
      if (impl == null) {
        impl = create(conf);
      }
      return impl;
    }
  }

  public static HadoopJobMonitorMetrics initSingleton(Configuration conf) {
    return Singleton.INSTANCE.init(conf);
  }

  public static HadoopJobMonitorMetrics getInstance() {
    HadoopJobMonitorMetrics hadoopJobMonitorMetrics = Singleton.INSTANCE.impl;
    if (hadoopJobMonitorMetrics == null)
      throw new IllegalStateException(
          "The HadoopJobMonitorMetics singlton instance is not initialized."
              + " Have you called init first?");
    return hadoopJobMonitorMetrics;
  }

  HadoopJobMonitorMetrics() {
    registry.tag(ProcessName, "HadoopJobMonitorService");
  }
  
  private class Heart implements Runnable {
    @Override
    public void run() {
      beat();
    }
    private void beat() {
      HadoopJobMonitorMetrics.getInstance().minutesSinceStart.incr();
    }
  }

  static HadoopJobMonitorMetrics create(Configuration conf) {
    MetricsSystem ms = DefaultMetricsSystem.instance();

    HadoopJobMonitorMetrics metricObject = new HadoopJobMonitorMetrics();
    MetricsSourceBuilder sb = MetricsAnnotations.newSourceBuilder(metricObject);
    final MetricsSource s = sb.build();
    ms.register("HadoopJobMonitorMetrics", "The Metrics of HadoopJobMonitor service", s);
    
    ScheduledExecutorService heartbeatExecutor =
        Executors.newSingleThreadScheduledExecutor(new SimpleThreadFactory());
    heartbeatExecutor.scheduleAtFixedRate(metricObject.new Heart(), 0, 1, TimeUnit.MINUTES);
    return metricObject;
  }

  /**
   * A thread factory for this thread
   */
  static class SimpleThreadFactory implements ThreadFactory {
    ThreadGroup threadGroup = new ThreadGroup(
        HadoopJobMonitorMetrics.class.getSimpleName());

    public Thread newThread(Runnable r) {
      Thread thread = new Thread(threadGroup, r);
      thread.setDaemon(true);
      thread.setName(HadoopJobMonitorMetrics.class.getSimpleName());
      return thread;
    }
  }
  
  public void shutdown() {
    DefaultMetricsSystem.shutdown();
  }

  /**
   * Number of actual killings performed by HadoopJobMonitor
   */
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of killed apps")
  public MutableCounterLong killedApps;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of killed tasks")
  public MutableCounterLong killedTasks;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of killed mappers")
  public MutableCounterLong killedMappers;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of killed reducers")
  public MutableCounterLong killedReducers;

  /**
   * Number of bad behaved entities discovered by HadoopJobMonitor
   */
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of badBehaved apps")
  public MutableCounterLong badBehavedApps;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of badBehaved tasks")
  public MutableCounterLong badBehavedTasks;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of badBehaved mappers")
  public MutableCounterLong badBehavedMappers;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of badBehaved reducers")
  public MutableCounterLong badBehavedReducers;

  /**
   * Number of entities that were inspected by HadoopJobMonitor
   */
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of inspected apps")
  public MutableCounterLong inspectedApps;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of inspected tasks")
  public MutableCounterLong inspectedTasks;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of inspected mappers")
  public MutableCounterLong inspectedMappers;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of inspected reducers")
  public MutableCounterLong inspectedReducers;

  /**
   * Number of inspected entities that that configured the HadoopJobMonitor-related
   * params
   */
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of configured apps")
  public MutableCounterLong configuredApps;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of configured tasks")
  public MutableCounterLong configuredTasks;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of configured mappers")
  public MutableCounterLong configuredMappers;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of configured reducers")
  public MutableCounterLong configuredReducers;

  /**
   * Number of inspected entities that requested enforcement from HadoopJobMonitor
   */
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of enforced apps")
  public MutableCounterLong enforcedApps;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of enforced tasks")
  public MutableCounterLong enforcedTasks;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of enforced mappers")
  public MutableCounterLong enforcedMappers;
  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("number of enforced reducers")
  public MutableCounterLong enforcedReducers;

  @XmlJavaTypeAdapter(MutableCounterLongAdapter.class)
  @Metric("minutes passes since start: used as a heartbeat")
  public MutableCounterLong minutesSinceStart;
}
