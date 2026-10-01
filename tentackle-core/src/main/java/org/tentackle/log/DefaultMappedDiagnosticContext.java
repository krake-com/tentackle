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

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.misc.InheritableThreadLocalHashTable;

import java.util.Map;


interface DefaultMappedDiagnosticContextHolder {
  DefaultMappedDiagnosticContext INSTANCE = ServiceFactory.createService(
              DefaultMappedDiagnosticContext.class, DefaultMappedDiagnosticContext.class);
}


/**
 * The MDC implementation for the java util logging.<br>
 * Although MDC is not supported by the Java logging API and its formatters,
 * this implementation keeps a thread-local map of properties
 * so that there's no difference to the application concerning
 * the effective logging provider.
 * <p>
 * Child threads get a copy of the parent's context. Neither keys nor values may be null.
 *
 * @author harald
 */
@Service(DefaultMappedDiagnosticContext.class)    // defaults to self
public class DefaultMappedDiagnosticContext extends AbstractMappedDiagnosticContext {

  /**
   * Gets the singleton.
   *
   * @return the singleton
   */
  public static DefaultMappedDiagnosticContext getInstance() {
    return DefaultMappedDiagnosticContextHolder.INSTANCE;
  }


  /**
   * map per thread.
   */
  private final InheritableThreadLocalHashTable<String, String> tlm = new InheritableThreadLocalHashTable<>();


  /**
   * Creates the MDC for java-util logging.
   */
  public DefaultMappedDiagnosticContext() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public Map<String, String> getContext() {
    return tlm.get();
  }

  @Override
  public void put(String key, String val){
    getContext().put(key, val);
  }

  @Override
  public String get(String key){
    return getContext().get(key);
  }

  @Override
  public void remove(String key){
    getContext().remove(key);
  }

  @Override
  public void clear() {
    getContext().clear();
  }

}
