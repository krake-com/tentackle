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
package org.tentackle.model;

import java.util.StringTokenizer;

/**
 * Defaults for model parsing.
 * <p>
 * Simply set a default attribute to a non-null value to be honored during model parsing.
 * Only logically "higher" values will be copied to the model.
 * For example: fulltracked will overwrite tracked, but a default of tracked will not overwrite fulltracked if
 * already defined in the model. Accordingly deletionCascaded will never clear the model setting to false if true.
 * <p>
 * Notice: the defaults have an effect on the generated java code only. The SQL/database-side remains unaffected.
 *
 * @author harald
 */
public class ModelDefaults {

  /**
   * The default tracktype.
   */
  private TrackType trackType;

  /**
   * cascaded delete in relations.
   */
  private Boolean deletionCascaded;

  /**
   * Automatically set ROOT global option.
   */
  private Boolean root;

  /**
   * Automatically set ROOTID global option.
   */
  private Boolean rootId;

  /**
   * Automatically set ROOTCLASSID global option.
   */
  private Boolean rootClassId;

  /**
   * The default [bind] option.
   */
  private Boolean bind;

  /**
   * The default [size] option.
   */
  private Boolean size;

  /**
   * The default [autoselect] option.
   */
  private Boolean autoSelect;

  /**
   * The default [remote] option.
   */
  private Boolean remote;

  // more to come...


  /**
   * Creates the defaults.
   */
  public ModelDefaults() {
  }

  /**
   * Creates the defaults from a string.
   * @param str the string
   * @throws ModelException if parsing failed
   */
  public ModelDefaults(String str) throws ModelException {
    parse(str);
  }

  /**
   * Parses the defaults from a string.
   *
   * @param str the string
   * @throws ModelException if parsing failed
   */
  public void parse(String str) throws ModelException {
    clear();
    StringTokenizer stok = new StringTokenizer(str, " ,\t\n\r");
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken().toLowerCase();
      switch(token) {
        case "tracked":
          trackType = TrackType.TRACKED;
          break;
        case "attracked":
          trackType = TrackType.ATTRACKED;
          break;
        case "fulltracked":
          trackType = TrackType.FULLTRACKED;
          break;
        case "deletioncascaded":
        case "cascadeddelete":
        case "cascaded":
        case "cascade":
          deletionCascaded = true;
          break;
        case "root":
          root = true;
          break;
        case "rootid":
          rootId = true;
          break;
        case "rootclassid":
          rootClassId = true;
          break;
        case "bind":
          bind = true;
          break;
        case "size":
          size = true;
          break;
        case "autoselect":
          autoSelect = true;
          break;
        case "remote":
          remote = true;
          break;

        default:
          throw new ModelException("unknown model default: " + token);
      }
    }
  }


  /**
   * Clears the defaults.
   */
  public void clear() {
    trackType = null;
    deletionCascaded = null;
    root = null;
    rootId = null;
    rootClassId = null;
    bind = null;
    size = null;
    autoSelect = null;
    remote = null;
  }

  /**
   * Gets the tracktype default.
   *
   * @return the track type
   */
  public TrackType getTrackType() {
    return trackType;
  }

  /**
   * Gets the cascaded delete default.
   *
   * @return the cascaded delete
   */
  public Boolean getDeletionCascaded() {
    return deletionCascaded;
  }

  /**
   * Gets the root default.
   *
   * @return true if determine root option according to model
   */
  public Boolean getRoot() {
    return root;
  }

  /**
   * Gets the rootid default.
   *
   * @return true if determine rootid option according to model
   */
  public Boolean getRootId() {
    return rootId;
  }

  /**
   * Gets the rootclassid default.
   *
   * @return true if determine rootclassid option according to model
   */
  public Boolean getRootClassId() {
    return rootClassId;
  }

  /**
   * Gets the [bind] default.
   *
   * @return true if set [bind] option
   */
  public Boolean getBind() {
    return bind;
  }

  /**
   * Gets the [size] default.
   *
   * @return true if set [size] option
   */
  public Boolean getSize() {
    return size;
  }

  /**
   * Gets the [autoselect] default.
   *
   * @return true if set [autoselect] option
   */
  public Boolean getAutoSelect() {
    return autoSelect;
  }

  /**
   * Gets the [remote] default.
   *
   * @return true i set [remote] option
   */
  public Boolean getRemote() {
    return remote;
  }

}
