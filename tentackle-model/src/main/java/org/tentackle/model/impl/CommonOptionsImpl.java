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
import org.tentackle.common.StripMode;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.model.AccessScope;
import org.tentackle.model.CommonOptions;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.tentackle.common.Constants.BIND_COLS;
import static org.tentackle.common.Constants.BIND_FILLER;
import static org.tentackle.common.Constants.BIND_LINES;
import static org.tentackle.common.Constants.BIND_MAXCOLS;
import static org.tentackle.common.Constants.BIND_SCALE;
import static org.tentackle.common.Constants.BIND_STRIP_BOTH;
import static org.tentackle.common.Constants.BIND_STRIP_LEFT;
import static org.tentackle.common.Constants.BIND_STRIP_NONE;
import static org.tentackle.common.Constants.BIND_STRIP_RIGHT;

/**
 * Implementation of common options.
 *
 * @author harald
 */
public abstract class CommonOptionsImpl implements CommonOptions, Cloneable {

  /**
   * Suppresses the backing-field declaration in the generated persistence implementation.
   * The column is still mapped (read from and written to the database), but no field is
   * emitted, because the value is held elsewhere (e.g. a derived or superclass field).
   */
  public static final String OPTION_NODECLARE = "NODECLARE";

  /**
   * Suppresses the getter and setter in the generated PDO interface and implementation.
   * The attribute is still declared and persisted, just not exposed through accessors.
   */
  public static final String OPTION_NOMETHOD = "NOMETHOD";

  /**
   * Suppresses the generated name constants for this element (the {@code AN_}/{@code CN_}
   * attribute- and column-name constants and the size constant).
   */
  public static final String OPTION_NOCONSTANT = "NOCONSTANT";

  /**
   * Marks the element as mute: mapped to a database column but deliberately invisible to the PDO.
   * No field, no accessors, no interface declaration, and excluded from snapshots and copies.
   * Since persistence never touches it, a mute column must be nullable or carry a database default.
   * Used for columns populated outside the object lifecycle (trigger, migration, hand-written code).
   */
  public static final String OPTION_MUTE = "MUTE";

  /**
   * Marks the element as inherited from a non-modeled superclass. The generator uses the attribute
   * (in queries, the domain key, etc.) but emits no field, name constant or accessors for it,
   * because they already exist upstream. (Implies {@code NODECLARE} and {@code NOMETHOD}.)
   */
  public static final String OPTION_SUPER = "SUPER";

  /**
   * Generates a getter only, no setter, so the application cannot modify the value.
   * (The persistence layer still writes the column when the row is created.)
   */
  public static final String OPTION_READONLY = "READONLY";

  /** Generates a setter only, no getter, so the application cannot read the value back. */
  public static final String OPTION_WRITEONLY = "WRITEONLY";

  /** Truncates the string value to the column's size when reading it from the database. */
  public static final String OPTION_TRIM_READ = "TRIMREAD";

  /** Truncates the string value to the column's size when writing it to the database. */
  public static final String OPTION_TRIM_WRITE = "TRIMWRITE";

  /** Truncates the string value to the column's size on both read and write ({@code TRIMREAD} + {@code TRIMWRITE}). */
  public static final String OPTION_TRIM = "TRIM";

  /**
   * Maps {@code null} to a non-null sentinel (empty string or 0) in the database, so a nullable
   * Java value can be stored in a {@code NOT NULL} column. The conversion is applied in both
   * directions by the generated JDBC read/write code.
   */
  public static final String OPTION_MAPNULL = "MAPNULL";

  /**
   * Annotates the generated accessors with {@code @Bindable}, making the property usable by the
   * FX binding layer. Setting any of the binding options below implies this option.
   */
  public static final String OPTION_BIND = "BIND";

  // binding options. Setting a binding option automatically turns on OPTION_BIND

  /**
   * Adds the {@code maxcols} binding option to the {@code @Bindable} annotation, limiting the
   * bound input length to the column's size (and, where applicable, the numeric scale).
   */
  public static final String BIND_SIZE = "SIZE";


  private final SourceInfo sourceInfo;

  private String comment;
  private String longComment;
  private List<String> stereotypes;
  private StringBuilder bindOptions;
  private boolean noDeclare;
  private boolean noMethod;
  private boolean noConstant;
  private boolean mute;
  private boolean fromSuper;
  private boolean readOnly;
  private boolean writeOnly;
  private AccessScope accessScope;
  private boolean trimRead;
  private boolean trimWrite;
  private boolean mapNull;
  private boolean bind;
  private boolean upperCase;
  private boolean lowerCase;
  private boolean autoSelect;
  private boolean maxCol;
  private StripMode stripMode;


  /**
   * Creates options.
   *
   * @param sourceInfo the source info, null if none
   */
  public CommonOptionsImpl(SourceInfo sourceInfo) {
    this.sourceInfo = sourceInfo;
    stereotypes = new ArrayList<>();
    bindOptions = new StringBuilder();
    accessScope = AccessScope.PUBLIC;
  }

  /**
   * Clones the options.<br>
   * Protected since subclasses' clone() methods are protected to enforce invocation via
   * special clone methods with additional arguments.
   *
   * @return the cloned options
   */
  @Override
  protected CommonOptionsImpl clone() {
    try {
      CommonOptionsImpl options = (CommonOptionsImpl) super.clone();
      if (stereotypes != null) {
        options.stereotypes = new ArrayList<>(stereotypes);
      }
      if (bindOptions != null) {
        options.bindOptions = new StringBuilder(bindOptions);
      }
      return options;
    }
    catch (CloneNotSupportedException cnx) {
      throw new TentackleRuntimeException(cnx);   // cannot happen, but...
    }
  }

  /**
   * Gets the source info.
   *
   * @return the info, null if unknown
   */
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  /**
   * Applies given option string.<br>
   *
   * @param option the option string
   * @param ctrl option control: true if turn option on, false if option off, null if on/off missing (defaults to on)
   * @return true if applied, false if unknown option
   * @throws ModelException if applying the option failed
   */
  public boolean applyOption(String option, Boolean ctrl) throws ModelException {

    String uco = option.toUpperCase(Locale.ROOT);
    String fullopt = uco;
    int ndx = uco.indexOf('=');   // option followed by '=', ex. COLS=5
    if (ndx > 0) {
      uco = uco.substring(0, ndx);
    }

    boolean on = ctrl == null || ctrl;

    boolean applied = true;

    switch(uco) {

      case OPTION_NODECLARE:
        setNoDeclare(on);
        break;

      case OPTION_NOMETHOD:
        setNoMethod(on);
        break;

      case OPTION_NOCONSTANT:
        setNoConstant(on);
        break;

      case OPTION_MUTE:
        setMute(on);
        break;

      case OPTION_SUPER:
        setFromSuper(on);
        break;

      case OPTION_READONLY:
        setReadOnly(on);
        break;

      case OPTION_WRITEONLY:
        setWriteOnly(on);
        break;

      case OPTION_TRIM_READ:
        setTrimRead(on);
        break;

      case OPTION_TRIM_WRITE:
        setTrimWrite(on);
        break;

      case OPTION_TRIM:
        setTrimRead(on);
        setTrimWrite(on);
        break;

      case OPTION_MAPNULL:
        setMapNull(on);
        break;

      case OPTION_BIND:
        setBindable(on);
        break;

      case Constants.BIND_UC:
        setUpperCase(on);
        processBindOption(Constants.BIND_LC, false);
        processBindOption(uco, on);
        break;

      case Constants.BIND_LC:
        setLowerCase(on);
        processBindOption(Constants.BIND_UC, false);
        processBindOption(uco, on);
        break;

      case Constants.BIND_AUTOSELECT:
        setAutoSelect(on);
        processBindOption(uco, on);
        break;

      case BIND_COLS:
      case BIND_MAXCOLS:
      case BIND_SCALE:
      case BIND_LINES:
      case BIND_FILLER:
        processBindOption(fullopt, on);
        break;

      case BIND_SIZE:
        setMaxCol(on);
        processBindOption(uco, on);
        break;

      case BIND_STRIP_NONE:
      case BIND_STRIP_BOTH:
      case BIND_STRIP_LEFT:
      case BIND_STRIP_RIGHT:
        setStripMode(on ? StripMode.fromBindOption(uco) : null);
        // remove old strip options, if any
        processBindOption(BIND_STRIP_NONE, false);
        processBindOption(BIND_STRIP_BOTH, false);
        processBindOption(BIND_STRIP_LEFT, false);
        processBindOption(BIND_STRIP_RIGHT, false);
        processBindOption(uco, on);
        break;

      default:
        // may be scope
        try {
          AccessScope scope = AccessScope.valueOf(uco);
          accessScope = on ? scope : AccessScope.PUBLIC;
          break;
        }
        catch (IllegalArgumentException ex) {
          // not a scope
        }
        applied = false;
    }

    return applied;
  }

  /**
   * Processes an option string.<br>
   * If the option string starts with ! or -, the option is considered to be turned off.
   * If the option starts with '+' it is forced to be turned on.
   * In all other cases it is left to the implementation of {@link #applyOption(java.lang.String, java.lang.Boolean)}
   * how the option should be treated.
   *
   * @param option the option string
   * @return true if option applied, false if not known
   * @throws ModelException if option malformed
   */
  public boolean processOption(String option) throws ModelException {
    return option != null && !option.isEmpty() &&
           switch (option.charAt(0)) {
             case '!', '-' -> applyOption(option.substring(1), Boolean.FALSE);
             case '+' -> applyOption(option.substring(1), Boolean.TRUE);
             default -> applyOption(option, null);
           };
  }

  /**
   * Processes a bind option.
   *
   * @param option the bind option
   * @param on true to add, else remove the option
   */
  public void processBindOption(String option, boolean on) {
    if (on) {
      setBindable(true);
      addBindOption(option);
    }
    else {
      removeBindOption(option);
    }
  }

  @Override
  public String getComment() {
    return comment;
  }

  /**
   * Sets the comment.
   *
   * @param comment the comment
   */
  public void setComment(String comment) {
    this.comment = comment;
  }


  @Override
  public String getLongComment() {
    return longComment;
  }

  /**
   * Sets the long comment.
   *
   * @param longComment the long comment
   */
  public void setLongComment(String longComment) {
    this.longComment = longComment;
  }

  @Override
  public List<String> getStereotypes() {
    return stereotypes;
  }

  public void setStereotypes(List<String> stereotypes) {
    this.stereotypes = stereotypes;
  }

  @Override
  public String getBindOptions() {
    return bindOptions.toString();
  }

  public void setBindOptions(String bindOptions) {
    this.bindOptions = new StringBuilder(bindOptions == null ? "" : bindOptions);
  }

  /**
   * Adds a bind option.<br>
   *
   * @param option the option
   */
  public void addBindOption(String option) {
    if (!bindOptions.isEmpty() && bindOptions.charAt(bindOptions.length() - 1) != ',') {
      bindOptions.append(',');    // separate by comma
    }
    bindOptions.append(option);
  }

  /**
   * Removes a bind option.<br>
   *
   * @param option the option
   */
  public void removeBindOption(String option) {
    int ndx = bindOptions.indexOf(option);
    if (ndx >= 0) {
      bindOptions.delete(ndx, ndx + option.length());
      int commaIndex = ndx > 0 ? ndx - 1 : 0;
      if (commaIndex < bindOptions.length() && bindOptions.charAt(commaIndex) == ',') {
        bindOptions.deleteCharAt(commaIndex);    // remove comma as well
      }
    }
  }

  @Override
  public boolean isNoDeclare() {
    return noDeclare;
  }

  @Override
  public boolean isNoMethod() {
    return noMethod;
  }

  @Override
  public boolean isNoConstant() {
    return noConstant;
  }

  @Override
  public boolean isMute() {
    return mute;
  }

  @Override
  public boolean isFromSuper() {
    return fromSuper;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public boolean isWriteOnly() {
    return writeOnly;
  }

  @Override
  public AccessScope getAccessScope() {
    return accessScope;
  }

  @Override
  public boolean isTrimRead() {
    return trimRead;
  }

  @Override
  public boolean isTrimWrite() {
    return trimWrite;
  }

  @Override
  public boolean isMapNull() {
    return mapNull;
  }

  @Override
  public boolean isBindable() {
    return bind;
  }

  public void setAccessScope(AccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public void setBindable(boolean bind) {
    this.bind = bind;
  }

  public void setMute(boolean mute) {
    this.mute = mute;
  }

  public void setFromSuper(boolean fromSuper) {
    this.fromSuper = fromSuper;
    if (fromSuper) {
      setNoDeclare(true);
      setNoMethod(true);
    }
  }

  public void setMapNull(boolean mapNull) {
    this.mapNull = mapNull;
  }

  public void setNoConstant(boolean noConstant) {
    this.noConstant = noConstant;
  }

  public void setNoDeclare(boolean noDeclare) {
    this.noDeclare = noDeclare;
  }

  public void setNoMethod(boolean noMethod) {
    this.noMethod = noMethod;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public void setTrimRead(boolean trimRead) {
    this.trimRead = trimRead;
  }

  public void setTrimWrite(boolean trimWrite) {
    this.trimWrite = trimWrite;
  }

  public void setWriteOnly(boolean writeOnly) {
    this.writeOnly = writeOnly;
  }

  @Override
  public boolean isAutoSelect() {
    return autoSelect;
  }

  public void setAutoSelect(boolean autoSelect) {
    this.autoSelect = autoSelect;
  }

  @Override
  public boolean isLowerCase() {
    return lowerCase;
  }

  public void setLowerCase(boolean lowerCase) {
    this.lowerCase = lowerCase;
  }

  @Override
  public boolean isMaxCol() {
    return maxCol;
  }

  public void setMaxCol(boolean maxCol) {
    this.maxCol = maxCol;
  }

  @Override
  public boolean isUpperCase() {
    return upperCase;
  }

  public void setUpperCase(boolean upperCase) {
    this.upperCase = upperCase;
  }

  @Override
  public StripMode getStripMode() {
    return stripMode;
  }

  public void setStripMode(StripMode stripMode) {
    this.stripMode = stripMode;
  }

  @Override
  public void validate() throws ModelException {
    if (accessScope == null) {
      throw createModelException("missing access scope");
    }
    if (lowerCase && upperCase) {
      throw createModelException("only one of " + Constants.BIND_LC + " or " + Constants.BIND_UC + " may be set");
    }
  }

  public ModelException createModelException(String message) {
    return new ModelException(message);
  }

}
