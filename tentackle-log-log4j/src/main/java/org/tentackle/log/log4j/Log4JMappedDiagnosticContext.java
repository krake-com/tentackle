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



package org.tentackle.log.log4j;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.MDC;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.AbstractMappedDiagnosticContext;


interface Log4JMappedDiagnosticContext$Singleton {
  Log4JMappedDiagnosticContext INSTANCE = ServiceFactory.createService(
                Log4JMappedDiagnosticContext.class, Log4JMappedDiagnosticContext.class);
}


/**
 * The Log4J mdc implementation.
 *
 * @author harald
 */
@Service(Log4JMappedDiagnosticContext.class)    // defaults to self
public class Log4JMappedDiagnosticContext extends AbstractMappedDiagnosticContext {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static Log4JMappedDiagnosticContext getInstance() {
    return Log4JMappedDiagnosticContext$Singleton.INSTANCE;
  }


  @Override
  public void put(String key, String val) {
    MDC.put(key, val);
  }

  @Override
  public String get(String key) {
    return (String) MDC.get(key);
  }

  @Override
  public void remove(String key) {
    MDC.remove(key);
  }

  @Override
  public void clear() {
    MDC.clear();
  }

  @Override
  public Map<String, String> getContext() {
    @SuppressWarnings("unchecked")
    Map<String, String> map = MDC.getContext();
    if (map == null) {
      map = new HashMap<>();
    }
    return map;
  }

}
