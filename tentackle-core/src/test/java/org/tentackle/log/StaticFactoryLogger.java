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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A logger providing a static {@code getLogger(String)} factory method.
 * <p>
 * {@link DefaultLoggerFactory} must prefer that method over a constructor. The constructor is
 * private on purpose, so that a factory falling back to it wouldn't work at all.
 *
 * @author harald
 */
public class StaticFactoryLogger extends DelegatingTestLogger {

  private static final Map<String, StaticFactoryLogger> LOGGERS = new ConcurrentHashMap<>();

  /**
   * Gets the logger for the given name.
   *
   * @param name the logger name
   * @return the logger, re-used for equal names
   */
  public static StaticFactoryLogger getLogger(String name) {
    return LOGGERS.computeIfAbsent(name, StaticFactoryLogger::new);
  }

  private StaticFactoryLogger(String name) {
    super(name);
  }

}
