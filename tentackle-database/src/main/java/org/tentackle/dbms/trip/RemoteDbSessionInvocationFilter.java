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

package org.tentackle.dbms.trip;

import java.lang.reflect.Method;

/**
 * A {@link RemoteDbSessionImpl} can provide a filter executed
 * before each method invocation by the {@link RemoteDbDelegateInvocationHandler}.
 */
public interface RemoteDbSessionInvocationFilter {

  /**
   * Filter a remote method invocation.
   *
   * @param proxy the object to invoke the method on
   * @param method the method to invoke
   * @param args the method arguments
   */
  void filter(Object proxy, Method method, Object[] args);

}
