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
package org.tentackle.model;

import java.util.Locale;
import java.util.StringTokenizer;

/**
 * Defaults for model parsing.
 * <p>
 * Set a default attribute to a non-null value to be honored during model parsing.
 * Only logically "higher" values will be copied to the model.
 * For example, {@code fulltracked} will overwrite {@code tracked}, but a default of {@code tracked}
 * will not overwrite {@code fulltracked} if already defined in the model.
 * Accordingly, {@code deletionCascaded} will never clear the model setting to false if true.
 *
 * @author harald
 */
public class ModelDefaults {

  /**
   * The default track type.
   */
  private TrackType trackType;

  /**
   * cascaded delete in relations.
   */
  private Boolean deletionCascaded;

  /**
   * Automatically set {@code ROOT} global option.
   */
  private Boolean root;

  /**
   * Automatically set {@code ROOTID} global option.
   */
  private Boolean rootId;

  /**
   * Automatically set {@code ROOTCLASSID} global option.
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
      String token = stok.nextToken().toLowerCase(Locale.ROOT);
      boolean on = true;
      if (token.startsWith("!")) {
        token = token.substring(1);
        on = false;
      }
      switch(token) {
        case "tracked":
          trackType = on ? TrackType.TRACKED : TrackType.UNTRACKED;
          break;
        case "attracked":
          trackType = on ? TrackType.ATTRACKED : TrackType.UNTRACKED;
          break;
        case "fulltracked":
          trackType = on ? TrackType.FULLTRACKED : TrackType.UNTRACKED;
          break;
        case "untracked":
          trackType = on ? TrackType.UNTRACKED : TrackType.UNTRACKED;
          break;
        case "deletioncascaded":
        case "cascadeddelete":
        case "cascaded":
        case "cascade":
          deletionCascaded = on;
          break;
        case "root":
          root = on;
          break;
        case "rootid":
          rootId = on;
          break;
        case "rootclassid":
          rootClassId = on;
          break;
        case "bind":
          bind = on;
          break;
        case "size":
          size = on;
          break;
        case "autoselect":
          autoSelect = on;
          break;
        case "remote":
          remote = on;
          break;

        default:
          throw new ModelException("unknown model default: " + token);
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (trackType != null) {
      buf.append(trackType).append(", ");
    }
    if (deletionCascaded != null) {
      if (!deletionCascaded) {
        buf.append('!');
      }
      buf.append("CASCADE, ");
    }
    if (root != null) {
      if (!root) {
        buf.append('!');
      }
      buf.append("ROOT, ");
    }
    if (rootId != null) {
      if (!rootId) {
        buf.append('!');
      }
      buf.append("ROOTID, ");
    }
    if (rootClassId != null) {
      if (!rootClassId) {
        buf.append('!');
      }
      buf.append("ROOTCLASSID, ");
    }
    if (bind != null) {
      if (!bind) {
        buf.append('!');
      }
      buf.append("BIND, ");
    }
    if (size != null) {
      if (!size) {
        buf.append('!');
      }
      buf.append("SIZE, ");
    }
    if (autoSelect != null) {
      if (!autoSelect) {
        buf.append('!');
      }
      buf.append("AUTOSELECT, ");
    }
    if (remote != null) {
      if (!remote) {
        buf.append('!');
      }
      buf.append("REMOTE, ");
    }
    String str = buf.toString();
    if (str.endsWith(", ")) {
      str = str.substring(0, str.length() - 2);
    }
    return str;
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
   * Gets the trac type default.
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
   * @return true to determine the {@code root} option according to model
   */
  public Boolean getRoot() {
    return root;
  }

  /**
   * Gets the rootid default.
   *
   * @return true to determine the {@code rootid} option according to model
   */
  public Boolean getRootId() {
    return rootId;
  }

  /**
   * Gets the rootclassid default.
   *
   * @return true to determine the {@code rootclassid} option according to model
   */
  public Boolean getRootClassId() {
    return rootClassId;
  }

  /**
   * Gets the [bind] default.
   *
   * @return true to set the [bind] option
   */
  public Boolean getBind() {
    return bind;
  }

  /**
   * Gets the [size] default.
   *
   * @return true to set the [size] option
   */
  public Boolean getSize() {
    return size;
  }

  /**
   * Gets the [autoselect] default.
   *
   * @return true to set the [autoselect] option
   */
  public Boolean getAutoSelect() {
    return autoSelect;
  }

  /**
   * Gets the [remote] default.
   *
   * @return true to set the [remote] option
   */
  public Boolean getRemote() {
    return remote;
  }

}
