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

package org.kurento.test.config;

import static org.kurento.test.config.TestConfiguration.SAUCELAB_KEY_PROPERTY;
import static org.kurento.test.config.TestConfiguration.SAUCELAB_USER_PROPERTY;
import static org.kurento.test.config.TestConfiguration.SELENIUM_VERSION;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.kurento.test.browser.Browser;
import org.kurento.test.browser.WebPageType;

/**
 * Browser configuration based for JSON test scenarios.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.1.0
 */
public class BrowserConfig {

  public static final String BROWSER = "browser";
  public static final String PRESENTER = "presenter";
  public static final String VIEWER = "viewer";

  private List<Map<String, BrowserInstance>> executions;

  public BrowserConfig() {
    executions = new ArrayList<>();
  }

  public List<Map<String, BrowserInstance>> getExecutions() {
    return executions;
  }

  public void setExecutions(List<Map<String, BrowserInstance>> executions) {
    this.executions = executions;
  }

  @Override
  public String toString() {
    return "BrowserConfig [executions=" + executions + "]";
  }

  public Collection<Object[]> getTestScenario() {
    Collection<Object[]> tests = new ArrayList<>();
    for (Map<String, BrowserInstance> browser : executions) {

      TestScenario test = new TestScenario();
      for (String key : browser.keySet()) {
        Browser browserClient = null;
        BrowserInstance instance = browser.get(key);

        Browser.Builder builder = new Browser.Builder().browserType(instance.getBrowserType());

        if (instance.getVideo() != null) {
          builder = builder.video(instance.getVideo());
        }
        if (instance.getInstances() > 0) {
          builder = builder.numInstances(instance.getInstances());
        }
        if (instance.getBrowserPerInstance() > 0) {
          builder = builder.browserPerInstance(instance.getBrowserPerInstance());
        }
        if (instance.getNode() != null) {
          builder = builder.node(instance.getNode());
        }
        if (instance.getLogin() != null) {
          builder = builder.login(instance.getLogin());
        }
        if (instance.getPasswd() != null) {
          builder = builder.passwd(instance.getPasswd());
        }
        if (instance.getKey() != null) {
          builder = builder.pem(instance.getKey());
        }
        if (instance.getPort() > 0) {
          builder = builder.serverPort(instance.getPort());
        }
        if (instance.isEnableScreenCapture()) {
          builder = builder.enableScreenCapture();
        }
        if (instance.getProtocol() != null) {
          builder = builder.protocol(Protocol.valueOf(instance.getProtocol().toUpperCase()));
        }
        if (instance.getPath() != null) {
          builder = builder.webPageType(WebPageType.value2WebPageType(instance.getPath()));
        }
        if (instance.getHost() != null) {
          builder = builder.host(instance.getHost());
        }
        if (instance.getParentTunnel() != null) {
          builder = builder.parentTunnel(instance.getParentTunnel());
        }
        if (instance.isAvoidProxy()) {
          builder.avoidProxy();
        }
        if (instance.getExtensions() != null) {
          builder = builder.extensions(instance.getExtensions());
        }
        if (instance.getSaucelabsUser() != null) {
          System.setProperty(SAUCELAB_USER_PROPERTY, instance.getSaucelabsUser());
        }
        if (instance.getSaucelabsKey() != null) {
          System.setProperty(SAUCELAB_KEY_PROPERTY, instance.getSaucelabsKey());
        }
        if (instance.getSeleniumVersion() != null) {
          System.setProperty(SELENIUM_VERSION, instance.getSeleniumVersion());
        }
        if (instance.isLocal()) {
          browserClient = builder.scope(BrowserScope.LOCAL).build();
        } else if (instance.isRemote()) {
          browserClient = builder.scope(BrowserScope.REMOTE).build();
        } else if (instance.isDocker()) {
          browserClient = builder.scope(BrowserScope.DOCKER).build();
        } else if (instance.isElastest()) {
          browserClient = builder.scope(BrowserScope.ELASTEST).build();
        } else if (instance.isSauceLabs()) {
          if (instance.getVersion() == null || instance.getPlatformType() == null) {
            throw new RuntimeException(
                "Platform and browser version should be configured in saucelabs tests");
          }
          browserClient = builder.scope(BrowserScope.SAUCELABS)
              .browserVersion(instance.getVersion()).platform(instance.getPlatformType()).build();
        } else {
          throw new RuntimeException("Unknown scope in JSON configuration: " + instance.getScope());
        }
        test.addBrowser(key, browserClient);
      }
      tests.add(new Object[] { test });
    }
    return tests;
  }
}
