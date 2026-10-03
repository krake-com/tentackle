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

package org.tentackle.log.slf4j;

import org.slf4j.MDC;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.AbstractMappedDiagnosticContext;

import java.util.HashMap;
import java.util.Map;

// holder idiom: lazy and thread-safe creation of the singleton
interface SLF4JMappedDiagnosticContextHolder {
  SLF4JMappedDiagnosticContext INSTANCE = ServiceFactory.createService(
                SLF4JMappedDiagnosticContext.class, SLF4JMappedDiagnosticContext.class);
}


/**
 * The mapped diagnostic context for SLF4J.
 * <p>
 * Delegates to SLF4J's thread-local {@link MDC}, so the keys can be referenced in the
 * backend's layout patterns (e.g. {@code %X} in Logback).<br>
 * The singleton is created via the {@link ServiceFactory} and can thus be replaced by an
 * application-specific {@code @Service}.
 *
 * @author harald
 */
@Service(SLF4JMappedDiagnosticContext.class)    // defaults to self
public class SLF4JMappedDiagnosticContext extends AbstractMappedDiagnosticContext {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static SLF4JMappedDiagnosticContext getInstance() {
    return SLF4JMappedDiagnosticContextHolder.INSTANCE;
  }


  /**
   * Creates the MDC for SLF4J.
   */
  public SLF4JMappedDiagnosticContext() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void put(String key, String val) {
    MDC.put(key, val);
  }

  @Override
  public String get(String key) {
    return MDC.get(key);
  }

  @Override
  public void remove(String key) {
    MDC.remove(key);
  }

  @Override
  public void clear() {
    MDC.clear();
  }

  /**
   * {@inheritDoc}
   * <p>
   * SLF4J returns a copy of the current thread's context.
   * If there is none, an empty mutable map is returned.
   */
  @Override
  public Map<String, String> getContext() {
    Map<String, String> map = MDC.getCopyOfContextMap();
    if (map == null) {
      map = new HashMap<>();
    }
    return map;
  }

}
