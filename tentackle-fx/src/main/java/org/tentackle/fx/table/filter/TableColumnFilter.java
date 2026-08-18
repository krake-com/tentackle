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

package org.tentackle.fx.table.filter;

import org.tentackle.common.LocaleProvider;
import org.tentackle.common.StringNormalizer;
import org.tentackle.fx.bind.FxTableBinding;
import org.tentackle.fx.table.TableColumnConfiguration;
import org.tentackle.log.Logger;
import org.tentackle.misc.CsvConverter;
import org.tentackle.misc.DateTimeUtilities;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The filter of a single table column.<p>
 * A column filter consists of two parts that are combined by a logical <em>and</em>:
 * <ul>
 *   <li>the <em>value set</em>, i.e. the displayed texts the user has selected from the
 *       column's distinct values (as known from the autofilter of spreadsheet applications), and</li>
 *   <li>the <em>condition</em>, i.e. an operator with up to two operands.</li>
 * </ul>
 * Both parts are optional. A column filter that is neither restricted by a value set nor
 * by a condition is {@link #isActive() inactive} and lets all rows pass.
 * <p>
 * Values are matched by their <em>displayed text</em> and not by {@link Object#equals(Object)},
 * since the values of a column often provide no useful equality (PDOs, for example) and since
 * the user selects what is displayed. Only the ordering operators fall back to the natural order
 * of the values.
 *
 * @param <S> type of the objects contained within the table's or treetable's items list
 * @param <T> type of the content in all cells of the filtered column
 * @author harald
 */
public class TableColumnFilter<S,T> {

  /** the format version of the preferences string. */
  private static final String PREFERENCES_VERSION = "1";

  /** the separator of the preferences string. */
  private static final String PREFERENCES_SEPARATOR = ",";

  /** number of fixed fields preceding the selected texts in the preferences string. */
  private static final int PREFERENCES_FIXED_FIELDS = 6;

  private static final Logger LOGGER = Logger.get(TableColumnFilter.class);


  private final TableFilter<S> tableFilter;
  private final TableColumnConfiguration<S,T> columnConfiguration;

  /** the selected displayed texts, null if all values are selected. */
  private Set<String> selectedTexts;

  /** whether blank values are selected (only relevant if the value set is active). */
  private boolean blankSelected = true;

  /** the condition's operator, never null. */
  private TableFilterOperator operator = TableFilterOperator.NONE;

  /** the condition's first operand. */
  private String firstOperand;

  /** the condition's second operand. */
  private String secondOperand;

  /** lazily created collator to compare non-comparable values. */
  private Collator collator;


  /**
   * Creates a column filter.
   *
   * @param tableFilter the filter of the whole table
   * @param columnConfiguration the column configuration
   */
  public TableColumnFilter(TableFilter<S> tableFilter, TableColumnConfiguration<S,T> columnConfiguration) {
    this.tableFilter = tableFilter;
    this.columnConfiguration = columnConfiguration;
  }


  @Override
  public String toString() {
    return columnConfiguration.getName() + "=" + toPreferencesString();
  }


  /**
   * Gets the filter of the whole table.
   *
   * @return the table filter
   */
  public TableFilter<S> getTableFilter() {
    return tableFilter;
  }

  /**
   * Gets the configuration of the filtered column.
   *
   * @return the column configuration
   */
  public TableColumnConfiguration<S,T> getColumnConfiguration() {
    return columnConfiguration;
  }


  // ------------------- the value set -------------------

  /**
   * Gets the selected displayed texts.
   *
   * @return the selected texts, null if all values are selected
   */
  public Set<String> getSelectedTexts() {
    return selectedTexts;
  }

  /**
   * Sets the selected displayed texts.
   *
   * @param selectedTexts the selected texts, null if all values are selected
   */
  public void setSelectedTexts(Collection<String> selectedTexts) {
    this.selectedTexts = selectedTexts == null ? null : new LinkedHashSet<>(selectedTexts);
    fireChanged();
  }

  /**
   * Returns whether blank values are selected.<br>
   * Only relevant if the value set is active.
   *
   * @return true if blank values pass the filter (default)
   */
  public boolean isBlankSelected() {
    return blankSelected;
  }

  /**
   * Sets whether blank values are selected.
   *
   * @param blankSelected true if blank values pass the filter
   */
  public void setBlankSelected(boolean blankSelected) {
    this.blankSelected = blankSelected;
    fireChanged();
  }

  /**
   * Returns whether the value set restricts the rows.
   *
   * @return true if the value set is active
   */
  public boolean isValueSetActive() {
    return selectedTexts != null;
  }


  // ------------------- the condition -------------------

  /**
   * Gets the condition's operator.
   *
   * @return the operator, never null, {@link TableFilterOperator#NONE} if no condition
   */
  public TableFilterOperator getOperator() {
    return operator;
  }

  /**
   * Sets the condition's operator.
   *
   * @param operator the operator, null is treated as {@link TableFilterOperator#NONE}
   */
  public void setOperator(TableFilterOperator operator) {
    this.operator = operator == null ? TableFilterOperator.NONE : operator;
    fireChanged();
  }

  /**
   * Gets the condition's first operand.
   *
   * @return the first operand, null if none
   */
  public String getFirstOperand() {
    return firstOperand;
  }

  /**
   * Gets the condition's second operand.
   *
   * @return the second operand, null if none
   */
  public String getSecondOperand() {
    return secondOperand;
  }

  /**
   * Sets the condition's operands.
   *
   * @param firstOperand the first operand, null if none
   * @param secondOperand the second operand, null if none
   */
  public void setOperands(String firstOperand, String secondOperand) {
    this.firstOperand = firstOperand;
    this.secondOperand = secondOperand;
    fireChanged();
  }

  /**
   * Applies the value set and the condition in one go.<br>
   * In contrast to the individual setters, the filter is notified only once.
   *
   * @param selectedTexts the selected texts, null if all values are selected
   * @param blankSelected true if blank values pass the filter
   * @param operator the condition's operator, null is treated as {@link TableFilterOperator#NONE}
   * @param firstOperand the condition's first operand, null if none
   * @param secondOperand the condition's second operand, null if none
   */
  public void apply(Collection<String> selectedTexts, boolean blankSelected,
                    TableFilterOperator operator, String firstOperand, String secondOperand) {
    this.selectedTexts = selectedTexts == null ? null : new LinkedHashSet<>(selectedTexts);
    this.blankSelected = blankSelected;
    this.operator = operator == null ? TableFilterOperator.NONE : operator;
    this.firstOperand = firstOperand;
    this.secondOperand = secondOperand;
    fireChanged();
  }

  /**
   * Returns whether the condition restricts the rows.<br>
   * A condition missing one of its operands is not active.
   *
   * @return true if the condition is active
   */
  public boolean isConditionActive() {
    return switch (operator.getOperandCount()) {
      case 0 -> operator != TableFilterOperator.NONE;
      case 1 -> !isEmpty(firstOperand);
      default -> !isEmpty(firstOperand) && !isEmpty(secondOperand);
    };
  }


  // ------------------- evaluation -------------------

  /**
   * Returns whether this filter restricts the rows at all.
   *
   * @return true if active
   */
  public boolean isActive() {
    return isValueSetActive() || isConditionActive();
  }

  /**
   * Clears the filter.<br>
   * The filter becomes inactive.
   */
  public void clear() {
    selectedTexts = null;
    blankSelected = true;
    operator = TableFilterOperator.NONE;
    firstOperand = null;
    secondOperand = null;
    fireChanged();
  }

  /**
   * Tests whether a row passes this filter.
   *
   * @param row the row object, may be null
   * @return true if the row passes
   */
  public boolean test(S row) {
    return !isActive() || testValue(getValue(row));
  }

  /**
   * Tests whether a column value passes this filter.
   *
   * @param value the column's value, may be null
   * @return true if the value passes
   */
  public boolean testValue(T value) {
    if (!isActive()) {
      return true;
    }
    String text = getText(value);
    boolean blank = isBlank(text);
    if (isValueSetActive() && !testValueSet(text, blank)) {
      return false;
    }
    return !isConditionActive() || testCondition(value, text, blank);
  }

  /**
   * Gets the column's value for a row.
   *
   * @param row the row object, may be null
   * @return the value, null if the column is not bound
   */
  protected T getValue(S row) {
    FxTableBinding<S,T> binding = columnConfiguration.getBinding();
    if (binding == null) {
      return null;
    }
    binding.setBoundRootObject(row);
    return binding.getModelValue();
  }

  /**
   * Gets the text displayed for a value.
   *
   * @param value the value, may be null
   * @return the displayed text, null if none
   */
  protected String getText(T value) {
    return columnConfiguration.getText(value);
  }

  /**
   * Tests the value set.
   *
   * @param text the displayed text of the value
   * @param blank true if the value is blank
   * @return true if the value passes the value set
   */
  protected boolean testValueSet(String text, boolean blank) {
    return blank ? blankSelected : selectedTexts.contains(text);
  }

  /**
   * Tests the condition.
   *
   * @param value the value, may be null
   * @param text the displayed text of the value
   * @param blank true if the value is blank
   * @return true if the value passes the condition
   */
  protected boolean testCondition(T value, String text, boolean blank) {
    switch (operator) {
      case EMPTY:
        return blank;

      case NOT_EMPTY:
        return !blank;

      default:
        break;
    }

    if (blank) {
      return false;   // blank values never match a condition with operands
    }

    if (operator.isTextual()) {
      String normalizedText = normalize(text);
      String normalizedOperand = normalize(firstOperand);
      return switch (operator) {
        case CONTAINS -> normalizedText.contains(normalizedOperand);
        case NOT_CONTAINS -> !normalizedText.contains(normalizedOperand);
        case STARTS_WITH -> normalizedText.startsWith(normalizedOperand);
        default -> normalizedText.endsWith(normalizedOperand);
      };
    }

    if (operator.isOrdering()) {
      Comparable<?> left = toComparable(value, text);
      int firstResult = compare(left, parseOperand(firstOperand, text));
      return switch (operator) {
        case LESS -> firstResult < 0;
        case LESS_OR_EQUAL -> firstResult <= 0;
        case GREATER -> firstResult > 0;
        case GREATER_OR_EQUAL -> firstResult >= 0;
        default -> {
          int secondResult = compare(left, parseOperand(secondOperand, text));
          boolean within = firstResult >= 0 && secondResult <= 0 || firstResult <= 0 && secondResult >= 0;
          yield operator == TableFilterOperator.BETWEEN == within;
        }
      };
    }

    // EQUAL and NOT_EQUAL compare the displayed texts
    boolean equal = normalize(text).equals(normalize(firstOperand));
    return operator == TableFilterOperator.EQUAL == equal;
  }


  // ------------------- comparing -------------------

  /**
   * Converts a value to a comparable object.<br>
   * The result must be comparable to the result of {@link #parseOperand(String, String)}.
   *
   * @param value the value, never null
   * @param text the displayed text of the value
   * @return the comparable object, never null
   */
  protected Comparable<?> toComparable(T value, String text) {
    if (value instanceof Number number) {
      Comparable<?> decimal = toBigDecimal(number.toString());
      if (decimal != null) {
        return decimal;
      }
    }
    else if (value instanceof Comparable<?> comparable && isParsableType()) {
      return comparable;
    }
    return normalize(text);
  }

  /**
   * Parses an operand into a comparable object.
   *
   * @param operand the operand as typed by the user, never null
   * @param text the displayed text of the value the operand is compared to
   * @return the comparable object, never null
   */
  protected Comparable<?> parseOperand(String operand, String text) {
    Class<T> type = columnConfiguration.getType();
    if (type != null) {
      if (Number.class.isAssignableFrom(type)) {
        Comparable<?> decimal = parseNumber(operand);
        if (decimal != null) {
          return decimal;
        }
      }
      else {
        Comparable<?> temporal = parseTemporal(type, operand);
        if (temporal != null) {
          return temporal;
        }
      }
    }
    return normalize(operand);
  }

  /**
   * Returns whether operands of the column's type can be parsed.
   *
   * @return true if the values can be compared by their natural order
   */
  protected boolean isParsableType() {
    Class<T> type = columnConfiguration.getType();
    return type != null && (Number.class.isAssignableFrom(type) || isTemporalType(type));
  }

  /**
   * Compares two comparables leniently.<br>
   * Objects of different types are compared by their string representation.
   *
   * @param left the left object
   * @param right the right object
   * @return the comparison result
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected int compare(Comparable<?> left, Comparable<?> right) {
    if (left.getClass() == right.getClass() || left.getClass().isInstance(right)) {
      return ((Comparable) left).compareTo(right);
    }
    return getCollator().compare(left.toString(), right.toString());
  }

  /**
   * Gets the collator used to compare texts.
   *
   * @return the collator, never null
   */
  protected Collator getCollator() {
    if (collator == null) {
      collator = Collator.getInstance(LocaleProvider.getInstance().getEffectiveLocale());
      collator.setStrength(Collator.SECONDARY);   // case insensitive
    }
    return collator;
  }

  /**
   * Normalizes a text for case- and diacritic-insensitive matching.
   *
   * @param text the text, may be null
   * @return the normalized text, never null
   */
  protected String normalize(String text) {
    if (text == null) {
      return "";
    }
    String normalized = StringNormalizer.getInstance().unDiacrit(text, true);
    return (normalized == null ? text : normalized).toUpperCase(Locale.ENGLISH).trim();
  }

  /**
   * Parses a number according to the column's format.
   *
   * @param operand the operand
   * @return the number, null if not parsable
   */
  protected BigDecimal parseNumber(String operand) {
    DecimalFormat format = columnConfiguration.getNumberFormat();
    if (format != null) {
      try {
        synchronized (format) {
          format.setParseBigDecimal(true);
          Number number = format.parse(operand.trim());
          if (number instanceof BigDecimal decimal) {
            return decimal;
          }
          return new BigDecimal(number.toString());
        }
      }
      catch (RuntimeException | java.text.ParseException ex) {
        LOGGER.fine("cannot parse ''{0}'' according to the column's format", operand);
      }
    }
    return toBigDecimal(operand);
  }

  /**
   * Parses a date or time value.
   *
   * @param type the column type
   * @param operand the operand
   * @return the temporal value, null if the type is no date/time type or the operand is not parsable
   */
  protected Comparable<?> parseTemporal(Class<T> type, String operand) {
    String text = operand.trim();
    try {
      if (LocalDate.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseLocalDate(text);
      }
      if (LocalTime.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseLocalTime(text);
      }
      if (LocalDateTime.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseLocalDateTime(text);
      }
      if (OffsetTime.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseOffsetTime(text);
      }
      if (OffsetDateTime.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseOffsetDateTime(text);
      }
      if (ZonedDateTime.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseZonedDateTime(text);
      }
      if (Instant.class.isAssignableFrom(type)) {
        return DateTimeUtilities.parseLocalDateTime(text).atZone(java.time.ZoneId.systemDefault()).toInstant();
      }
    }
    catch (RuntimeException ex) {
      LOGGER.fine("cannot parse ''{0}'' as {1}", operand, type);
    }
    return null;
  }

  /**
   * Returns whether a type is a date or time type.
   *
   * @param type the type
   * @return true if a date or time type
   */
  protected boolean isTemporalType(Class<?> type) {
    return java.time.temporal.Temporal.class.isAssignableFrom(type) ||
           java.util.Date.class.isAssignableFrom(type);
  }


  // ------------------- preferences -------------------

  /**
   * Converts this filter to a string that can be stored in the preferences.
   *
   * @return the string, never null
   * @see #fromPreferencesString(String)
   */
  public String toPreferencesString() {
    List<String> fields = new ArrayList<>();
    fields.add(PREFERENCES_VERSION);
    fields.add(operator.name());
    fields.add(firstOperand == null ? "" : firstOperand);
    fields.add(secondOperand == null ? "" : secondOperand);
    fields.add(blankSelected ? "1" : "0");
    // the number of selected texts, -1 if the value set is inactive.
    // A count is stored rather than a flag, since the CSV representation drops trailing empty fields.
    fields.add(isValueSetActive() ? Integer.toString(selectedTexts.size()) : "-1");
    if (isValueSetActive()) {
      fields.addAll(selectedTexts);
    }
    return CsvConverter.getInstance().toCsv(PREFERENCES_SEPARATOR, List.of(fields)).getFirst();
  }

  /**
   * Applies a filter stored in the preferences.<br>
   * Unparsable or outdated strings clear the filter instead of throwing an exception.
   *
   * @param text the string, null or empty to clear the filter
   * @see #toPreferencesString()
   */
  public void fromPreferencesString(String text) {
    selectedTexts = null;
    blankSelected = true;
    operator = TableFilterOperator.NONE;
    firstOperand = null;
    secondOperand = null;
    if (text != null && !text.isEmpty()) {
      try {
        List<String> fields = CsvConverter.getInstance().fromCsv(PREFERENCES_SEPARATOR.charAt(0), List.of(text)).getFirst();
        if (fields.size() >= 2 && PREFERENCES_VERSION.equals(fields.getFirst())) {
          operator = TableFilterOperator.valueOf(fields.get(1));
          firstOperand = emptyToNull(field(fields, 2));
          secondOperand = emptyToNull(field(fields, 3));
          blankSelected = !"0".equals(field(fields, 4));
          int valueCount = Integer.parseInt(field(fields, 5).isEmpty() ? "-1" : field(fields, 5));
          if (valueCount >= 0) {
            selectedTexts = new LinkedHashSet<>();
            for (int index = 0; index < valueCount; index++) {
              // trailing empty values are dropped by the CSV representation and are restored here
              selectedTexts.add(field(fields, PREFERENCES_FIXED_FIELDS + index));
            }
          }
        }
        else {
          LOGGER.fine("unsupported filter preferences for column {0}: {1}", columnConfiguration.getName(), text);
        }
      }
      catch (RuntimeException ex) {
        LOGGER.fine("invalid filter preferences for column " + columnConfiguration.getName() + ": " + text, ex);
        selectedTexts = null;
        operator = TableFilterOperator.NONE;
        firstOperand = null;
        secondOperand = null;
      }
    }
    fireChanged();
  }


  /**
   * Notifies the table filter that this column filter has been modified.
   */
  protected void fireChanged() {
    tableFilter.fireChanged();
  }


  private static BigDecimal toBigDecimal(String text) {
    try {
      return new BigDecimal(text.trim());
    }
    catch (RuntimeException ex) {
      return null;
    }
  }

  private static String field(List<String> fields, int index) {
    return index < fields.size() ? fields.get(index) : "";
  }

  private static String emptyToNull(String text) {
    return text.isEmpty() ? null : text;
  }

  private static boolean isEmpty(String text) {
    return text == null || text.isEmpty();
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }

}
