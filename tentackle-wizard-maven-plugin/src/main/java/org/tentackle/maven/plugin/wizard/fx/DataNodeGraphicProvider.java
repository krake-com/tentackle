/*
 * Tentackle - https://tentackle.org
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

package org.tentackle.maven.plugin.wizard.fx;

import org.tentackle.fx.GraphicProviderService;
import org.tentackle.fx.IkonliGraphicProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * PDO browser image provider.<br>
 * Based on the Ikonli Material Design 2 icon pack, which is also used by tentackle-fx.
 */
@GraphicProviderService(DataNodeGraphicProvider.REALM)
public class DataNodeGraphicProvider extends IkonliGraphicProvider {

  /**
   * The image realm of the data node icons.
   */
  public static final String REALM = "data";


  /**
   * Maps Tentackle symbolic names to icon font names.
   */
  private Map<String, String> nameMap;

  /**
   * Gets the symbolic name map.
   *
   * @return the map of symbolic to icon pack names
   */
  protected Map<String, String> getNameMap() {
    if (nameMap == null) {
      Map<String, String> nm = new HashMap<>();

      nm.put("DataItem", "mdi2b-brightness-auto");
      nm.put("DataList", "mdi2f-format-list-bulleted");
      nm.put("DataObject", "mdi2a-alpha-o-box");

      nameMap = nm;
    }
    return nameMap;
  }

  @Override
  protected String translateName(String name) {
    String transName = getNameMap().get(name);
    return transName != null ? transName : name;
  }



}
