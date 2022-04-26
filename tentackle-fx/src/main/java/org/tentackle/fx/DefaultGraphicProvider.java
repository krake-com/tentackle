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
 *
 */

package org.tentackle.fx;

import java.util.HashMap;
import java.util.Map;

/**
 * The default graphic provider for framework-related icons.<br>
 * Based on the ikonli materialdesign pack.
 *
 * @author harald
 */
@GraphicProviderService
public class DefaultGraphicProvider extends IkonliGraphicProvider {

  /**
   * Maps Tentackle symbolic names to icon font names.<br>
   * Symbolic names allows to replace the icon pack by the application without changing the framework's code.
   */
  private Map<String, String> nameMap;

  @Override
  protected String translateName(String name) {
    String transName = getNameMap().get(name);
    return transName != null ? transName : name;
  }

  /**
   * Creates the symbolic to icon-pack name map.
   *
   * @return the map
   */
  protected Map<String, String> createNameMap() {
    Map<String, String> nm = new HashMap<>();

    nm.put("about", "mdi-information-outline");
    nm.put("add", "mdi-plus");
    nm.put("browser", "mdi-information-outline");
    nm.put("cancel", "mdi-close");
    nm.put("checked", "mdi-checkbox-marked-outline");
    nm.put("close", "mdi-close-circle-outline");
    nm.put("collapse", "mdi-arrow-compress");
    nm.put("copy", "mdi-content-copy");
    nm.put("cut", "mdi-content-cut");
    nm.put("delete", "mdi-delete");
    nm.put("down", "mdi-chevron-down");
    nm.put("edit", "mdi-table-edit");
    nm.put("exit", "mdi-power");
    nm.put("expand", "mdi-arrow-expand");
    nm.put("file", "mdi-file-outline");
    nm.put("filter", "mdi-filter-outline");
    nm.put("help", "mdi-help");
    nm.put("new", "mdi-shape-rectangle-plus");
    nm.put("ok", "mdi-check");
    nm.put("password", "mdi-key");
    nm.put("preferences", "mdi-wrench");
    nm.put("print", "mdi-printer");
    nm.put("reload", "mdi-refresh");
    nm.put("save", "mdi-content-save");
    nm.put("search", "mdi-file-find");
    nm.put("security", "mdi-security");
    nm.put("session", "mdi-lan-connect");
    nm.put("subtract", "mdi-minus");
    nm.put("table", "mdi-table");
    nm.put("tree", "mdi-file-tree");
    nm.put("unchecked", "mdi-checkbox-blank-outline");
    nm.put("unknown", "mdi-help-circle-outline");
    nm.put("view", "mdi-eye");
    nm.put("up", "mdi-chevron-up");

    return nm;
  }

  private Map<String, String> getNameMap() {
    if (nameMap == null) {
      nameMap = createNameMap();
    }
    return nameMap;
  }

}
