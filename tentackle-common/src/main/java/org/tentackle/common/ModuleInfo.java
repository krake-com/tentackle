/*
 * Tentackle - a framework for java desktop applications
 * http://www.tentackle.org
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

package org.tentackle.common;

import java.lang.module.ModuleDescriptor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Holds aligned dependency information about a module.
 * <p>
 * This class has a natural ordering that is inconsistent with equals!
 *
 * @author harald
 */
public class ModuleInfo implements Comparable<ModuleInfo> {

  private final String name;
  private final Set<String> requiredModuleNames;
  private Set<String> allRequiredModuleNames;

  /**
   * Creates a module info.
   *
   * @param module the jpms module
   */
  public ModuleInfo(Module module) {
    name = module.getName();
    requiredModuleNames = new HashSet<>();
    for (ModuleDescriptor.Requires req : module.getDescriptor().requires()) {
      requiredModuleNames.add(req.name());
    }
  }

  /**
   * Gets the module name.
   *
   * @return the name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the names of the required modules.
   *
   * @return the directly required names
   */
  public Set<String> getRequiredModuleNames() {
    return requiredModuleNames;
  }

  /**
   * Gets all required module names.
   *
   * @return the direct and transitive required modules
   */
  public Set<String> getAllRequiredModuleNames(Map<String, ModuleInfo> allModules) {
    if (allRequiredModuleNames == null) {
      allRequiredModuleNames = new HashSet<>();
      for (Iterator<String> iter = requiredModuleNames.iterator(); iter.hasNext();) {
        String subName = iter.next();
        ModuleInfo subModule = allModules.get(subName);
        if (subModule != null) {
          allRequiredModuleNames.add(subName);
          allRequiredModuleNames.addAll(subModule.getAllRequiredModuleNames(allModules));
        }
        else {
          iter.remove();
        }
      }
    }
    return allRequiredModuleNames;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(name).append(" [");
    boolean needComma = false;
    for (String subName : requiredModuleNames) {
      if (needComma) {
        buf.append(", ");
      }
      else {
        needComma = true;
      }
      buf.append(subName);
    }
    buf.append(']');
    return buf.toString();
  }

  @Override
  public int hashCode() {
    int hash = 7;
    hash = 79 * hash + Objects.hashCode(this.name);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ModuleInfo other = (ModuleInfo) obj;
    return Objects.equals(this.name, other.name);
  }

  @Override
  public int compareTo(ModuleInfo o) {
    if (allRequiredModuleNames != null) {
      if (allRequiredModuleNames.contains(o.name)) {
        return 1;
      }
      if (o.allRequiredModuleNames.contains(name)) {
        return -1;
      }
    }
    return 0;
  }

}
