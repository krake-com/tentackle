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

package org.tentackle.log.log4j2v;

import org.apache.logging.log4j.ThreadContext;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.AbstractMappedDiagnosticContext;

import java.util.HashMap;
import java.util.Map;


// holder idiom: lazy and thread-safe creation of the singleton
interface Log4J2MappedDiagnosticContextHolder {
  Log4J2MappedDiagnosticContext INSTANCE = ServiceFactory.createService(
                Log4J2MappedDiagnosticContext.class, Log4J2MappedDiagnosticContext.class);
}


/**
 * The mapped diagnostic context for Log4J 2.
 * <p>
 * Delegates to Log4J's thread-local {@link ThreadContext}, so the keys can be referenced
 * in Log4J layout patterns via {@code %X}.<br>
 * The singleton is created via the {@link ServiceFactory} and can thus be replaced by an
 * application-specific {@code @Service}.
 *
 * @author harald
 */
@Service(Log4J2MappedDiagnosticContext.class)    // defaults to self
public class Log4J2MappedDiagnosticContext extends AbstractMappedDiagnosticContext {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static Log4J2MappedDiagnosticContext getInstance() {
    return Log4J2MappedDiagnosticContextHolder.INSTANCE;
  }


  /**
   * Creates the MDC for Log4J2.
   */
  public Log4J2MappedDiagnosticContext() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void put(String key, String val) {
    ThreadContext.put(key, val);
  }

  @Override
  public String get(String key) {
    return ThreadContext.get(key);
  }

  @Override
  public void remove(String key) {
    ThreadContext.remove(key);
  }

  @Override
  public void clear() {
    ThreadContext.clearMap();
  }

  /**
   * {@inheritDoc}
   * <p>
   * Log4J returns a copy of the current thread's context.
   */
  @Override
  public Map<String, String> getContext() {
    Map<String, String> map = ThreadContext.getContext();
    if (map == null) {
      map = new HashMap<>();
    }
    return map;
  }

}
