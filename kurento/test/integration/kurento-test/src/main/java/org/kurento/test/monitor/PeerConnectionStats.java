/*
 * (C) Copyright 2016 Kurento (http://kurento.org/)
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

package org.kurento.test.monitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PeerConnectionStats extends MonitorStats {

  private Map<String, Object> stats;

  public PeerConnectionStats(Map<String, Object> stats) {
    this.stats = stats;
  }

  public Map<String, Object> getStats() {
    return stats;
  }

  public List<String> calculateHeaders() {
    return new ArrayList<>(stats.keySet());
  }

  public List<Object> calculateValues(List<String> headers) {

    List<Object> values = new ArrayList<>();
    for (String header : headers) {
      values.add(stats.get(header));
    }
    return values;
  }

}
