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

import org.tentackle.misc.Duration;

/**
 * Method statistics result.
 *
 * @author harald
 */
public class StatisticsResult {

  private long count;               // invocation count
  private Duration minDuration;     // the shorted duration
  private Duration maxDuration;     // the longest duration
  private Duration totalDuration;   // total duration


  /**
   * Count an invocation.
   *
   * @param duration the duration
   */
  public void count(Duration duration) {
    if (minDuration == null || minDuration.nanos() > duration.nanos()) {
      minDuration = duration;
    }
    if (maxDuration == null || maxDuration.nanos() < duration.nanos()) {
      maxDuration = duration;
    }
    count++;
    if (totalDuration == null) {
      totalDuration = new Duration(duration.nanos());
    }
    else {
      totalDuration.add(duration);
    }
  }

  /**
   * Gets the minimum duration.
   *
   * @return the minimum duration.
   */
  public Duration getMinDuration() {
    return minDuration;
  }

  /**
   * Gets the maximum durations.
   *
   * @return the maximum durations
   */
  public Duration getMaxDuration() {
    return maxDuration;
  }

  /**
   * Gets the total duration.
   *
   * @return the total duration
   */
  public Duration getTotalDuration() {
    return totalDuration;
  }

  /**
   * Gets the number of invocations.
   *
   * @return the number of invocations
   */
  public long getCount() {
    return count;
  }

}
