/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.misc;

import org.tentackle.common.TentackleRuntimeException;

import java.text.DecimalFormat;

/**
 * A duration.
 * <p>
 * The implementation is based on {@link System#nanoTime()}.
 * <p>
 * Notice: despite its fluent API, {@link TimeKeeper} is not immutable, since it measures time.
 *
 * @author harald
 */
public class TimeKeeper implements Cloneable, Comparable<TimeKeeper> {

  /**
   * Format for the {@code xxxToString()} methods.
   * <p>
   * The default format prints 3 fraction digits.
   */
  private static final ThreadLocal<DecimalFormat> DURATION_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#0.000"));

  /**
   * String for invalid duration.
   */
  private static final String INVALID_DURATION_TEXT = "?";


  private long start;             // starting time in nanos
  private long end;               // ending time in nanos
  private boolean durationValid;  // true if end nanos valid


  /**
   * Creates a duration.
   * <p>
   * The initial starting time is retrieved from {@link System#nanoTime()}.<br>
   * The duration is invalid until {@link #end()} is invoked.
   *
   * @see #isValid()
   */
  public TimeKeeper() {
    start = System.nanoTime();
  }

  /**
   * Creates a duration with given nanoseconds.
   * <p>
   * The duration is valid.
   *
   * @param nanos the nanoseconds
   * @see #isValid()
   */
  public TimeKeeper(long nanos) {
    this();
    end = start + nanos;
    durationValid = true;
  }


  @Override
  public TimeKeeper clone() {
    try {
      return (TimeKeeper) super.clone();
    }
    catch (CloneNotSupportedException ex) {
      throw new TentackleRuntimeException(ex);    // this shouldn't happen, since we are Cloneable
    }
  }


  /**
   * Resets the duration.<br>
   * Restarts the time measurement. The duration is invalid until {@link #end()} is invoked.
   *
   * @return the duration itself (for fluent style)
   */
  public TimeKeeper reset() {
    end = start = System.nanoTime();
    durationValid = false;
    return this;
  }

  /**
   * Marks the end of a duration.
   * <p>
   * The end is retrieved from {@link System#nanoTime()}.<br>
   * The method can be invoked more than once.
   *
   * @return the duration itself (for fluent style)
   */
  public TimeKeeper end() {
    end = System.nanoTime();
    durationValid = true;
    return this;
  }

  /**
   * Returns whether the duration is valid.
   *
   * @return true if {@link #end()} invoked at least once
   */
  public boolean isValid() {
    return durationValid;
  }

  /**
   * Invalidates the duration.
   */
  public void invalidate() {
    durationValid = false;
  }

  /**
   * Gets the duration in nanoseconds.
   *
   * @return the elapsed time in nanoseconds
   * @throws TentackleRuntimeException if {@link #end()} not invoked
   */
  public long nanos() {
    assertDurationValid();
    return end - start;
  }

  /**
   * Gets the duration in microseconds.
   *
   * @return the elapsed time in microseconds
   * @throws TentackleRuntimeException if {@link #end()} not invoked
   */
  public double micros() {
    return ((double) nanos()) / 1000.0;
  }

  /**
   * Gets the duration in milliseconds.
   *
   * @return the elapsed time in milliseconds
   * @throws TentackleRuntimeException if {@link #end()} not invoked
   */
  public double millis() {
    return ((double) nanos()) / 1000000.0;
  }

  /**
   * Gets the duration in seconds.
   *
   * @return the elapsed time in seconds
   * @throws TentackleRuntimeException if {@link #end()} not invoked
   */
  public double seconds() {
    return ((double) nanos()) / 1000000000.0;
  }

  /**
   * Adds another duration.
   *
   * @param timeKeeper the duration to add
   * @return this duration
   * @throws TentackleRuntimeException if one of the durations is not valid
   */
  public TimeKeeper add(TimeKeeper timeKeeper) {
    assertDurationValid();
    end += timeKeeper.nanos();
    return this;
  }

  /**
   * Subtracts another duration.
   *
   * @param timeKeeper the duration to subtract
   * @return this duration
   * @throws TentackleRuntimeException if one of the durations is not valid
   */
  public TimeKeeper subtract(TimeKeeper timeKeeper) {
    assertDurationValid();
    end -= timeKeeper.nanos();
    return this;
  }

  /**
   * Multiplies this duration.
   *
   * @param multiplier the multiplier (must be &ge; 0)
   * @return this duration
   * @throws TentackleRuntimeException if the duration is not valid
   * @throws IllegalArgumentException if the multiplier is negative
   */
  public TimeKeeper multiply(long multiplier) {
    assertDurationValid();
    if (multiplier < 0) {
      throw new IllegalArgumentException("multiplier must be >= 0");
    }
    end = start + ((end - start) * multiplier);
    return this;
  }

  /**
   * Divides this duration.
   *
   * @param divisor the divisor (must be &gt; 0)
   * @return this duration
   * @throws TentackleRuntimeException if the duration is not valid
   * @throws IllegalArgumentException if the divisor is not positive
   */
  public TimeKeeper divide(long divisor) {
    assertDurationValid();
    if (divisor <= 0) {
      throw new IllegalArgumentException("divisor must be > 0");
    }
    end = start + ((end - start) / divisor);
    return this;
  }

  /**
   * Compares the durations.<br>
   * Invalid durations are treated as 0.
   *
   * @param o the other duration
   * @return the comparison result
   */
  @Override
  public int compareTo(TimeKeeper o) {
    // don't throw an exception if duration not valid. Assume duration 0 instead.
    long span = durationValid ? end - start : 0;
    long otherSpan = o.durationValid ? o.end - o.start : 0;
    return Long.compare(span, otherSpan);
  }

  /**
   * Returns the duration in nanoseconds as a string.
   *
   * @return the nanoseconds, {@code "?"} if the duration is not valid
   */
  @Override
  public String toString() {
    return isValid() ? Long.toString(nanos()) : INVALID_DURATION_TEXT;
  }

  /**
   * Returns the microseconds as a string.
   *
   * @return the microseconds with nanoseconds resolution, {@code "?"} if the duration is not valid
   */
  public String microsToString() {
    return durationToString(micros());
  }

  /**
   * Returns the milliseconds as a string.
   *
   * @return the milliseconds with microseconds resolution, {@code "?"} if the duration is not valid
   */
  public String millisToString() {
    return durationToString(millis());
  }

  /**
   * Returns the seconds as a string.
   *
   * @return the seconds with milliseconds resolution, {@code "?"} if the duration is not valid
   */
  public String secondsToString() {
    return durationToString(seconds());
  }


  /**
   * Formats a duration.
   *
   * @param duration the value representing a duration
   * @return the formatted string
   */
  protected String durationToString(double duration) {
    if (isValid()) {
      return DURATION_FORMAT.get().format(duration);
    }
    return INVALID_DURATION_TEXT;
  }


  /**
   * Asserts that the duration is valid.
   * <p>
   * The duration is valid if {@link #end()} has been invoked.
   *
   * @throws TentackleRuntimeException if the duration is not valid
   */
  protected void assertDurationValid() {
    if (!durationValid) {
      throw new TentackleRuntimeException("duration not valid");
    }
  }

}
