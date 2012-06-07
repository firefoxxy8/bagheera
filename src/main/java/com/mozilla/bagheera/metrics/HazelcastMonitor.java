/*
 * Copyright 2012 Mozilla Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mozilla.bagheera.metrics;

import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Instance;
import com.hazelcast.core.Instance.InstanceType;
import com.hazelcast.core.LifecycleEvent;
import com.hazelcast.core.LifecycleEvent.LifecycleState;
import com.hazelcast.core.LifecycleListener;
import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.MetricName;

public class HazelcastMonitor implements LifecycleListener {

    private static final Logger LOG = Logger.getLogger(HazelcastMonitor.class);
    
    private static HazelcastMonitor INSTANCE;
    private ScheduledExecutorService ses;
    private boolean isMonitoring;
    
    private HazelcastMonitor() {
        ses = Executors.newSingleThreadScheduledExecutor();
        isMonitoring = false;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                ses.shutdown();
                try {
                    if (ses.awaitTermination(10, TimeUnit.SECONDS)) {
                        ses.shutdownNow();
                        if (ses.awaitTermination(10, TimeUnit.SECONDS)) {
                            LOG.error("Unable to shutdown hazelcast monitor");
                        }
                    }
                } catch (InterruptedException e) {
                    ses.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
    
    public static HazelcastMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new HazelcastMonitor();
        }
        
        return INSTANCE;
    }
    
    @Override
    public void stateChanged(LifecycleEvent event) {
        if (LifecycleState.STARTED == event.getState()) {
            monitor();
        }
    }
    
    public void monitor() {
        if (!isMonitoring) {
            ses.scheduleAtFixedRate(new TimerTask() {
                public void run() {
                    for (Instance instance : Hazelcast.getInstances()) {
                        final String instanceId = (String)instance.getId();
                        if (InstanceType.MAP == instance.getInstanceType()) {
                            Metrics.newGauge(new MetricName(IMap.class, instanceId + ".size"), new Gauge<Integer>() {
                                @Override
                                public Integer value() {
                                    IMap<?,?> m = Hazelcast.getMap(instanceId);
                                    return m.size();
                                }
                            });
                        }
                    } 
                }
            }, 1, 60, TimeUnit.SECONDS);
            isMonitoring = true;
        }
    }
    
}