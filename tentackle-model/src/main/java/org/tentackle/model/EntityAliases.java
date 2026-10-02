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

package org.tentackle.model;

import java.io.Serial;
import java.util.HashMap;
import java.util.StringTokenizer;

/**
 * Holds aliases for entities.<br>
 * Maps names used in relations or inheritance declarations to entity names,
 * for example, to simplify refactoring.
 * <p>
 * Example:
 * <pre>
 *    &lt;entityAliases&gt;
 *       Port = LogisticNode,
 *       ManifestVoyage = Voyage
 *    &lt;/entityAliases&gt;
 * </pre>
 * The alias goes to the left of the equal sign. It will be mapped to the right side.
 *
 * @author harald
 */
public class EntityAliases extends HashMap<String,String> {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Creates an empty alias map.
   */
  public EntityAliases() {
  }

  /**
   * Creates the aliases from a string.
   *
   * @param str the string
   * @throws ModelException if parsing failed
   */
  public EntityAliases(String str) throws ModelException {
    parse(str);
  }

  /**
   * Parses the aliases from a string.
   *
   * @param str the string
   * @throws ModelException if parsing failed
   */
  public void parse(String str) throws ModelException {
    clear();
    StringTokenizer stok = new StringTokenizer(str, ";,\n\r");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      int ndx = token.indexOf('=');
      if (ndx < 0) {
        throw new ModelException("missing delimiter '=' in entity alias: " + token);
      }
      String alias = token.substring(0, ndx).trim();
      String name = token.substring(ndx + 1).trim();
      String oldName = put(alias, name);
      if (oldName != null && !oldName.equals(name)) {
        throw new ModelException("alias '" + alias + "' already mapped to '" + oldName + "'");
      }
    }
  }

  /**
   * Maps the given name to an entity name.
   *
   * @param name the original name used in the model
   * @return the possibly mapped name if name was an alias
   */
  public String translate(String name) {
    String mappedName = get(name);
    return mappedName == null ? name : mappedName;
  }
  
}
