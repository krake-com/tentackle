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

package org.tentackle.model.impl;

import java.util.ArrayList;
import java.util.List;
import org.tentackle.common.Constants;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.TrackType;

/**
 * Entity options.
 *
 * @author harald
 */
public class EntityOptionsImpl extends CommonOptionsImpl implements EntityOptions {

  /** don't apply model defaults. */
  public static final String OPTION_NO_MODELDEFAULTS = "NODEFAULTS";

  /** don't generate ID as primary key. */
  public static final String OPTION_NO_PRIMARY = "NOPKEY";

  /** column tableserial is provided. */
  public static final String OPTION_TABLESERIAL = "TABLESERIAL";

  /** token lock columns editedBy/Since/Expiry are provided. */
  public static final String OPTION_TOKENLOCK = "TOKENLOCK";

  /** normText is provided. */
  public static final String OPTION_NORMTEXT = "NORMTEXT";

  /** root entity. */
  public static final String OPTION_ROOT = "ROOT";

  /** rootid is provided. */
  public static final String OPTION_ROOTID = "ROOTID";

  /** rootclassid is provided. */
  public static final String OPTION_ROOTCLASSID = "ROOTCLASSID";

  /** enable remoting. */
  public static final String OPTION_REMOTE = "REMOTE";





  private final EntityFactory factory;
  private final Entity entity;

  private boolean noModelDefaults;
  private boolean noPrimary;
  private TrackType trackType;
  private boolean tableSerialProvided;
  private boolean tokenLockProvided;
  private boolean normTextProvided;
  private boolean rootEntity;
  private boolean rootIdProvided;
  private boolean rootClassIdProvided;
  private boolean remote;
  private List<String> sorting;   // != null if '|' found



  /**
   * Creates entity options.
   *
   * @param factory the factory
   * @param entity the entity the options belong to
   * @param sourceInfo the source info
   */
  public EntityOptionsImpl(EntityFactory factory, Entity entity, SourceInfo sourceInfo) {
    super(sourceInfo);
    this.factory = factory;
    this.entity = entity;
    trackType = TrackType.NONE;
  }


  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public boolean noModelDefaults() {
    return noModelDefaults;
  }

  @Override
  public boolean applyOption(String option, Boolean ctrl) throws ModelException {

    boolean applied = super.applyOption(option, ctrl);
    boolean on = ctrl == null || ctrl;

    if (!applied) {
      String uco = option.toUpperCase();
      applied = true;
      switch (uco) {
        case OPTION_NO_MODELDEFAULTS:
          setNoModelDefaults(on);
          break;

        case OPTION_NO_PRIMARY:
          setNoPrimaryKey(on);
          break;

        case OPTION_ROOT:
          setRootEntity(on);
          break;

        case OPTION_REMOTE:
          setRemote(on);
          break;

        case OPTION_TABLESERIAL:
          setTableSerialProvided(on);
          if (on) {
            AttributeImpl tableSerialAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            tableSerialAttribute.setDataType(DataType.LONG_PRIMITIVE);
            tableSerialAttribute.setColumnName(Constants.CN_TABLESERIAL);
            tableSerialAttribute.setJavaName(Constants.AN_TABLESERIAL);
            tableSerialAttribute.getOptions().setComment("table serial");
            tableSerialAttribute.getOptions().setFromSuper(true);
            tableSerialAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(tableSerialAttribute);
          }
          break;

        case OPTION_ROOTID:
          setRootIdProvided(on);
          if (on) {
            AttributeImpl rootIdAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            rootIdAttribute.setDataType(DataType.LONG_PRIMITIVE);
            rootIdAttribute.setColumnName(Constants.CN_ROOTID);
            rootIdAttribute.setJavaName(Constants.AN_ROOTID);
            rootIdAttribute.getOptions().setComment("root entity id");
            rootIdAttribute.getOptions().setFromSuper(true);
            rootIdAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(0, rootIdAttribute);
          }
          break;

        case OPTION_ROOTCLASSID:
          setRootClassIdProvided(on);
          if (on) {
            AttributeImpl rootClassIdAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            rootClassIdAttribute.setDataType(DataType.INTEGER_PRIMITIVE);
            rootClassIdAttribute.setColumnName(Constants.CN_ROOTCLASSID);
            rootClassIdAttribute.setJavaName(Constants.AN_ROOTCLASSID);
            rootClassIdAttribute.getOptions().setComment("root entity class id");
            rootClassIdAttribute.getOptions().setFromSuper(true);
            rootClassIdAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(0, rootClassIdAttribute);
          }
          break;

        case OPTION_TOKENLOCK:
          setEditedByProvided(on);
          if (on) {
            AttributeImpl editedByAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            editedByAttribute.setDataType(DataType.LONG_PRIMITIVE);
            editedByAttribute.setColumnName(Constants.CN_EDITEDBY);
            editedByAttribute.setJavaName(Constants.AN_EDITEDBY);
            editedByAttribute.getOptions().setComment("being edited by user id");
            editedByAttribute.getOptions().setFromSuper(true);
            editedByAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(editedByAttribute);

            AttributeImpl editedSinceAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            editedSinceAttribute.setDataType(DataType.TIMESTAMP);
            editedSinceAttribute.setColumnName(Constants.CN_EDITEDSINCE);
            editedSinceAttribute.setJavaName(Constants.AN_EDITEDSINCE);
            editedSinceAttribute.getOptions().setComment("being edited since");
            editedSinceAttribute.getOptions().setFromSuper(true);
            editedSinceAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(editedSinceAttribute);

            AttributeImpl editedExpiryAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            editedExpiryAttribute.setDataType(DataType.TIMESTAMP);
            editedExpiryAttribute.setColumnName(Constants.CN_EDITEDEXPIRY);
            editedExpiryAttribute.setJavaName(Constants.AN_EDITEDEXPIRY);
            editedExpiryAttribute.getOptions().setComment("editing lock expiration");
            editedExpiryAttribute.getOptions().setFromSuper(true);
            editedExpiryAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(editedExpiryAttribute);
          }
          break;

        case OPTION_NORMTEXT:
          setNormTextProvided(on);
          if (on) {
            AttributeImpl normTextAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
            normTextAttribute.setDataType(DataType.STRING);
            normTextAttribute.setColumnName(Constants.CN_NORMTEXT);
            normTextAttribute.setJavaName(Constants.AN_NORMTEXT);
            normTextAttribute.getOptions().setComment("normalized text");
            normTextAttribute.getOptions().setFromSuper(true);
            normTextAttribute.getOptions().setSetGet(true);
            entity.getAttributes().add(normTextAttribute);
          }
          break;

        default:
          // sorted column?
          // this is just a convenience hack to allow ommitting the pipe sign.
          // works, however, only if the column to sort is not an option keyword.
          if (ctrl != null) {
            if (sorting == null) {
              sorting = new ArrayList<>();
            }
            sorting.add((on ? "+" : "-") + uco);
            break;
          }
          else  {
            // tracking type?
            try {
              setTrackType(TrackType.valueOf(uco));
              break;
            }
            catch (IllegalArgumentException ex) {
              // ignore
            }
          }
          applied = false;
      }
    }
    return applied;
  }


  /**
   * Checks that the sorting string starts with a + or -.
   *
   * @param token the sorting token
   * @return true if applied
   */
  public boolean processSorting(String token) {
    boolean applied = false;
    if (sorting == null) {
      sorting = new ArrayList<>();
    }
    if (token != null && !token.isEmpty()) {
      // must start with + or -
      char c = token.charAt(0);
      if (c == '-' || c == '+') {
        sorting.add(token);
        applied = true;
      }
      else if (Character.isLetter(c)) {
        // prepend +
        sorting.add("+" + token);
        applied = true;
      }
    }
    return applied;
  }


  @Override
  public boolean isNoPrimaryKey() {
    return noPrimary;
  }

  @Override
  public TrackType getTrackType() {
    return trackType;
  }

  @Override
  public boolean isTableSerialProvided() {
    return tableSerialProvided;
  }

  @Override
  public boolean isTokenLockProvided() {
    return tokenLockProvided;
  }

  @Override
  public boolean isNormTextProvided() {
    return normTextProvided;
  }

  @Override
  public boolean isRootEntity() {
    return rootEntity;
  }

  @Override
  public boolean isRootIdProvided() {
    return rootIdProvided;
  }

  @Override
  public boolean isRootClassIdProvided() {
    return rootClassIdProvided;
  }

  @Override
  public boolean isRemote() {
    return remote;
  }

  @Override
  public List<String> getSorting() {
    return sorting;
  }


  public void setNoModelDefaults(boolean noModelDefaults) {
    this.noModelDefaults = noModelDefaults;
  }

  public void setTrackType(TrackType trackType) {
    this.trackType = trackType;
  }

  public void setEditedByProvided(boolean tokenLockProvided) {
    this.tokenLockProvided = tokenLockProvided;
  }

  public void setNormTextProvided(boolean normText) {
    this.normTextProvided = normText;
  }

  public void setNoPrimaryKey(boolean noPrimary) {
    this.noPrimary = noPrimary;
  }

  public void setTableSerialProvided(boolean tableSerial) {
    this.tableSerialProvided = tableSerial;
  }

  public void setRootEntity(boolean rootEntity) {
    this.rootEntity = rootEntity;
  }

  public void setRootIdProvided(boolean rootId) {
    this.rootIdProvided = rootId;
  }

  public void setRootClassIdProvided(boolean rootClassId) {
    this.rootClassIdProvided = rootClassId;
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

}
