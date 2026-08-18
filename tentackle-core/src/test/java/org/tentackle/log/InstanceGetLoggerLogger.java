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

/**
 * A logger declaring {@code getLogger(String)} as an <em>instance</em> method.
 * <p>
 * There is no way to use such a method as a factory, so {@link DefaultLoggerFactory} must ignore it
 * and fall back to the {@link DefaultLogger}, rather than invoking it with a null receiver.
 *
 * @author harald
 */
public class InstanceGetLoggerLogger extends DelegatingTestLogger {

  /**
   * Creates a logger.
   */
  public InstanceGetLoggerLogger() {
    super(InstanceGetLoggerLogger.class.getName());
  }

  /**
   * Deliberately not static.
   *
   * @param name the logger name
   * @return this logger
   */
  public InstanceGetLoggerLogger getLogger(String name) {
    return this;
  }

}
