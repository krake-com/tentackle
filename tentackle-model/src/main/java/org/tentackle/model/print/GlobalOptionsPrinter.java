/*
 * Tentackle - http://www.tentackle.org
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

package org.tentackle.model.print;

import org.tentackle.common.Constants;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.impl.CommonOptionsImpl;
import org.tentackle.model.impl.EntityOptionsImpl;

/**
 * Prints the global options.
 *
 * @author harald
 */
public class GlobalOptionsPrinter {

  private final Entity entity;
  private final ModelDefaults modelDefaults;

  /**
   * Creates the printer.
   *
   * @param entity the entity
   * @param modelDefaults the model defaults, null if none
   */
  public GlobalOptionsPrinter(Entity entity, ModelDefaults modelDefaults) {
    this.entity = entity;
    this.modelDefaults = modelDefaults;
  }

  /**
   * Pretty prints the options.
   *
   * @return the string
   */
  public String print() {
    StringBuilder buf = new StringBuilder();
    buf.append('[');
    EntityOptions options = entity.getOptions();

    if (options.isLowerCase()) {
      buf.append(Constants.BIND_LC).append(',');
    }
    if (options.isUpperCase()) {
      buf.append(Constants.BIND_UC).append(',');
    }
    if (options.isNoDeclare()) {
      buf.append(CommonOptionsImpl.OPTION_NODECLARE).append(',');
    }
    if (options.isNoMethod()) {
      buf.append(CommonOptionsImpl.OPTION_NOMETHOD).append(',');
    }
    if (options.isNoConstant()) {
      buf.append(CommonOptionsImpl.OPTION_NOCONSTANT).append(',');
    }
    if (options.isDerived()) {
      buf.append(CommonOptionsImpl.OPTION_DERIVED).append(',');
    }
    if (options.isFromSuper()) {
      buf.append(CommonOptionsImpl.OPTION_SUPER).append(',');
    }
    if (options.isReadOnly()) {
      buf.append(CommonOptionsImpl.OPTION_READONLY).append(',');
    }
    if (options.isWriteOnly()) {
      buf.append(CommonOptionsImpl.OPTION_WRITEONLY).append(',');
    }
    if (options.getAccessScope() != AccessScope.PUBLIC) {
      buf.append(options.getAccessScope()).append(',');
    }
    if (options.isTrimRead() && options.isTrimWrite()) {
      buf.append(CommonOptionsImpl.OPTION_TRIM).append(',');
    }
    else if (options.isTrimRead()) {
      buf.append(CommonOptionsImpl.OPTION_TRIM_READ).append(',');
    }
    else if (options.isTrimWrite()) {
      buf.append(CommonOptionsImpl.OPTION_TRIM_WRITE).append(',');
    }
    if (options.isMapNull()) {
      buf.append(CommonOptionsImpl.OPTION_MAPNULL).append(',');
    }
    if (options.isSetGet()) {
      buf.append(CommonOptionsImpl.OPTION_SETGET).append(',');
    }

    if (options.isNoPrimaryKey()) {
      buf.append(EntityOptionsImpl.OPTION_NO_PRIMARY).append(',');
    }
    if (options.isTableSerialProvided()) {
      buf.append(EntityOptionsImpl.OPTION_TABLESERIAL).append(',');
    }
    if (options.isTokenLockProvided()) {
      buf.append(EntityOptionsImpl.OPTION_TOKENLOCK).append(',');
    }
    if (options.isNormTextProvided()) {
      buf.append(EntityOptionsImpl.OPTION_NORMTEXT).append(',');
    }

    boolean sizeOption = false;
    String bindOptions = options.getBindOptions();
    if (bindOptions != null) {
      // special handing for SIZE option
      if (bindOptions.contains(CommonOptionsImpl.BIND_SIZE)) {
        sizeOption = true;
      }
    }

    if (options.noModelDefaults() || modelDefaults == null) {
      if (sizeOption) {
        buf.append(CommonOptionsImpl.BIND_SIZE).append(',');
      }
      if (options.isBind()) {
        buf.append(CommonOptionsImpl.OPTION_BIND).append(',');
      }
      if (options.getTrackType() != null) {
        buf.append(options.getTrackType()).append(',');
      }
      if (options.isAutoSelect()) {
        buf.append(Constants.BIND_AUTOSELECT).append(',');
      }
      if (options.isRootEntity() != entity.isRootEntityAccordingToModel()) {
        if (!options.isRootEntity()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOT).append(',');
      }
      if (options.isRootIdProvided() != entity.isProvidingRootIdAccordingToModel()) {
        if (!options.isRootIdProvided()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTID).append(',');
      }
      if (options.isRootClassIdProvided() != entity.isProvidingRootClassIdAccordingToModel()) {
        if (!options.isRootClassIdProvided()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTCLASSID).append(',');
      }
      if (options.isRemote()) {
        buf.append(EntityOptionsImpl.OPTION_REMOTE).append(',');
      }
    }
    else {
      if (modelDefaults.getSize() != null && modelDefaults.getSize() != sizeOption) {
        if (!sizeOption) {
          buf.append('-');
        }
        buf.append(CommonOptionsImpl.BIND_SIZE).append(',');
      }
      if (modelDefaults.getBind() != null && modelDefaults.getBind() != options.isBind()) {
        if (!options.isBind()) {
          buf.append('-');
        }
        buf.append(CommonOptionsImpl.OPTION_BIND).append(',');
      }
      if (modelDefaults.getTrackType() != null && options.getTrackType() != null &&
          modelDefaults.getTrackType() != options.getTrackType()) {
        buf.append(options.getTrackType()).append(',');
      }
      if (modelDefaults.getAutoSelect() != null && modelDefaults.getAutoSelect() != options.isAutoSelect()) {
        if (!options.isAutoSelect()) {
          buf.append('-');
        }
        buf.append(Constants.BIND_AUTOSELECT).append(',');
      }
      if (modelDefaults.getRoot() != null && modelDefaults.getRoot() != options.isRootEntity()) {
        if (!options.isRootEntity()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOT).append(',');
      }
      if (modelDefaults.getRootId() != null && modelDefaults.getRootId() != options.isRootIdProvided()) {
        if (!options.isRootIdProvided()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTID).append(',');
      }
      if (modelDefaults.getRootClassId() != null && modelDefaults.getRootClassId() != options.isRootClassIdProvided()) {
        if (!options.isRootClassIdProvided()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTCLASSID).append(',');
      }
      if (modelDefaults.getRemote() != null && modelDefaults.getRemote() != options.isRemote()) {
        if (!options.isRemote()) {
          buf.append('-');
        }
        buf.append(EntityOptionsImpl.OPTION_REMOTE).append(',');
      }
    }


    if (buf.charAt(buf.length() - 1) == ',') {
      buf.setLength(buf.length() - 1);
    }

    if (options.getSorting() != null && !options.getSorting().isEmpty()) {
      buf.append(" |");
      for (String sort: options.getSorting()) {
        buf.append(' ').append(sort);
      }
    }

    buf.append(']');
    return buf.toString();
  }

}
