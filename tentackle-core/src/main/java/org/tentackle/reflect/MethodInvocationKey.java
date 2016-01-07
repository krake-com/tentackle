/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.reflect;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import org.tentackle.misc.Identifiable;

/**
 * A key to track method invocations.
 *
 * @author harald
 */
public class MethodInvocationKey {

  private final Method method;            // the method
  private final Object object;            // the object the method is invoked on, null if static
  private final Object[] arguments;       // effective method args


  /**
   * Creates a method key.
   *
   * @param method the method
   * @param object the object the method is invoked on, null if object doesnt matter (e.g. static methods)
   * @param arguments the argument values
   */
  public MethodInvocationKey(Method method, Object object, Object... arguments) {
    this.method = method;
    this.object = object;
    this.arguments = arguments;
  }


  /**
   * Gets the method.
   *
   * @return the method
   */
  public Method getMethod() {
    return method;
  }

  /**
   * Gets the object the method is invoked on.
   *
   * @return the object, null if object doesnt matter
   */
  public Object getObject() {
    return object;
  }

  /**
   * Gets the argument values.
   *
   * @return the arguments
   */
  public Object[] getArguments() {
    return arguments;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final MethodInvocationKey other = (MethodInvocationKey) obj;
    if (!Objects.equals(this.method, other.method)) {
      return false;
    }
    if (!Objects.equals(this.object, other.object)) {
      return false;
    }
    if (!Arrays.deepEquals(this.arguments, other.arguments)) {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 47 * hash + Objects.hashCode(this.method);
    hash = 47 * hash + Objects.hashCode(this.object);
    hash = 47 * hash + Arrays.deepHashCode(this.arguments);
    return hash;
  }



  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(method);
    buf.append(" on ");
    if (object == null) {
      buf.append("<null>");
    }
    else  {
      buf.append('{');
      buf.append(object instanceof Identifiable ? ((Identifiable) object).toGenericString() : object.toString());
      buf.append('}');
    }
    buf.append(" for {");
    buf.append(Arrays.toString(arguments));
    buf.append("}");
    return buf.toString();
  }

}
