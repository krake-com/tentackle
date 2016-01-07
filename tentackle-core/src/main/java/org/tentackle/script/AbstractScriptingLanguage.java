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

import java.util.Objects;

/**
 * Common code for scripting languages.
 *
 * @author harald
 */
public abstract class AbstractScriptingLanguage implements ScriptingLanguage {

  @Override
  public String createLocalVariableReference(String name) {
    return name;
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 47 * hash + Objects.hashCode(getName());
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AbstractScriptingLanguage other = (AbstractScriptingLanguage) obj;
    return Objects.equals(getName(), other.getName());
  }

}
