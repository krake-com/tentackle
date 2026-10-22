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

import org.tentackle.log.Logger.Level;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.reflect.AbstractInterceptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implementation for the {@link Log} interception.<br>
 * Logs the execution time of the intercepted method.
 *
 * @author harald
 */
public final class LogInterceptor extends AbstractInterceptor {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = Logger.get(LogInterceptor.class);

  private Level level;   // the logging level

  /**
   * Creates the {@link Log} interceptor.
   */
  public LogInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    level = ((Log) annotation).value();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args, Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    TimeKeeper timeKeeper = null;
    if (LOGGER.isLoggable(level)) {
      timeKeeper = new TimeKeeper();
    }
    Object value = method.invoke(proxy, args);
    if (timeKeeper != null) {
      timeKeeper.end();
      LOGGER.log(level, orgMethod.toGenericString() + " took " + timeKeeper.millisToString() + "ms");
    }
    return value;
  }

}
