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
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeOptions;
import org.tentackle.model.EntityOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;
import org.tentackle.sql.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Attribute options.
 *
 * @author harald
 */
public class AttributeOptionsImpl extends CommonOptionsImpl implements AttributeOptions {

  /**
   * Emits a SQL {@code DEFAULT} clause into the generated column DDL. This is a literal,
   * database-side default supplied on insert when the column is omitted; it does not
   * initialize the Java field. Followed by the literal value ({@code DEFAULT <value>}).
   */
  public static final String OPTION_DEFAULT = "DEFAULT";

  /**
   * A Java expression placed on the generated field declaration, so it initializes every
   * instance from construction (a reload from the database then overwrites it). Use it to avoid
   * {@code null} starting values. Followed by the expression ({@code INIT <expr>}).
   */
  public static final String OPTION_INIT = "INIT";

  /**
   * A Java expression run once from the generated {@code presetVirgin()} just before a
   * never-persisted PDO is first saved. Unlike {@code INIT} it does not touch reloaded objects
   * and may call methods or read other fields. Followed by the expression ({@code NEW <expr>}).
   */
  public static final String OPTION_NEW = "NEW";

  /**
   * Marks the attribute that holds the id of the object spanning the PDO's domain context
   * (e.g. the tenant/mandator). The generated {@code determineContextId()} fills it and queries
   * add a context condition. At most one per entity; not allowed on embedded attributes.
   */
  public static final String OPTION_CONTEXT = "CONTEXT";

  /** Marks the attribute as part of the unique domain key (UDK). */
  public static final String OPTION_DOMAINKEY = "KEY";

  /**
   * For the legacy {@code java.sql} date/time types only: applies an explicit, per-object timezone
   * via the {@code Calendar}-argument JDBC accessors. The PDO must supply the {@code Calendar}
   * through a {@code get<Attribute>TimezoneConfig()} method. Use for wall-clock values stored in a
   * data-driven zone; prefer the {@code java.time} types where possible.
   */
  public static final String OPTION_TZ = "TZ";

  /** Generates the attribute in the persistence implementation only, not in the PDO interface. */
  public static final String OPTION_HIDDEN = "HIDDEN";

  /** Excludes the attribute from snapshot and copy operations. */
  public static final String OPTION_SHALLOW = "SHALLOW";

  /** Includes this attribute's value in the entity's {@code normText} column for full-text search. */
  public static final String OPTION_NORMTEXT = "NORMTEXT";



  private AttributeImpl attribute;    // associated attribute

  private List<String> annotations;
  private Object defaultValue;
  private String initialValue;
  private String newValue;
  private boolean contextId;
  private boolean domainKey;
  private boolean utc;
  private boolean withTimezone;
  private boolean unsigned;
  private boolean digitsOnly;
  private boolean hidden;
  private boolean shallow;
  private boolean partOfNormText;


  /**
   * Creates attribute options.
   *
   * @param attribute the attribute
   * @param sourceInfo the source info, null if none
   */
  public AttributeOptionsImpl(Attribute attribute, SourceInfo sourceInfo) {
    super(sourceInfo);
    this.attribute = (AttributeImpl) attribute;
    annotations = new ArrayList<>();
  }

  /**
   * Clones the attribute options.<br>
   * The method is protected, since {@link #clone(AttributeImpl)} should be used instead,
   * which invokes {@code clone()}.
   *
   * @return the cloned options
   */
  @Override
  protected AttributeOptionsImpl clone() {
    AttributeOptionsImpl options = (AttributeOptionsImpl) super.clone();
    if (annotations != null) {
      options.annotations = new ArrayList<>(annotations);
    }
    return options;
  }

  /**
   * Clones options for another attribute instance.
   *
   * @param attribute the attribute
   * @return the options
   */
  public AttributeOptionsImpl clone(AttributeImpl attribute) {
    AttributeOptionsImpl options = clone();
    options.attribute = attribute;
    return options;
  }

  @Override
  public AttributeImpl getAttribute() {
    return attribute;
  }

  @Override
  public String getComment() {
    String comment = super.getComment();
    if (attribute.isEmbedded()) {
      StringBuilder cmt = new StringBuilder();
      if (comment != null) {
        cmt.append(comment).append(' ');
      }
      String pathName = attribute.getPathName();
      int lastDotNdx = pathName.lastIndexOf('.');
      if (lastDotNdx >= 0) {
        pathName = pathName.substring(0, lastDotNdx);
      }
      cmt.append('<').append(pathName).append('>');
      comment = cmt.toString();
    }
    return comment;
  }

  /**
   * Applies the entity options as defaults for this attribute.<br>
   * MAPNULL is only applied if the data type supports it, AUTOSELECT only for numeric and date/time types
   * (otherwise it is removed from the bind options).
   *
   * @param options the entity options
   * @param dataType the data type of the attribute
   */
  public void applyEntityOptions(EntityOptions options, DataType<?> dataType) {
    setNoDeclare(options.isNoDeclare());
    setNoMethod(options.isNoMethod());
    setNoConstant(options.isNoConstant());
    setMute(options.isMute());
    setFromSuper(options.isFromSuper());
    setReadOnly(options.isReadOnly());
    setWriteOnly(options.isWriteOnly());
    setAccessScope(options.getAccessScope());
    setTrimRead(options.isTrimRead());
    setTrimWrite(options.isTrimWrite());
    if (dataType.isMapNullSupported()) {
      setMapNull(options.isMapNull());
    }
    setBindable(options.isBindable());
    setUpperCase(options.isUpperCase());
    setLowerCase(options.isLowerCase());
    setMaxCol(options.isMaxCol());
    setStripMode(options.getStripMode());
    setBindOptions(options.getBindOptions());
    if (dataType.isNumeric() || dataType.isDateOrTime()) {
      setAutoSelect(options.isAutoSelect());
    }
    else  {
      // remove autoselect from bindoptions, if set
      removeBindOption(Constants.BIND_AUTOSELECT);
    }
  }



  @Override
  public boolean applyOption(String option, Boolean ctrl) throws ModelException {

    boolean applied = super.applyOption(option, ctrl);
    boolean on = ctrl == null || ctrl;

    if (!applied) {
      String uco = option.toUpperCase(Locale.ROOT);
      if (uco.startsWith(OPTION_DEFAULT)) {
        if (on) {
          String str = option.substring(OPTION_DEFAULT.length()).trim();
          Object value = getAttribute().getEffectiveDataType().valueOf(str);
          setDefaultValue(value);
        }
        else  {
          setDefaultValue(null);
        }
        applied = true;
      }
      else if (uco.startsWith(OPTION_INIT)) {
        if (on) {
          setInitialValue(option.substring(OPTION_INIT.length()).trim());
        }
        else {
          setInitialValue(null);
        }
        applied = true;
      }
      else if (uco.startsWith(OPTION_NEW)) {
        if (on) {
          setNewValue(option.substring(OPTION_NEW.length()).trim());
        }
        else {
          setNewValue(null);
        }
        applied = true;
      }
      else if (uco.equals(OPTION_CONTEXT)) {
        setContextId(on);
        applied = true;
      }
      else if (uco.equals(OPTION_DOMAINKEY)) {
        setDomainKey(on);
        applied = true;
      }
      else if (uco.equals(OPTION_TZ)) {
        setWithTimezone(on);
        applied = true;
      }
      else if (uco.equals(OPTION_HIDDEN)) {
        setHidden(on);
        applied = true;
      }
      else if (uco.equals(OPTION_SHALLOW)) {
        setShallow(on);
        applied = true;
      }
      else if (uco.equals(OPTION_NORMTEXT)) {
        setPartOfNormText(on);
        applied = true;
      }
      else if (uco.equals(Constants.BIND_UTC)) {
        setUTC(on);
        processBindOption(uco, on);
        applied = true;
      }
      else if (uco.equals(Constants.BIND_UNSIGNED)) {
        setUnsigned(on);
        processBindOption(uco, on);
        applied = true;
      }
      else if (uco.equals(Constants.BIND_DIGITS)) {
        setDigitsOnly(on);
        processBindOption(uco, on);
        applied = true;
      }
    }
    return applied;
  }


  @Override
  public List<String> getAnnotations() {
    return annotations;
  }

  @Override
  public Object getDefaultValue() {
    return defaultValue;
  }

  @Override
  public String getInitialValue() {
    return initialValue;
  }

  @Override
  public String getNewValue() {
    return newValue;
  }

  public void setAnnotations(List<String> annotations) {
    this.annotations = annotations;
  }

  public void setDefaultValue(Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public void setInitialValue(String initialValue) {
    this.initialValue = initialValue;
  }

  public void setNewValue(String newValue) {
    this.newValue = newValue;
  }

  @Override
  public boolean isContextId() {
    return contextId;
  }

  public void setContextId(boolean contextId) {
    this.contextId = contextId;
  }

  @Override
  public boolean isDomainKey() {
    return domainKey;
  }

  public void setDomainKey(boolean domainKey) {
    this.domainKey = domainKey;
  }

  @Override
  public boolean isUTC() {
    return utc;
  }

  public void setUTC(boolean utc) {
    this.utc = utc;
  }

  @Override
  public boolean isWithTimezone() {
    return withTimezone;
  }

  public void setWithTimezone(boolean withTimezone) {
    this.withTimezone = withTimezone;
  }

  @Override
  public boolean isUnsigned() {
    return unsigned;
  }

  public void setUnsigned(boolean unsigned) {
    this.unsigned = unsigned;
  }

  @Override
  public boolean isDigitsOnly() {
    return digitsOnly;
  }

  public void setDigitsOnly(boolean digitsOnly) {
    this.digitsOnly = digitsOnly;
  }

  @Override
  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  @Override
  public boolean isShallow() {
    return shallow;
  }

  public void setShallow(boolean shallow) {
    this.shallow = shallow;
  }

  @Override
  public boolean isPartOfNormText() {
    return partOfNormText;
  }

  public void setPartOfNormText(boolean partOfNormText) {
    this.partOfNormText = partOfNormText;
  }

  @Override
  public ModelException createModelException(String message) {
    if (attribute != null) {
      return attribute.createModelException(message);
    }
    return super.createModelException(message);
  }

}
