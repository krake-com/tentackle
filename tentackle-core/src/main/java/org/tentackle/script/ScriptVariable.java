/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.script;

import org.tentackle.reflect.EffectiveClassProvider;

/**
 * Script variable.
 *
 * @author harald
 */
public class ScriptVariable {

  private static final long serialVersionUID = 7698497385959487231L;


  private final String name;     // the variable name
  private final Object value;    // the value

  /**
   * Creates a {@link ScriptVariable}.
   *
   * @param name the variable's name
   * @param value its value
   */
  public ScriptVariable(String name, Object value) {
    this.name = name;
    this.value = value;
  }

  /**
   * @return the variable's name
   */
  public String getName() {
    return name;
  }

  /**
   * @return the value
   */
  public Object getValue() {
    return value;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getName());
    buf.append("=");
    Object val = getValue();
    if (val == null) {
      buf.append("<null>");
    }
    else {
      buf.append("'");
      buf.append(val);
      buf.append("'(");
      buf.append(EffectiveClassProvider.getEffectiveClass(val).getName());
      buf.append(")");
    }
    return buf.toString();
  }


  /**
   * Returns the string of a list of variables.
   *
   * @param variables the list of variables
   * @return the string representation
   */
  public static String variablesToString(ScriptVariable... variables) {
    StringBuilder args = new StringBuilder();
    if (variables != null) {
      boolean needComma = false;
      for (ScriptVariable variable : variables) {
        if (needComma) {
          args.append(", ");
        }
        else {
          needComma = true;
        }
        args.append(variable);
      }
    }
    return args.toString();
  }

}
