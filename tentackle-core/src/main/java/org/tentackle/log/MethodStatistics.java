/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.log;

import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.reflect.ReflectionHelper;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Method statistics collector.<br>
 * Counts the invocations and durations per method and serviced class. Thread-safe.
 *
 * @author harald
 */
public class MethodStatistics {

  /**
   * The logger for this class.
   */
  private static final Logger LOGGER = Logger.get(MethodStatistics.class);

  // the statistics map.
  private final Map<MethodStatisticsKey, StatisticsResult> stats;

  /**
   * Creates a new statistics object.
   */
  public MethodStatistics() {
    this.stats = new HashMap<>();
  }

  /**
   * Counts the invocation of a delegate method.
   *
   * @param method the method invoked
   * @param servicedClass the serviced class, null if {@code method.getDeclaringClass()}
   * @param timeKeeper execution duration
   */
  public synchronized void countMethodInvocation(Method method, Class<?> servicedClass, TimeKeeper timeKeeper) {
    MethodStatisticsKey key = new MethodStatisticsKey(method, servicedClass);
    StatisticsResult result = stats.computeIfAbsent(key, k -> new StatisticsResult());
    result.count(timeKeeper);
  }

  /**
   * Counts the invocation of a delegate method.
   *
   * @param method the method invoked
   * @param timeKeeper execution duration
   */
  public void countMethodInvocation(Method method, TimeKeeper timeKeeper) {
    countMethodInvocation(method, null, timeKeeper);
  }

  /**
   * Logs the statistics.<br>
   * Each line shows the minimum, maximum and total duration in milliseconds and the number of invocations.
   *
   * @param title the title, null if default
   * @param level the logging level
   * @param tag intro logging tag
   * @param clear true if clear statistics after dump
   */
  public synchronized void logStatistics(String title, Logger.Level level, String tag, boolean clear) {
    if (!stats.isEmpty()) {
      if (LOGGER.isLoggable(level)) {
        try {
          StringBuilder buf = new StringBuilder();
          if (title == null) {
            title = "Invocation Statistics";
          }
          buf.append(title).append(tag).append(FormatHelper.formatLocalDateTime(LocalDateTime.now()));
          for (Map.Entry<MethodStatisticsKey, StatisticsResult> entry : stats.entrySet()) {
            StatisticsResult result = entry.getValue();
            if (result.isValid()) {
              MethodStatisticsKey key = entry.getKey();
              buf.append('\n').append(tag)
                 .append(result).append(" x ")
                 .append(ReflectionHelper.methodToString(key.getMethod()));
              if (!key.getServicedClass().equals(key.getMethod().getDeclaringClass())) {
                buf.append(" for ").append(key.getServicedClass().getSimpleName());
              }
            }
          }
          if (clear) {
            buf.append("\n    (cleared)");
          }
          LOGGER.log(level, buf.toString());
        }
        catch (RuntimeException rex) {
          LOGGER.severe("cannot log method statistics", rex);
        }
      }
      if (clear) {
        stats.clear();
      }
    }
  }

}
