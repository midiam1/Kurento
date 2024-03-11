/*

 * (C) Copyright 2015 Kurento (http://kurento.org/)
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

package org.kurento.test.grid;

import static org.kurento.commons.PropertiesManager.getProperty;
import static org.kurento.test.config.TestConfiguration.SELENIUM_HUB_ADDRESS;
import static org.kurento.test.config.TestConfiguration.SELENIUM_HUB_ADDRESS_DEFAULT;
import static org.kurento.test.config.TestConfiguration.SELENIUM_HUB_PORT_DEFAULT;
import static org.kurento.test.config.TestConfiguration.SELENIUM_HUB_PORT_PROPERTY;
import static org.kurento.test.config.TestConfiguration.SELENIUM_NODES_FILE_LIST_PROPERTY;
import static org.kurento.test.config.TestConfiguration.SELENIUM_NODES_LIST_DEFAULT;
import static org.kurento.test.config.TestConfiguration.SELENIUM_NODES_LIST_PROPERTY;
import static org.kurento.test.config.TestConfiguration.SELENIUM_NODES_URL_PROPERTY;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;

import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.kurento.test.base.PerformanceTest;
import org.kurento.test.browser.BrowserType;
import org.kurento.test.config.TestScenario;
import org.kurento.test.utils.Randomizer;
import org.kurento.test.utils.Shell;
import org.kurento.test.utils.SshConnection;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.support.events.WebDriverEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.google.gson.JsonElement;

import freemarker.template.Configuration;
import freemarker.template.Template;

/**
 * Singleton handler for Selenium Grid infrastructure.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.1.1
 * @see <a href="http://www.seleniumhq.org/">Selenium</a>
 */
public class GridHandler {

  public static Logger log = LoggerFactory.getLogger(GridHandler.class);

  public static final String REMOTE_FOLDER = ".kurento-test";
  public static final String REMOTE_PID_FILE = "node-pid";
  public static final String IPS_REGEX =
      "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

  private static final int TIMEOUT_NODE = 300; // seconds
  private static final String LAUNCH_SH = "launch-node.sh";

  private static GridHandler instance = null;

  private GridHub hub;
  private String hubAddress = getProperty(SELENIUM_HUB_ADDRESS, SELENIUM_HUB_ADDRESS_DEFAULT);
  private int hubPort = getProperty(SELENIUM_HUB_PORT_PROPERTY, SELENIUM_HUB_PORT_DEFAULT);
  private CountDownLatch countDownLatch;
  private Map<String, GridNode> nodes = new ConcurrentHashMap<>();
  private List<String> nodeList;
  private boolean hubStarted = false;
  private boolean nodeListFiltered = false;

  protected GridHandler() {
    String nodesListProp = System.getProperty(SELENIUM_NODES_LIST_PROPERTY);
    String nodesListFileProp = System.getProperty(SELENIUM_NODES_FILE_LIST_PROPERTY);
    String nodesListUrlProp = System.getProperty(SELENIUM_NODES_URL_PROPERTY);

    if (nodesListUrlProp != null) {
      if (nodeList == null) {
        nodeList = new ArrayList<>();
        try {
          log.trace("Reading node list from URL {}", nodesListUrlProp);
          String contents = readContents(nodesListUrlProp);
          Pattern p = Pattern.compile(IPS_REGEX);
          Matcher m = p.matcher(contents);
          while (m.find()) {
            nodeList.add(m.group());
          }
        } catch (IOException e) {
          Assert.fail("Exception reading URL " + nodesListUrlProp + " : " + e.getMessage());
        }
      }

    } else if (nodesListFileProp != null) {
      log.trace("Reading node list from file {}", nodesListFileProp);
      try {
        nodeList = FileUtils.readLines(new File(nodesListFileProp), Charset.defaultCharset());
      } catch (IOException e) {
        Assert.fail("Exception reading node list file: " + e.getMessage());
      }

    } else if (nodesListProp != null) {
      log.trace("Reading node list from property {}", nodesListProp);
      nodeList = new ArrayList<>(Arrays.asList(nodesListProp.split(";")));

    } else {
      log.trace("Using default node list {}", SELENIUM_NODES_LIST_DEFAULT);
      InputStream inputStream =
          PerformanceTest.class.getClassLoader().getResourceAsStream(SELENIUM_NODES_LIST_DEFAULT);

      try {
        nodeList = CharStreams.readLines(new InputStreamReader(inputStream, Charsets.UTF_8));
      } catch (IOException e) {
        Assert.fail("Exception reading node-list.txt: " + e.getMessage());
      }
    }
  }

  public static synchronized GridHandler getInstance() {
    if (instance == null) {
      instance = new GridHandler();
    }
    return instance;
  }

  public synchronized void stopGrid() {
    log.debug("Stopping Selenium Grid");
    try {
      // Stop Hub
      if (hub != null) {
        log.debug("Stopping Hub");
        hub.stop();
        hubStarted = false;
      }

      // Stop Nodes
      if (nodes != null) {
        log.debug("Number of nodes: {}", nodes.size());

        for (GridNode node : nodes.values()) {
          log.debug("Stopping Node {}", node.getHost());
          stopNode(node);
        }
      }
      nodes.clear();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

  }

  public synchronized void startHub() {
    try {
      if (hubAddress != null && !hubStarted) {
        hub = new GridHub(hubPort);
        hub.start();
        hubStarted = true;
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void startNodes() {
    try {
      countDownLatch = new CountDownLatch(nodes.size());
      ExecutorService exec = Executors.newFixedThreadPool(nodes.size());

      for (final GridNode n : nodes.values()) {
        Thread t = new Thread() {
          @Override
          public void run() {
            startNode(n);
          }
        };
        exec.execute(t);
      }

      if (!countDownLatch.await(TIMEOUT_NODE, TimeUnit.SECONDS)) {
        Assert.fail("Timeout waiting nodes (" + TIMEOUT_NODE + " seconds)");
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void startNode(GridNode node) {
    try {
      countDownLatch = new CountDownLatch(1);
      log.debug("Launching node {}", node.getHost());
      node.startSsh();

      final String chromeDriverSource = System.getProperty("webdriver.chrome.driver");

      final Class<?>[] classpath = { ImmutableList.class,
          NetworkUtils.class, WebDriverException.class, LogFactory.class, HttpServlet.class,
          ChromeDriver.class, FirefoxDriver.class, JsonElement.class, HttpEntity.class,
          HttpClient.class, WebDriverEventListener.class, ExecuteWatchdog.class };

      // OverThere SCP need absolute path, so home path must be known
      String remoteHome = node.getHome();

      final String remoteFolder = remoteHome + "/" + REMOTE_FOLDER;
      final String remoteChromeDriver = remoteFolder + "/chromedriver";
      final String remoteScript = node.getTmpFolder() + "/" + LAUNCH_SH;
      final String remotePort = String.valueOf(node.getSshConnection().getFreePort());

      if (!node.getSshConnection().exists(remoteFolder) || node.isOverwrite()) {
        node.getSshConnection().execAndWaitCommand("mkdir", "-p", remoteFolder);
      }
      if (!node.getSshConnection().exists(remoteChromeDriver) || node.isOverwrite()) {
        node.getSshConnection().scp(chromeDriverSource, remoteChromeDriver);
        node.getSshConnection().execAndWaitCommand("chmod", "+x", remoteChromeDriver);
      }

      String cp = "";
      for (Class<?> clazz : classpath) {
        if (!cp.isEmpty()) {
          cp += ":";
        }
        String jarSource = getJarPath(clazz).getAbsolutePath();
        String remoteSeleniumJar = remoteFolder + "/" + getJarPath(clazz).getName();
        cp += remoteSeleniumJar;

        if (!node.getSshConnection().exists(remoteSeleniumJar) || node.isOverwrite()) {
          node.getSshConnection().scp(jarSource, remoteSeleniumJar);
        }
      }

      // Script is always overwritten
      createRemoteScript(node, remotePort, remoteScript, remoteFolder, remoteChromeDriver, cp,
          node.getBrowserType(), node.getMaxInstances());

      // Launch node
      node.getSshConnection().execCommand(remoteScript);

      // Wait to be available for Hub
      waitForNode(node.getHost(), remotePort);

      // Set started flag to true
      node.setStarted(true);
    } catch (Exception e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private File getJarPath(Class<?> aclass) {
    URL url;
    try {
      url = aclass.getProtectionDomain().getCodeSource().getLocation();
    } catch (SecurityException ex) {
      url = aclass.getResource(aclass.getSimpleName() + ".class");
    }
    try {
      return new File(url.toURI());
    } catch (URISyntaxException ex) {
      return new File(url.getPath());
    }
  }

  private void createRemoteScript(GridNode node, String remotePort, String remoteScript,
      String remoteFolder, String remoteChromeDriver, String classpath, BrowserType browser,
      int maxInstances) throws IOException {

    Map<String, Object> data = new HashMap<String, Object>();
    data.put("remotePort", String.valueOf(remotePort));
    data.put("maxInstances", String.valueOf(maxInstances));
    data.put("hubIp", hubAddress);
    data.put("hubPort", String.valueOf(hubPort));
    data.put("tmpFolder", node.getTmpFolder());
    data.put("remoteChromeDriver", remoteChromeDriver);
    data.put("classpath", classpath);
    data.put("pidFile", REMOTE_PID_FILE);
    data.put("browser", browser);

    // Create script for Node
    Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
    cfg.setClassForTemplateLoading(PerformanceTest.class, "/templates/");

    String tmpScript = node.getTmpFolder() + LAUNCH_SH;
    try {
      Template template = cfg.getTemplate(LAUNCH_SH + ".ftl");
      Writer writer = new FileWriter(new File(tmpScript));
      template.process(data, writer);
      writer.flush();
      writer.close();

    } catch (Exception e) {
      throw new RuntimeException("Exception while creating file from template", e);
    }

    // Copy script to remote node
    node.getSshConnection().scp(tmpScript, remoteScript);
    node.getSshConnection().execAndWaitCommand("chmod", "+x", remoteScript);
    Shell.runAndWait("rm", tmpScript);
  }

  public void copyRemoteVideo(GridNode node, String video) {
    try {
      // Copy video in remote host if necessary
      if (!node.getSshConnection().exists(node.getRemoteVideo(video)) || node.isOverwrite()) {
        node.getSshConnection().scp(video, node.getRemoteVideo(video));
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void waitForNode(String node, String port) {
    log.debug("Waiting for node {} to be ready...", node);
    int responseStatusCode = 0;
    HttpClient client = HttpClientBuilder.create().build();
    HttpGet httpGet =
        new HttpGet("http://" + node + ":" + port + "/wd/hub/static/resource/hub.html");

    // Wait for a max of TIMEOUT_NODE seconds
    long maxSystemTime = System.currentTimeMillis() + TIMEOUT_NODE * 1000;
    do {
      try {
        HttpResponse response = client.execute(httpGet);
        responseStatusCode = response.getStatusLine().getStatusCode();
      } catch (Exception e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
          // Intentionally left blank
        }
        if (System.currentTimeMillis() > maxSystemTime) {
          log.error("Timeout ({} sec) waiting for node {}", TIMEOUT_NODE, node);
        }
      }
    } while (responseStatusCode != HttpStatus.SC_OK);

    if (responseStatusCode == HttpStatus.SC_OK) {
      log.debug("Node {} ready (responseStatus {})", node, responseStatusCode);
      countDownLatch.countDown();
    }
  }

  public synchronized void filterValidNodes() {
    if (!nodeListFiltered) {
      log.debug("Node availables in the node list: {}", nodeList.size());
      int nodeListSize = nodeList.size();
      ExecutorService executor = Executors.newFixedThreadPool(nodeListSize);
      final CountDownLatch latch = new CountDownLatch(nodeListSize);

      for (final String nodeCandidate : nodeList) {
        executor.execute(new Runnable() {
          @Override
          public void run() {
            if (!nodeIsValid(nodeCandidate)) {
              nodeList.remove(nodeCandidate);
            }
            latch.countDown();
          }
        });
      }

      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      nodeListFiltered = true;
      log.debug("Node availables in the node list after filtering: {}", nodeList.size());
    }
  }

  public boolean nodeIsValid(String nodeCandidate) {
    boolean valid = false;
    log.debug("Node candidate {}", nodeCandidate);

    if (SshConnection.ping(nodeCandidate)) {
      SshConnection remoteHost = new SshConnection(nodeCandidate);
      try {
        remoteHost.start();
        int xvfb = remoteHost.runAndWaitCommand("xvfb-run");
        if (xvfb != 2) {
          log.debug("Node {} has no Xvfb", nodeCandidate);
        } else {
          valid = true;
        }
      } catch (Exception e) {
        log.debug("Invalid credentials to access node {} ", nodeCandidate);
      } finally {
        remoteHost.stop();
      }

    } else {
      log.debug("Node {} seems to be down", nodeCandidate);
    }
    return valid;
  }

  public synchronized GridNode getRandomNodeFromList(String browserKey, BrowserType browserType,
      int browserPerInstance) {

    log.debug("getRandomNodeFromList for browser {}", browserKey);

    GridNode node = browserPerInstance > 1 ? existsNode(browserKey) : null;
    if (node == null) {
      try {

        String nodeCandidate = nodeList.get(Randomizer.getInt(0, nodeList.size()));
        log.debug("######## Creating node {} in host {}", browserKey, nodeCandidate);
        node = new GridNode(nodeCandidate, browserType, browserPerInstance);
        addNode(browserKey, node);
        nodeList.remove(nodeCandidate);
        log.debug(">>>> Using node {} for browser '{}'", node.getHost(), browserKey);

      } catch (IllegalArgumentException e) {
        throw new RuntimeException("No valid available node(s) to perform Selenim Grid test");
      }

    } else {
      log.debug(">>>> Re-using node {} for browser '{}'", node.getHost(), browserKey);
      node.setStarted(true);
    }
    return node;

  }

  private synchronized GridNode existsNode(String browserKey) {
    GridNode gridNode = null;
    int indexOfSeparator = browserKey.lastIndexOf(TestScenario.INSTANCES_SEPARATOR);

    if (indexOfSeparator != -1) {
      String browserPreffix = browserKey.substring(0, indexOfSeparator + 1);
      log.debug("browserPreffix {}", browserPreffix);

      for (String node : nodes.keySet()) {
        if (node.startsWith(browserPreffix)) {
          gridNode = nodes.get(node);
          break;
        }
      }
    }
    log.debug("Exists node {} = {}", browserKey, gridNode != null);

    return gridNode;
  }

  private void stopNode(GridNode node) throws IOException {
    if (node.getSshConnection().isStarted()) {
      node.getSshConnection().execCommand("kill", "-9", "-1");
      node.stopSsh();
    }
  }

  public void runParallel(List<GridNode> nodeList, Runnable myFunc)
      throws InterruptedException, ExecutionException {
    ExecutorService exec = Executors.newFixedThreadPool(nodes.size());
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i++) {
      results.add(exec.submit(myFunc));
    }
    for (Future<?> r : results) {
      r.get();
    }
  }

  public String getHubHost() {
    return hubAddress;
  }

  public int getHubPort() {
    return hubPort;
  }

  public GridNode getNode(String browserKey) {
    return nodes.get(browserKey);
  }

  public synchronized void addNode(String browserKey, GridNode node) {
    log.debug("Adding node {} ({}) to map", browserKey, node.getHost());
    nodes.put(browserKey, node);
  }

  public boolean useRemoteNodes() {
    return !nodes.isEmpty();
  }

  public void logNodeList() {
    String nodeListStr = "";
    for (GridNode node : nodes.values()) {
      nodeListStr += node.getHost() + " ";
    }
    log.debug("Node list: {}", nodeListStr);
  }

  public GridNode getFirstNode(String browserKey) {
    if (nodes.containsKey(browserKey)) {
      return nodes.get(browserKey);
    } else {
      return nodes.get(browserKey.substring(0, browserKey.indexOf("-") + 1) + 0);
    }
  }

  public synchronized boolean containsSimilarBrowserKey(String browserKey) {
    boolean constainsSimilarBrowser = false;
    int index = browserKey.indexOf("-");
    String browser = null;
    if (index != -1) {
      String prefix = browserKey.substring(0, browserKey.indexOf("-"));
      for (String key : nodes.keySet()) {
        constainsSimilarBrowser |= key.startsWith(prefix);
        if (constainsSimilarBrowser) {
          browser = key;
          break;
        }
      }
    }

    if (constainsSimilarBrowser && !nodes.keySet().contains(browserKey) && browser != null) {
      addNode(browserKey, nodes.get(browser));
    }
    return constainsSimilarBrowser;
  }

  public void setHubAddress(String hubAddress) {
    this.hubAddress = hubAddress;
  }

  public static String readContents(String address) throws IOException {
    StringBuilder contents = new StringBuilder(2048);
    BufferedReader br = null;
    try {
      URL url = new URL(address);
      br = new BufferedReader(new InputStreamReader(url.openStream()));
      String line = "";
      while (line != null) {
        line = br.readLine();
        contents.append(line);
      }
    } finally {
      if (br != null) {
        br.close();
      }
    }
    return contents.toString();
  }

  public List<String> getNodeList() {
    return nodeList;
  }

}
