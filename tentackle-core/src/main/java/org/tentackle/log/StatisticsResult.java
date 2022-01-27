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

import org.tentackle.misc.TimeKeeper;

/**
 * Method statistics result.
 *
 * @author harald
 */
public class StatisticsResult {

  private long count;               // invocation count
  private TimeKeeper minTimeKeeper;     // the shorted duration
  private TimeKeeper maxTimeKeeper;     // the longest duration
  private TimeKeeper totalTimeKeeper;   // total duration


  /**
   * Count invocations.
   *
   * @param count the number of invocations
   * @param timeKeeper the duration
   */
  public void count(TimeKeeper timeKeeper, int count) {
    if (count <= 1) {
      count(timeKeeper);
    }
    else {
      TimeKeeper singleTimeKeeper = timeKeeper.clone().divide(count);
      long nanos = singleTimeKeeper.nanos() / count;
      if (minTimeKeeper == null || minTimeKeeper.nanos() > nanos) {
        minTimeKeeper = singleTimeKeeper;
      }
      if (maxTimeKeeper == null || maxTimeKeeper.nanos() < nanos) {
        maxTimeKeeper = singleTimeKeeper;
      }
      this.count += count;
      if (totalTimeKeeper == null) {
        totalTimeKeeper = timeKeeper.clone();
      }
      else {
        totalTimeKeeper.add(timeKeeper);
      }
    }
  }

  /**
   * Count an invocation.
   *
   * @param timeKeeper the duration
   */
  public void count(TimeKeeper timeKeeper) {
    long nanos = timeKeeper.nanos();
    if (minTimeKeeper == null || minTimeKeeper.nanos() > nanos) {
      minTimeKeeper = timeKeeper.clone();
    }
    if (maxTimeKeeper == null || maxTimeKeeper.nanos() < nanos) {
      maxTimeKeeper = timeKeeper.clone();
    }
    count++;
    if (totalTimeKeeper == null) {
      totalTimeKeeper = timeKeeper.clone();
    }
    else {
      totalTimeKeeper.add(timeKeeper);
    }
  }

  /**
   * Gets the minimum duration.
   *
   * @return the minimum duration.
   */
  public TimeKeeper getMinDuration() {
    return minTimeKeeper;
  }

  /**
   * Gets the maximum durations.
   *
   * @return the maximum durations
   */
  public TimeKeeper getMaxDuration() {
    return maxTimeKeeper;
  }

  /**
   * Gets the total duration.
   *
   * @return the total duration
   */
  public TimeKeeper getTotalDuration() {
    return totalTimeKeeper;
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
