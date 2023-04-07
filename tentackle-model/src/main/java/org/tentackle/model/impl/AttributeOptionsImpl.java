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

  /** generate SQL DEFAULT. */
  public static final String OPTION_DEFAULT = "DEFAULT";

  /** initial value. */
  public static final String OPTION_INIT = "INIT";

  /** value to set before validation if persisted as new. */
  public static final String OPTION_NEW = "NEW";

  /** this is the object id holding the context. */
  public static final String OPTION_CONTEXT = "CONTEXT";

  /** this is a unique domain key (or part of it). */
  public static final String OPTION_DOMAINKEY = "KEY";

  /** timestamp, date or time with database timezone. */
  public static final String OPTION_TZ = "TZ";

  /** hidden attribute (no interface definition). */
  public static final String OPTION_HIDDEN = "HIDDEN";

  /** shallow attribute (ignored in snapshots and copies). */
  public static final String OPTION_SHALLOW = "SHALLOW";

  /** add toString value to the partOfNormText. */
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

  @Override
  public AttributeOptionsImpl clone() {
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
   * Sets the default from entity options.
   *
   * @param options the entity options
   * @param dataType the datatype of the attribute
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
