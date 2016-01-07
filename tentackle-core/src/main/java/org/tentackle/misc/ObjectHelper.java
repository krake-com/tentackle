/**
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


package org.tentackle.misc;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;


/**
 * Generic helper methods applicable to all kinds of objects.
 *
 * @author harald
 */
public final class ObjectHelper {

  /**
   * Clones an object.<br>
   * Useful to clone any object, without knowing whether it is cloneable or not.
   * The method is implemented by using reflection.
   *
   * @param obj the object to clone
   * @return the cloned object
   * @throws CloneNotSupportedException if object is not cloneable
   */
  public static Object clone(Object obj) throws CloneNotSupportedException {
    Object cobj = null;     // cloned object
    if (obj != null) {
      if (obj instanceof Cloneable) {
        try {
          // find the clone() method
          Method m = obj.getClass().getMethod("clone");
          int mods = m.getModifiers();
          if (!Modifier.isPublic(mods)) {
            throw new CloneNotSupportedException("clone() is not public");
          }
          if (Modifier.isStatic(mods)) {
            throw new CloneNotSupportedException("clone() is static");
          }
          try {
            // ok, let's clone...
            cobj = m.invoke(obj);
          }
          catch (Exception ex) {
            throw new CloneNotSupportedException("clone() failed: " + ex.getMessage());
          }
        }
        catch (SecurityException ex) {
          throw new CloneNotSupportedException("clone() inhibited by security manager: " + ex.getMessage());
        }
        catch (NoSuchMethodException ex) {
          throw new CloneNotSupportedException(ex.getMessage());
        }
      }
      else  {
        throw new CloneNotSupportedException("object does not implement the Cloneable interface");
      }
    }
    return cobj;
  }

  /**
   * prevent instantiation.
   */
  private ObjectHelper() { }

}
