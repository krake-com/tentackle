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
 * Statistics result for execution count and durations.
 *
 * @author harald
 */
public class StatisticsResult {

  private long count;                 // invocation count
  private TimeKeeper minDuration;     // the shortest duration
  private TimeKeeper maxDuration;     // the longest duration
  private TimeKeeper totalDuration;   // total duration


  /**
   * Creates an empty statistics result.
   */
  public StatisticsResult() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Counts invocations sharing a total duration.<br>
   * The minimum and maximum durations are updated with the average duration per invocation.
   *
   * @param duration the total duration of all invocations
   * @param count the number of invocations
   */
  public void count(TimeKeeper duration, int count) {
    if (count <= 1) {
      count(duration);
    }
    else {
      TimeKeeper singleTimeKeeper = duration.clone().divide(count);
      long nanos = singleTimeKeeper.nanos();
      if (minDuration == null || minDuration.nanos() > nanos) {
        minDuration = singleTimeKeeper;
      }
      if (maxDuration == null || maxDuration.nanos() < nanos) {
        maxDuration = singleTimeKeeper;
      }
      this.count += count;
      if (totalDuration == null) {
        totalDuration = duration.clone();
      }
      else {
        totalDuration.add(duration);
      }
    }
  }

  /**
   * Counts an invocation.
   *
   * @param duration the duration
   */
  public void count(TimeKeeper duration) {
    long nanos = duration.nanos();
    if (minDuration == null || minDuration.nanos() > nanos) {
      minDuration = duration.clone();
    }
    if (maxDuration == null || maxDuration.nanos() < nanos) {
      maxDuration = duration.clone();
    }
    count++;
    if (totalDuration == null) {
      totalDuration = duration.clone();
    }
    else {
      totalDuration.add(duration);
    }
  }

  /**
   * Gets the minimum duration.
   *
   * @return the minimum duration, null if nothing counted yet
   */
  public TimeKeeper getMinDuration() {
    return minDuration;
  }

  /**
   * Gets the maximum duration.
   *
   * @return the maximum duration, null if nothing counted yet
   */
  public TimeKeeper getMaxDuration() {
    return maxDuration;
  }

  /**
   * Gets the total duration.
   *
   * @return the total duration, null if nothing counted yet
   */
  public TimeKeeper getTotalDuration() {
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

  /**
   * Returns whether the result is valid.
   *
   * @return true if at least one invocation was counted
   */
  public boolean isValid() {
    return count > 0;
  }


  @Override
  public String toString() {
    return isValid() ?
           getMinDuration().millisToString() + " " + getMaxDuration().millisToString() + " " +
           getTotalDuration().millisToString() + " ms / " + getCount()
                     :
           "<invalid>";
  }

}
