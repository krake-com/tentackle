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

package org.tentackle.script;

import org.tentackle.reflect.EffectiveClassProvider;

import java.util.Objects;
import java.util.Set;

/**
 * Script variable.<br>
 * Variables are identified by their name, i.e. equality refers to the name only.
 *
 * @author harald
 */
public class ScriptVariable {

  private final String name;      // the variable name (never null)
  private Object value;           // the value

  /**
   * Creates a {@link ScriptVariable}.
   *
   * @param name the variable's name
   * @param value its value
   * @throws NullPointerException if name is null
   */
  public ScriptVariable(String name, Object value) {
    this.name = Objects.requireNonNull(name);
    this.value = value;
  }

  /**
   * Creates a {@link ScriptVariable} without value.
   *
   * @param name the variable's name
   */
  public ScriptVariable(String name) {
    this(name, null);
  }

  /**
   * Gets the variable's name.
   *
   * @return the variable's name, never null
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the variable's value.
   *
   * @return the value
   */
  public Object getValue() {
    return value;
  }

  /**
   * Sets the variable's value.
   *
   * @param value the new value
   */
  public void setValue(Object value) {
    this.value = value;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ScriptVariable that = (ScriptVariable) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * Returns the string of a list of variables.
   *
   * @param variables the list of variables
   * @return the string representation
   */
  public static String variablesToString(Set<ScriptVariable> variables) {
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
