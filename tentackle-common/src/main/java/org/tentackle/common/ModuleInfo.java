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
import java.util.Set;

/**
 * Holds information about a module.
 *
 * @author harald
 */
public final class ModuleInfo {

  private final ModuleHook hook;                      // the module hook implementation
  private final String urlPath;                       // the path of the source URL
  private final Module module;                        // the module name
  private final Set<String> requiredModuleNames;      // the required module names

  private int ordinal;                                // the ordinal, >0 if sorted
  private boolean visited;                            // true if visited by sorter

  /**
   * Creates a module info.
   *
   * @param hook the module hook
   */
  public ModuleInfo(ModuleHook hook) {
    this.hook = hook;
    this.urlPath = hook.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
    this.module = hook.getClass().getModule();
    if (module == null || !module.isNamed()) {
      throw new TentackleRuntimeException(hook.getClass() + " does not belong to a named module");
    }
    requiredModuleNames = new HashSet<>();
    for (ModuleDescriptor.Requires req : module.getDescriptor().requires()) {
      requiredModuleNames.add(req.name());
    }
  }

  /**
   * Gets the module hook.
   *
   * @return the hook
   */
  public ModuleHook getHook() {
    return hook;
  }

  /**
   * Gets the URL path of the hook's module.
   *
   * @return the url path
   */
  public String getUrlPath() {
    return urlPath;
  }

  /**
   * Gets the module.
   *
   * @return the module
   */
  public Module getModule() {
    return module;
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
   * Gets the module ordinal.<br>
   * Higher values come first in module order.
   *
   * @return the ordinal
   */
  public int getOrdinal() {
    return ordinal;
  }

  /**
   * Sets the ordinal.<br>
   * Higher values come first in module order.
   *
   * @param ordinal the ordinal
   */
  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  /**
   * Marks the node as visited.<br>
   * Package scope. For dependency sorting.
   *
   * @param visited true if visited
   */
  void setVisited(boolean visited) {
    this.visited = visited;
  }

  /**
   * Returns whether node has been visited.<br>
   * Package scope. For dependency sorting.
   *
   * @return true if visited
   */
  boolean isVisited() {
    return visited;
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(module.getName()).append(" [");
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

}
