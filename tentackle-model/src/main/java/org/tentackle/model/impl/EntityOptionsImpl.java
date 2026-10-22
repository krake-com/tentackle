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

package org.tentackle.model.impl;

import org.tentackle.common.Constants;
import org.tentackle.common.Timestamp;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.TrackType;
import org.tentackle.sql.DataTypeFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Entity options.
 *
 * @author harald
 */
public class EntityOptionsImpl extends CommonOptionsImpl implements EntityOptions {

  /**
   * Ignores the project-wide model defaults for this entity, so only the options listed
   * explicitly in its model apply.
   */
  public static final String OPTION_NO_MODELDEFAULTS = "NODEFAULTS";

  /**
   * Suppresses the generated {@code id} primary key column, for entities that have none
   * (e.g. views or plain link tables).
   */
  public static final String OPTION_NO_PRIMARY = "NOPKEY";

  /**
   * Adds the {@code tableSerial} column: a table-wide serial bumped on every modification,
   * used for change detection and cache invalidation.
   */
  public static final String OPTION_TABLESERIAL = "TABLESERIAL";

  /**
   * Adds the token-lock columns ({@code editedBy}, {@code editedSince}, {@code editedExpiry})
   * that back Tentackle's pessimistic token locking.
   */
  public static final String OPTION_TOKENLOCK = "TOKENLOCK";

  /**
   * Adds the {@code normText} column holding normalized, concatenated text of the attributes
   * flagged {@code normtext}, used for full-text search.
   */
  public static final String OPTION_NORMTEXT = "NORMTEXT";

  /**
   * Marks the entity as a root entity — the top of an aggregate, or a standalone entity — rather
   * than a component of another aggregate. Normally derived automatically from the model.
   */
  public static final String OPTION_ROOT = "ROOT";

  /**
   * Adds the {@code rootId} column carrying the id of the aggregate root, so a component knows
   * its root without a database query. Normally derived automatically from the model.
   */
  public static final String OPTION_ROOTID = "ROOTID";

  /**
   * Adds the {@code rootClassId} column carrying the class id of the aggregate root, for
   * components shared by more than one root entity type. Normally derived automatically.
   */
  public static final String OPTION_ROOTCLASSID = "ROOTCLASSID";

  /**
   * Enables remoting via TRIP, so the entity's methods can be invoked across JVMs
   * (directly or through a remote server). Generates the remote delegate methods.
   */
  public static final String OPTION_REMOTE = "REMOTE";

  /**
   * Declares the table as maintained externally: no DDL or SQL is generated for this entity.
   */
  public static final String OPTION_PROVIDED = "PROVIDED";

  /**
   * Lets the entity provide a cache (root entities only); generates cache-aware select methods.
   */
  public static final String OPTION_CACHED = "CACHED";




  private final EntityFactory factory;
  private EntityImpl entity;

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
  private boolean provided;
  private boolean cached;
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
    this.entity = (EntityImpl) entity;
    trackType = TrackType.UNTRACKED;
  }

  /**
   * Clones the entity options.<br>
   * The method is protected, since {@link #clone(EntityImpl)} should be used instead,
   * which invokes {@code clone()}.
   *
   * @return the cloned options
   */
  @Override
  protected EntityOptionsImpl clone() {
    EntityOptionsImpl options = (EntityOptionsImpl) super.clone();
    if (sorting != null) {
      options.sorting = new ArrayList<>(sorting);
    }
    return options;
  }

  /**
   * Clones options for another entity instance.
   *
   * @param entity the entity
   * @return the options
   */
  public EntityOptionsImpl clone(EntityImpl entity) {
    EntityOptionsImpl options = clone();
    options.entity = entity;
    return options;
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
      String uco = option.toUpperCase(Locale.ROOT);
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

        case OPTION_PROVIDED:
          setProvided(on);
          break;

        case OPTION_CACHED:
          setCached(on);
          break;

        case OPTION_TABLESERIAL:
          setTableSerialProvided(on);
          break;

        case OPTION_ROOTID:
          setRootIdProvided(on);
          break;

        case OPTION_ROOTCLASSID:
          setRootClassIdProvided(on);
          break;

        case OPTION_TOKENLOCK:
          setTokenLockProvided(on);
          break;

        case OPTION_NORMTEXT:
          setNormTextProvided(on);
          break;

        default:
          // sorted column?
          // this is just a convenience hack to allow omitting the pipe sign.
          // works, however, only if the column to sort is not an option keyword.
          if (ctrl != null) {
            if (sorting == null) {
              sorting = new ArrayList<>();
            }
            sorting.add((on ? "+" : "-") + option);
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
   * Processes a sorting token.<br>
   * Tokens starting with '+' or '-' are added as is, tokens starting with a letter are added as ascending.
   *
   * @param token the sorting token
   * @return true if applied, false if null, empty or starting with an invalid character
   */
  public boolean processSorting(String token) {
    boolean applied = false;
    if (sorting == null) {
      sorting = new ArrayList<>();
    }
    if (token != null && !token.isEmpty()) {
      // must start with +, - or a letter
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
  public boolean isRoot() {
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
  public boolean isProvided() {
    return provided;
  }

  @Override
  public boolean isCached() {
    return cached;
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

  /**
   * Sets the TOKENLOCK option.<br>
   * Adds or removes the implicit attributes editedBy, editedSince and editedExpiry accordingly.
   *
   * @param tokenLockProvided true if the entity provides a token lock
   */
  public void setTokenLockProvided(boolean tokenLockProvided) {
    this.tokenLockProvided = tokenLockProvided;
    AttributeImpl editedByAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_EDITEDBY, false);
    AttributeImpl editedSinceAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_EDITEDSINCE, false);
    AttributeImpl editedExpiryAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_EDITEDEXPIRY, false);
    if (tokenLockProvided) {
      if (editedExpiryAttribute == null) {
        editedExpiryAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        editedExpiryAttribute.setDataType(DataTypeFactory.getInstance().get(Timestamp.class));
        editedExpiryAttribute.setColumnName(Constants.CN_EDITEDEXPIRY);
        editedExpiryAttribute.setName(Constants.AN_EDITEDEXPIRY);
        editedExpiryAttribute.getOptions().setComment("editing lock expiration");
        editedExpiryAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(editedExpiryAttribute);
      }
      if (editedSinceAttribute == null) {
        editedSinceAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        editedSinceAttribute.setDataType(DataTypeFactory.getInstance().get(Timestamp.class));
        editedSinceAttribute.setColumnName(Constants.CN_EDITEDSINCE);
        editedSinceAttribute.setName(Constants.AN_EDITEDSINCE);
        editedSinceAttribute.getOptions().setComment("being edited since");
        editedSinceAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(editedSinceAttribute);
      }
      if (editedByAttribute == null) {
        editedByAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        editedByAttribute.setDataType(DataTypeFactory.getInstance().get(Long.TYPE));
        editedByAttribute.setColumnName(Constants.CN_EDITEDBY);
        editedByAttribute.setName(Constants.AN_EDITEDBY);
        editedByAttribute.getOptions().setComment("being edited by user id");
        editedByAttribute.getOptions().setFromSuper(true);
        editedByAttribute.getOptions().setDefaultValue(0L);
        entity.getAttributes().addFirst(editedByAttribute);
      }
    }
    else {
      if (editedByAttribute != null) {
        entity.getAttributes().remove(editedByAttribute);
      }
      if (editedSinceAttribute != null) {
        entity.getAttributes().remove(editedSinceAttribute);
      }
      if (editedExpiryAttribute != null) {
        entity.getAttributes().remove(editedExpiryAttribute);
      }
    }
  }

  /**
   * Sets the NORMTEXT option.<br>
   * Adds or removes the implicit attribute normText accordingly.
   *
   * @param normText true if the entity provides a normtext column
   */
  public void setNormTextProvided(boolean normText) {
    this.normTextProvided = normText;
    AttributeImpl normTextAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_NORMTEXT, false);
    if (normText) {
      if (normTextAttribute == null) {
        normTextAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        normTextAttribute.setDataType(DataTypeFactory.getInstance().get(String.class));
        normTextAttribute.setColumnName(Constants.CN_NORMTEXT);
        normTextAttribute.setName(Constants.AN_NORMTEXT);
        normTextAttribute.getOptions().setComment("normalized text");
        normTextAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(normTextAttribute);
      }
    }
    else if (normTextAttribute != null) {
      entity.getAttributes().remove(normTextAttribute);
    }
  }

  public void setNoPrimaryKey(boolean noPrimary) {
    this.noPrimary = noPrimary;
  }

  /**
   * Sets the TABLESERIAL option.<br>
   * Adds or removes the implicit attribute tableSerial accordingly.
   *
   * @param tableSerial true if the entity provides a table serial
   */
  public void setTableSerialProvided(boolean tableSerial) {
    this.tableSerialProvided = tableSerial;
    AttributeImpl tableSerialAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_TABLESERIAL, false);
    if (tableSerial) {
      if (tableSerialAttribute == null) {
        tableSerialAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        tableSerialAttribute.setDataType(DataTypeFactory.getInstance().get(Long.TYPE));
        tableSerialAttribute.setColumnName(Constants.CN_TABLESERIAL);
        tableSerialAttribute.setName(Constants.AN_TABLESERIAL);
        tableSerialAttribute.getOptions().setComment("table serial");
        tableSerialAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(tableSerialAttribute);
      }
    }
    else if (tableSerialAttribute != null) {
      entity.getAttributes().remove(tableSerialAttribute);
    }
  }

  public void setRootEntity(boolean rootEntity) {
    this.rootEntity = rootEntity;
  }

  /**
   * Sets the ROOTID option.<br>
   * Adds or removes the implicit attribute rootId accordingly.
   *
   * @param rootId true if the entity provides a root id
   */
  public void setRootIdProvided(boolean rootId) {
    this.rootIdProvided = rootId;
    AttributeImpl rootIdAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_ROOTID, false);
    if (rootId) {
      if (rootIdAttribute == null) {
        rootIdAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        rootIdAttribute.setDataType(DataTypeFactory.getInstance().get(Long.TYPE));
        rootIdAttribute.setColumnName(Constants.CN_ROOTID);
        rootIdAttribute.setName(Constants.AN_ROOTID);
        rootIdAttribute.getOptions().setComment("root entity id");
        rootIdAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(rootIdAttribute);
      }
    }
    else if (rootIdAttribute != null) {
      entity.getAttributes().remove(rootIdAttribute);
    }
  }

  /**
   * Sets the ROOTCLASSID option.<br>
   * Adds or removes the implicit attribute rootClassId accordingly.
   *
   * @param rootClassId true if the entity provides a root class id
   */
  public void setRootClassIdProvided(boolean rootClassId) {
    this.rootClassIdProvided = rootClassId;
    AttributeImpl rootClassIdAttribute = (AttributeImpl) entity.getAttributeByJavaName(Constants.AN_ROOTCLASSID, false);
    if (rootClassId) {
      if (rootClassIdAttribute == null) {
        rootClassIdAttribute = (AttributeImpl) factory.createAttribute(entity, getSourceInfo(), true);
        rootClassIdAttribute.setDataType(DataTypeFactory.getInstance().get(Integer.TYPE));
        rootClassIdAttribute.setColumnName(Constants.CN_ROOTCLASSID);
        rootClassIdAttribute.setName(Constants.AN_ROOTCLASSID);
        rootClassIdAttribute.getOptions().setComment("root entity class id");
        rootClassIdAttribute.getOptions().setFromSuper(true);
        entity.getAttributes().addFirst(rootClassIdAttribute);
      }
    }
    else if (rootClassIdAttribute != null) {
      entity.getAttributes().remove(rootClassIdAttribute);
    }
  }

  public void setRemote(boolean remote) {
    this.remote = remote;
  }

  public void setProvided(boolean provided) {
    this.provided = provided;
  }

  public void setCached(boolean cached) {
    this.cached = cached;
  }

}
