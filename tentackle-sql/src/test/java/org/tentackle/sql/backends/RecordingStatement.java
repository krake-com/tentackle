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

package org.tentackle.sql.backends;

import org.tentackle.sql.BackendPreparedStatement;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Records the ints bound to a {@link BackendPreparedStatement} and forwards them to a real statement, if given.
 *
 * @author harald
 */
class RecordingStatement implements InvocationHandler {

  private final PreparedStatement stmt;

  /** the values bound via setInt in the order of invocation. */
  final List<Integer> values = new ArrayList<>();

  /**
   * Creates a recording statement.
   *
   * @param stmt the statement to forward to, null if record only
   */
  RecordingStatement(PreparedStatement stmt) {
    this.stmt = stmt;
  }

  /**
   * Creates the proxy. {@link BackendPreparedStatement} is far too large to implement by hand
   * and only {@code setInt} is of interest here.
   *
   * @return the proxy
   */
  BackendPreparedStatement proxy() {
    return (BackendPreparedStatement) Proxy.newProxyInstance(
        BackendPreparedStatement.class.getClassLoader(),
        new Class<?>[] { BackendPreparedStatement.class }, this);
  }

  @Override
  public Object invoke(Object p, Method method, Object[] args) throws Throwable {
    if ("setInt".equals(method.getName())) {
      int index = (Integer) args[0];
      int value = (Integer) args[1];
      values.add(value);
      if (stmt != null) {
        stmt.setInt(index, value);
      }
    }
    return null;
  }

}
