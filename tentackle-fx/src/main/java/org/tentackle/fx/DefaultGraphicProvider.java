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
   * Creates the graphic provider.
   */
  public DefaultGraphicProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Maps Tentackle symbolic names to icon font names.<br>
   * Symbolic names allows to replace the icon pack by the application without changing the framework's code.
   */
  private Map<String, String> nameMap;

  /**
   * Gets the symbolic name map.
   *
   * @return the map of symbolic to icon pack names
   */
  protected Map<String, String> getNameMap() {
    if (nameMap == null) {
      nameMap = createNameMap();
    }
    return nameMap;
  }

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

    nm.put("about", "mdi2i-information-outline");
    nm.put("add", "mdi2p-plus");
    nm.put("browser", "mdi2i-information-outline");
    nm.put("cancel", "mdi2c-cancel");
    nm.put("checked", "mdi2c-checkbox-marked");
    nm.put("close", "mdi2c-close");
    nm.put("collapse", "mdi2a-arrow-collapse-vertical");
    nm.put("copy", "mdi2c-content-copy");
    nm.put("cut", "mdi2c-content-cut");
    nm.put("delete", "mdi2d-delete");
    nm.put("down", "mdi2c-chevron-down");
    nm.put("edit", "mdi2f-file-document-edit-outline");
    nm.put("exit", "mdi2l-logout");
    nm.put("expand", "mdi2a-arrow-expand-vertical");
    nm.put("file", "mdi2f-file-outline");
    nm.put("filter", "mdi2f-filter-outline");
    nm.put("generate", "mdi2c-cog");
    nm.put("help", "mdi2h-help");
    nm.put("hide", "mdi2e-eye-off");
    nm.put("id", "mdi2i-id-card");
    nm.put("login", "mdi2l-login");
    nm.put("new", "mdi2s-shape-rectangle-plus");
    nm.put("object", "mdi2a-alpha-o-box");
    nm.put("ok", "mdi2c-check");
    nm.put("password", "mdi2k-key");
    nm.put("preferences", "mdi2w-wrench");
    nm.put("print", "mdi2p-printer");
    nm.put("run", "mdi2p-play-circle-outline");
    nm.put("reload", "mdi2r-refresh");
    nm.put("save", "mdi2c-content-save");
    nm.put("script", "mdi2s-script-text-outline");
    nm.put("search", "mdi2f-file-find");
    nm.put("security", "mdi2s-security");
    nm.put("session", "mdi2l-lan-connect");
    nm.put("stop", "mdi2t-timer-stop-outline");
    nm.put("subtract", "mdi2m-minus");
    nm.put("table", "mdi2t-table");
    nm.put("theme", "mdi2t-theme-light-dark");
    nm.put("tree", "mdi2f-file-tree");
    nm.put("unchecked", "mdi2c-checkbox-blank-outline");
    nm.put("unknown", "mdi2h-help-circle-outline");
    nm.put("view", "mdi2e-eye");
    nm.put("up", "mdi2c-chevron-up");

    return nm;
  }

}
