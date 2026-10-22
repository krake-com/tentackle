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

package org.tentackle.model.print;

import org.tentackle.common.Constants;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.TrackType;
import org.tentackle.model.impl.CommonOptionsImpl;
import org.tentackle.model.impl.EntityOptionsImpl;

import java.util.Objects;

/**
 * Prints the global options.
 *
 * @author harald
 */
public class GlobalOptionsPrinter {

  private final Entity entity;
  private final PrintConfiguration configuration;

  /**
   * Creates the printer.
   *
   * @param entity the entity
   * @param configuration the printing configuration
   */
  public GlobalOptionsPrinter(Entity entity, PrintConfiguration configuration) {
    this.entity = entity;
    this.configuration = configuration;
  }

  /**
   * Pretty prints the options.
   *
   * @return the options in square brackets, including the optional sorting, "[]" if none
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
    if (options.getStripMode() != null) {
      buf.append(options.getStripMode().getBindOption()).append(',');
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
    if (options.isMute()) {
      buf.append(CommonOptionsImpl.OPTION_MUTE).append(',');
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

    if (options.isProvided()) {
      buf.append(EntityOptionsImpl.OPTION_PROVIDED).append(',');
    }
    if (options.isCached()) {
      buf.append(EntityOptionsImpl.OPTION_CACHED).append(',');
    }

    ModelDefaults modelDefaults = configuration.modelDefaults();

    if (options.noModelDefaults() || modelDefaults == null) {
      if (entity.getSuperEntityName() == null &&
          options.isRoot() != entity.isRootEntityAccordingToModel()) {
        if (!options.isRoot()) {
          buf.append('!');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOT).append(',');
      }
      if (options.isMaxCol()) {
        buf.append(CommonOptionsImpl.BIND_SIZE).append(',');
      }
      if (options.isBindable()) {
        buf.append(CommonOptionsImpl.OPTION_BIND).append(',');
      }
      if (options.getTrackType() != TrackType.UNTRACKED) {
        buf.append(options.getTrackType()).append(',');
      }
      if (options.isAutoSelect()) {
        buf.append(Constants.BIND_AUTOSELECT).append(',');
      }
      if (options.isRootIdProvided() != entity.isProvidingRootIdAccordingToModel()) {
        if (!options.isRootIdProvided()) {
          buf.append('!');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTID).append(',');
      }
      if (options.isRootClassIdProvided() != entity.isProvidingRootClassIdAccordingToModel()) {
        if (!options.isRootClassIdProvided()) {
          buf.append('!');
        }
        buf.append(EntityOptionsImpl.OPTION_ROOTCLASSID).append(',');
      }
      if (options.isRemote()) {
        buf.append(EntityOptionsImpl.OPTION_REMOTE).append(',');
      }
    }
    else {
      printBool(buf, CommonOptionsImpl.BIND_SIZE, options.isMaxCol(), modelDefaults.getSize());
      printBool(buf, CommonOptionsImpl.OPTION_BIND, options.isBindable(), modelDefaults.getBind());
      printBool(buf, Constants.BIND_AUTOSELECT, options.isAutoSelect(), modelDefaults.getAutoSelect());
      printBool(buf, EntityOptionsImpl.OPTION_REMOTE, options.isRemote(), modelDefaults.getRemote());

      if (modelDefaults.getTrackType() == null && options.getTrackType() != TrackType.UNTRACKED ||
          !Objects.equals(modelDefaults.getTrackType(), options.getTrackType())) {
        buf.append(options.getTrackType()).append(',');
      }

      if (entity.getSuperEntityName() == null) {
        if (modelDefaults.getRoot() != null) {
          printBool(buf, EntityOptionsImpl.OPTION_ROOT, options.isRoot(),
                    modelDefaults.getRoot() && entity.isRootEntityAccordingToModel());
        }
        else {
          printBool(buf, EntityOptionsImpl.OPTION_ROOT, options.isRoot(), entity.isRootEntityAccordingToModel());
        }
      }

      if (modelDefaults.getRootId() != null) {
        printBool(buf, EntityOptionsImpl.OPTION_ROOTID, options.isRootIdProvided(),
                modelDefaults.getRootId() && entity.isProvidingRootIdAccordingToModel());
      }
      else {
        printBool(buf, EntityOptionsImpl.OPTION_ROOTID, options.isRootIdProvided(),
                entity.isProvidingRootIdAccordingToModel());
      }

      if (modelDefaults.getRootClassId() != null) {
        printBool(buf, EntityOptionsImpl.OPTION_ROOTCLASSID, options.isRootClassIdProvided(),
                modelDefaults.getRootClassId() && entity.isProvidingRootClassIdAccordingToModel());
      }
      else {
        printBool(buf, EntityOptionsImpl.OPTION_ROOTCLASSID, options.isRootClassIdProvided(),
                entity.isProvidingRootClassIdAccordingToModel());
      }
    }

    for (String stereotype: options.getStereotypes()) {
      buf.append('#').append(stereotype).append(',');
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


  /**
   * Prints a boolean value with respect to the model default's setting.
   *
   * @param buf the builder
   * @param name the option's name
   * @param optionValue the option's value
   * @param defaultValue the model default value
   */
  private void printBool(StringBuilder buf, String name, boolean optionValue, Boolean defaultValue) {
    if (defaultValue == null && optionValue ||
        defaultValue != null && defaultValue != optionValue) {
      if (!optionValue) {
        buf.append('!');
      }
      buf.append(name).append(',');
    }
  }

}
