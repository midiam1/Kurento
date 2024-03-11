/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kurento.test.latency;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.kurento.test.base.KurentoTest;
import org.kurento.test.browser.WebPage;
import org.kurento.test.monitor.SystemMonitorManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Latency controller.
 *
 * @author Micael Gallego (micael.gallego@gmail.com)
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.5
 */
public class LatencyController implements ChangeColorEventListener<ChangeColorEvent> {

  private static final int MAX_DISTANCE = 60;

  public Logger log = LoggerFactory.getLogger(LatencyController.class);

  private Map<Long, LatencyRegistry> latencyMap;

  private String name;

  private long latencyThreshold;
  private TimeUnit latencyThresholdTimeUnit;

  private long timeout;
  private TimeUnit timeoutTimeUnit;

  private ChangeColorObservable localChangeColor;
  private ChangeColorObservable remoteChangeColor;

  private long lastLocalColorChangeTime = -1;
  private long lastRemoteColorChangeTime = -1;
  private long lastLocalColorChangeTimeAbsolute = -1;
  private long lastRemoteColorChangeTimeAbsolute = -1;

  private Color lastLocalColor;
  private Color lastRemoteColor;

  private Thread localColorTrigger;
  private Thread remoteColorTrigger;

  private Semaphore localEventLatch = new Semaphore(0);
  private Semaphore remoteEventLatch = new Semaphore(0);

  private boolean failIfLatencyProblem;

  private long latencyRate;

  private int consecutiveFailMax;

  private SystemMonitorManager monitor;

  public LatencyController(String name) {
    this();
    this.name = name;
  }

  public LatencyController(String name, SystemMonitorManager monitor) {
    this(name);
    this.monitor = monitor;
  }

  public LatencyController() {
    // Defaults
    latencyThreshold = 3000;
    latencyThresholdTimeUnit = TimeUnit.MILLISECONDS;

    timeout = 30;
    timeoutTimeUnit = TimeUnit.SECONDS;

    failIfLatencyProblem = false;

    latencyRate = 100; // milliseconds

    consecutiveFailMax = 3;

    // Latency map (registry)
    latencyMap = new TreeMap<Long, LatencyRegistry>();
  }

  @Override
  public synchronized void onEvent(ChangeColorEvent event) {
    if (event.getVideoTag().getVideoTagType() == VideoTagType.LOCAL) {
      lastLocalColorChangeTimeAbsolute = new Date().getTime();
      lastLocalColorChangeTime = event.getTime();
      lastLocalColor = event.getColor();
      localEventLatch.release();
    } else if (event.getVideoTag().getVideoTagType() == VideoTagType.REMOTE) {
      lastRemoteColorChangeTimeAbsolute = new Date().getTime();
      lastRemoteColorChangeTime = event.getTime();
      lastRemoteColor = event.getColor();
      remoteEventLatch.release();
    }
  }

  public void checkLatencyInBackground(final long testTime, final TimeUnit testTimeUnit,
      final WebPage client) throws InterruptedException, IOException {
    new Thread() {
      @Override
      public void run() {
        try {
          checkLatency(testTime, testTimeUnit, client);
        } catch (InterruptedException e1) {
          log.warn("checkLatencyInBackground InterruptedException: {}", e1.getMessage());
        } catch (IOException e2) {
          throw new RuntimeException(e2);
        }
      }
    }.start();
  }

  public void checkLatencyInBackground(final long testTime, final TimeUnit testTimeUnit,
      final WebPage localClient, final WebPage remoteClient) {
    new Thread() {
      @Override
      public void run() {
        checkLatency(testTime, testTimeUnit, localClient, remoteClient);
      }
    }.start();
  }

  public void checkLatencyInBackground(final WebPage localClient, final WebPage remoteClient) {
    new Thread() {
      @Override
      public void run() {
        checkLatency(Long.MAX_VALUE, TimeUnit.SECONDS, localClient, remoteClient);
      }
    }.start();
  }

  public void checkLatency(final long testTime, final TimeUnit testTimeUnit, WebPage client)
      throws InterruptedException, IOException {
    long playTime = TimeUnit.MILLISECONDS.convert(testTime, testTimeUnit);
    long endTimeMillis = System.currentTimeMillis() + playTime;
    int consecutiveFailCounter = 0;
    boolean first = true;

    while (true) {
      if (System.currentTimeMillis() > endTimeMillis) {
        break;
      }
      Thread.sleep(latencyRate);

      long latency = 0;
      LatencyRegistry latencyRegistry = new LatencyRegistry();
      try {
        latency = client.getLatency();
        if (latency == Long.MIN_VALUE || latency == 0) {
          continue;
        }
        if (first) {
          // First latency measurement is discarded
          first = false;
          continue;
        }
        log.debug(">>> Latency adquired: {} ms", latency);

      } catch (LatencyException le) {
        latencyRegistry.setLatencyException(le);

        if (failIfLatencyProblem) {
          throw le;
        }
      }

      long latencyTime = client.getCurrentTime(new VideoTag(VideoTagType.REMOTE));
      latencyRegistry.setLatency(latency);

      if (latency > getLatencyThreshold(TimeUnit.MILLISECONDS)) {

        String parsedtime = new SimpleDateFormat("mm-ss.SSS").format(latencyTime);
        client.takeScreeshot(
            KurentoTest.getDefaultOutputFile("-" + parsedtime + "-error-screenshot.png"));

        LatencyException latencyException = new LatencyException(latency, TimeUnit.MILLISECONDS);

        latencyRegistry.setLatencyException(latencyException);
        if (failIfLatencyProblem) {
          throw latencyException;
        }

        consecutiveFailCounter++;
        if (consecutiveFailCounter >= consecutiveFailMax) {
          throw new RuntimeException(
              consecutiveFailMax + " consecutive latency errors detected. Latest: "
                  + latencyException.getLocalizedMessage());
        }
      } else {
        // Reset the consecutive fail counter
        consecutiveFailCounter = 0;
      }
      latencyMap.put(latencyTime, latencyRegistry);
    }
  }

  public void checkLatency(final long testTime, final TimeUnit testTimeUnit, WebPage localClient,
      WebPage remoteClient) {

    addChangeColorEventListener(new VideoTag(VideoTagType.LOCAL), localClient,
        getName() + " " + VideoTagType.LOCAL);
    addChangeColorEventListener(new VideoTag(VideoTagType.REMOTE), remoteClient,
        getName() + " " + VideoTagType.REMOTE);

    String msgName = name != null ? "[" + name + "] " : "";

    if (localChangeColor == null || remoteChangeColor == null) {
      throw new RuntimeException(msgName + "Bad setup in latency controller "
          + " (local and remote tag of browser(s) needed");
    }

    try {
      final Thread waitingThread = Thread.currentThread();

      Thread thread;
      if (testTimeUnit != null) {
        thread = new Thread() {
          @Override
          public void run() {
            try {
              testTimeUnit.sleep(testTime);
              waitingThread.interrupt();
            } catch (InterruptedException e) {
              // Intentionally left blank
            }
          }
        };
        thread.setDaemon(true);
        thread.start();
      } else {
        thread = waitingThread;
      }

      // Synchronization with the green color
      do {
        waitForLocalColor(msgName, thread);
      } while (!similarColor(lastLocalColor, Color.GREEN));
      do {
        waitForRemoteColor(msgName, thread);
      } while (!similarColor(lastRemoteColor, Color.GREEN));

      while (true) {

        waitForLocalColor(msgName, thread);
        waitForRemoteColor(msgName, thread);

        long latencyMilis =
            Math.abs(lastRemoteColorChangeTimeAbsolute - lastLocalColorChangeTimeAbsolute);

        SimpleDateFormat formater = new SimpleDateFormat("mm:ss.SSS");
        String parsedLocaltime = formater.format(lastLocalColorChangeTimeAbsolute);
        String parsedRemotetime = formater.format(lastRemoteColorChangeTimeAbsolute);

        log.debug(
            "latencyMilis={} -- lastLocalColor={} -- lastRemoteColor={} -- "
                + "lastLocalColorChangeTime={} -- lastRemoteColorChangeTime={} -- "
                + "lastLocalColorChangeTimeAbsolute={} -- lastRemoteColorChangeTimeAbsolute={}",
            latencyMilis, lastLocalColor, lastRemoteColor,
            formater.format(lastLocalColorChangeTime), formater.format(lastRemoteColorChangeTime),
            parsedLocaltime, parsedRemotetime);

        if (similarColor(lastLocalColor, lastRemoteColor)) {
          log.debug("--> Latency adquired ({} ms)", latencyMilis);

          if (monitor != null) {
            monitor.addCurrentLatency(latencyMilis);
          }

          LatencyRegistry LatencyRegistry = new LatencyRegistry(lastRemoteColor, latencyMilis);

          if (latencyMilis > getLatencyThreshold(TimeUnit.MILLISECONDS)) {
            LatencyException latencyException = new LatencyException(latencyMilis, testTimeUnit,
                parsedLocaltime, parsedRemotetime, testTime, latencyMilis);
            LatencyRegistry.setLatencyException(latencyException);
            if (failIfLatencyProblem) {
              thread.interrupt();
              throw latencyException;
            } else {
              log.warn(latencyException.getMessage());
            }
            if (monitor != null) {
              monitor.incrementLatencyErrors();
            }
          }

          latencyMap.put(lastRemoteColorChangeTime, LatencyRegistry);
        }

      }

    } catch (InterruptedException e) {
      log.debug("Finished LatencyController thread due to Interrupted Exception");
    }
    localColorTrigger.interrupt();
    remoteColorTrigger.interrupt();
  }

  private void waitForRemoteColor(String msgName, Thread thread) throws InterruptedException {
    if (!remoteEventLatch.tryAcquire(timeout, timeoutTimeUnit)) {
      thread.interrupt();
      throw new RuntimeException(msgName + "Change color not detected in REMOTE steam after "
          + timeout + " " + timeoutTimeUnit);
    }
  }

  private void waitForLocalColor(String msgName, Thread thread) throws InterruptedException {
    if (!localEventLatch.tryAcquire(timeout, timeoutTimeUnit)) {
      thread.interrupt();

      throw new RuntimeException(msgName + "Change color not detected in LOCAL steam after "
          + timeout + " " + timeoutTimeUnit);
    }
  }

  private boolean similarColor(Color expectedColor, Color realColor) {
    int realRed = realColor.getRed();
    int realGreen = realColor.getGreen();
    int realBlue = realColor.getBlue();

    int expectedRed = expectedColor.getRed();
    int expectedGreen = expectedColor.getGreen();
    int expectedBlue = expectedColor.getBlue();

    double distance = Math.sqrt((realRed - expectedRed) * (realRed - expectedRed)
        + (realGreen - expectedGreen) * (realGreen - expectedGreen)
        + (realBlue - expectedBlue) * (realBlue - expectedBlue));
    return distance <= MAX_DISTANCE;
  }

  public void addChangeColorEventListener(VideoTag type, WebPage testClient, String name) {
    final long timeoutSeconds = TimeUnit.SECONDS.convert(timeout, timeoutTimeUnit);

    if (type.getVideoTagType() == VideoTagType.LOCAL) {
      localChangeColor = new ChangeColorObservable();
      localChangeColor.addListener(this);
      localColorTrigger =
          new Thread(new ColorTrigger(type, testClient, localChangeColor, timeoutSeconds));
      if (name != null) {
        localColorTrigger.setName(name);
      }
      localColorTrigger.start();
    } else {
      remoteChangeColor = new ChangeColorObservable();
      remoteChangeColor.addListener(this);
      remoteColorTrigger =
          new Thread(new ColorTrigger(type, testClient, remoteChangeColor, timeoutSeconds));
      if (name != null) {
        remoteColorTrigger.setName(name);
      }
      remoteColorTrigger.start();
    }
  }

  public void drawChart(String filename, int width, int height) throws IOException {
    ChartWriter chartWriter = new ChartWriter(latencyMap, getName());
    chartWriter.drawChart(filename, width, height);
  }

  public void writeCsv(String csvTitle) throws IOException {
    PrintWriter pw = new PrintWriter(new FileWriter(csvTitle));
    for (long time : latencyMap.keySet()) {
      pw.println(time + "," + latencyMap.get(time).getLatency());
    }
    pw.close();
  }

  public void logLatencyErrorrs() throws IOException {
    log.debug("---------------------------------------------");
    log.debug("LATENCY ERRORS " + getName());
    log.debug("---------------------------------------------");
    int numErrors = 0;
    for (LatencyRegistry registry : latencyMap.values()) {
      if (registry.isLatencyError()) {
        numErrors++;
        log.debug(registry.getLatencyException().getMessage());
      }
    }

    log.debug("{} errors of latency detected (threshold: {} {})", numErrors, latencyThreshold,
        latencyThresholdTimeUnit);
    log.debug("---------------------------------------------");
  }

  public long getLatencyThreshold(TimeUnit timeUnit) {
    return timeUnit.convert(latencyThreshold, latencyThresholdTimeUnit);
  }

  public long getLatencyThreshold() {
    return latencyThreshold;
  }

  public void setLatencyThreshold(long latencyThreshold, TimeUnit latencyThresholdTimeUnit) {
    this.latencyThreshold = latencyThreshold;
    this.latencyThresholdTimeUnit = latencyThresholdTimeUnit;
  }

  public TimeUnit getLatencyTimeUnit() {
    return latencyThresholdTimeUnit;
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout, TimeUnit timeoutTimeUnit) {
    this.timeout = timeout;
    this.timeoutTimeUnit = timeoutTimeUnit;
  }

  public TimeUnit getTimeoutTimeUnit() {
    return timeoutTimeUnit;
  }

  public void failIfLatencyProblem() {
    this.failIfLatencyProblem = true;
  }

  public String getName() {
    return name != null ? name : "";
  }

  public void setLatencyRate(long latencyRate) {
    this.latencyRate = latencyRate;
  }

  public void setConsecutiveFailMax(int consecutiveFailMax) {
    this.consecutiveFailMax = consecutiveFailMax;
  }

  public Map<Long, LatencyRegistry> getLatencyMap() {
    return latencyMap;
  }

}
