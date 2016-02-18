/*
 * Tentackle - http://www.tentackle.org.
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

import java.text.DecimalFormat;
import org.tentackle.common.TentackleRuntimeException;


/**
 * A duration.
 * <p>
 * The implementation is based on {@link System#nanoTime()}.
 *
 * @author harald
 */
public class Duration {

  /**
   * Format for xxxxToString().
   * <p>
   * The default format prints 3 digits after the comma.
   */
  private static final DecimalFormat DURATION_FORMAT = new DecimalFormat("#0.000");

  /**
   * String for invalid duration.
   */
  private static final String INVALID_DURATION_TEXT = "?";


  private final long start;       // starting time in nanos
  private long end;               // ending time in nanos
  private boolean durationValid;  // true if end nanos valid


  /**
   * Creates a duration.
   * <p>
   * The initial starting time is retrieved from {@link System#nanoTime()}.<br>
   * The duration is invalid as long as {@link #zero()} or {@link #end()} is invoked.
   *
   * @see #isValid()
   */
  public Duration() {
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
  public Duration(long nanos) {
    this();
    end = start + nanos;
    durationValid = true;
  }

  /**
   * Sets the duration to zero.
   */
  public void zero() {
    end = start;
    durationValid = true;
  }

  /**
   * Marks the end of a nanos.
   * <p>
   * The end is retrieved from {@link System#nanoTime()}.<br>
   * The method can be invoked more than once.
   */
  public void end() {
    end = System.nanoTime();
    durationValid = true;
  }

  /**
   * Returns whether the duration is valid.
   *
   * @return true if {@link #zero()} or {@link #end()} invoked at least once
   */
  public boolean isValid() {
    return durationValid;
  }

  /**
   * Invalidates a duration.
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
   * @param duration the nanos to add
   * @return this duration
   */
  public Duration add(Duration duration) {
    assertDurationValid();
    end += duration.nanos();
    return this;
  }

  /**
   * Subtracts another duration.
   *
   * @param duration the nanos to subtract
   * @return this duration
   */
  public Duration subtract(Duration duration) {
    assertDurationValid();
    end -= duration.nanos();
    return this;
  }


  @Override
  public String toString() {
    return isValid() ? Long.toString(nanos()) : INVALID_DURATION_TEXT;
  }

  /**
   * Returns the microseconds as a String.
   *
   * @return the microseconds with nanoseconds resolution
   */
  public String microsToString() {
    return durationToString(micros());
  }

  /**
   * Returns the milliseconds as a String.
   *
   * @return the milliseconds with microseconds resolution
   */
  public String millisToString() {
    return durationToString(millis());
  }

  /**
   * Returns the seconds as a String.
   *
   * @return the seconds with milliseconds resolution
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
      synchronized(DURATION_FORMAT) {
        return DURATION_FORMAT.format(duration);
      }
    }
    return INVALID_DURATION_TEXT;
  }


  /**
   * Asserts that end ist valid.
   * <p>
   * The end is valid if {@link #end()} has been invoked.
   */
  protected void assertDurationValid() {
    if (!durationValid) {
      throw new TentackleRuntimeException("duration not valid");
    }
  }

}
