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

package org.tentackle.fx.translate;

import javafx.scene.Node;

import org.tentackle.bind.BindingException;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.bind.FxComponentBinding;
import org.tentackle.misc.DateTimeUtilities;
import org.tentackle.misc.ObjectUtilities;
import org.tentackle.misc.time.NumberShortcut;
import org.tentackle.misc.time.SmartDateTimeParser;

import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * String translator for date- and/or time types based on {@link Temporal}.<br>
 *
 * @param <T> the {@link Temporal} type
 */
public abstract class TemporalStringTranslator<T extends Temporal> extends ValueStringTranslator<T>
       implements NumberShortcut.WindowProvider, SmartDateTimeParser.Shortcut {

  /**
   * Component property for a reference date supplier of type Supplier&lt;T&gt;.<br>
   * '@' as input will be replaced by the supplier's date.<br>
   * Example:
   * <pre>
   *   blahField.getProperties.put(REFERENCE_DATE_SUPPLIER, (Supplier&lt;LocalDate&gt;) () -&gt; otherDate);
   * </pre>
   * @see #connectAsReferenceDateSuppliers(FxTextComponent, FxTextComponent)
   */
  public static final String REFERENCE_DATE_SUPPLIER = "referenceDateSupplier";

  /**
   * Property to define the sliding year input window.
   * <p>
   * Example:
   * <pre>
   *   blahField.getProperties().put(TemporalStringTranslator.YEAR_WINDOW, 30);
   * </pre>
   * @see NumberShortcut.WindowProvider#getYearWindow()
   */
  public static final String YEAR_WINDOW = "yearWindow";

  /**
   * Property to define the sliding month input window.
   * <p>
   * Example:
   * <pre>
   *   blahField.getProperties().put(TemporalStringTranslator.MONTH_WINDOW, 2);
   * </pre>
   * @see NumberShortcut.WindowProvider#getMonthWindow()
   */
  public static final String MONTH_WINDOW = "monthWindow";

  /**
   * Property to define the sliding day input window.
   * <p>
   * Example:
   * <pre>
   *   blahField.getProperties().put(TemporalStringTranslator.DAY_WINDOW, 1);
   * </pre>
   * @see NumberShortcut.WindowProvider#getDayWindow()
   */
  public static final String DAY_WINDOW = "dayWindow";


  /**
   * Connects two fields as reference date suppliers.<br>
   * Each field provides the reference date for the other one.<br>
   * Replaces code like:
   * <pre>
   *   fromField.getProperties().put(TemporalStringTranslator.REFERENCE_DATE_SUPPLIER, (Supplier<Date>) () -&gt; until);
   *   untilField.getProperties().put(TemporalStringTranslator.REFERENCE_DATE_SUPPLIER, (Supplier<Date>) () -&gt; from);
   * </pre>
   * by
   * <pre>
   *   TemporalStringTranslator.connectAsReferenceDateSuppliers(fromField, untilField);
   * </pre>
   * Throws {@link BindingException} if one of the fields is not bound.
   *
   * @param a the first component
   * @param b the second component
   */
  @SuppressWarnings("rawtypes")
  public static void connectAsReferenceDateSuppliers(FxTextComponent a, FxTextComponent b) {
    FxComponentBinding bindingA = a.getBinding();
    FxComponentBinding bindingB = b.getBinding();
    if (bindingA == null || bindingB == null) {
      throw new BindingException("both components must be bound to the model");
    }
    ((Node) a).getProperties().put(REFERENCE_DATE_SUPPLIER, (Supplier) bindingB::getModelValue);
    ((Node) b).getProperties().put(REFERENCE_DATE_SUPPLIER, (Supplier) bindingA::getModelValue);
  }


  private List<SmartDateTimeParser.TextFilter> filters;       // the input filters
  private List<SmartDateTimeParser.Shortcut> shortcuts;       // the input shortcuts
  private T lastValue;                                        // last processed date or time
  private SmartDateTimeParser<T> parser;                      // the smart parser

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public TemporalStringTranslator(FxTextComponent component) {
    super(component);
  }


  /**
   * Gets the reference date supplier.
   *
   * @return the date supplier, null if none
   */
  protected Supplier<?> getReferenceDateSupplier() {
    return (Supplier<?>) ((Node) getComponent()).getProperties().get(REFERENCE_DATE_SUPPLIER);
  }


  @Override
  public int getYearWindow() {
    Integer val = (Integer) ((Node) getComponent()).getProperties().get(YEAR_WINDOW);
    return val == null ? getDefaultYearWindow() : val;
  }

  /**
   * Gets the default value for {@link #getYearWindow()}.
   *
   * @return the default (50 years)
   */
  protected int getDefaultYearWindow() {
    return 50;
  }


  @Override
  public int getMonthWindow() {
    Integer val = (Integer) ((Node) getComponent()).getProperties().get(MONTH_WINDOW);
    return val == null ? getDefaultMonthWindow() : val;
  }

  /**
   * Gets the default month window for {@link #getMonthWindow()}.
   *
   * @return the default (-1 == disabled)
   */
  protected int getDefaultMonthWindow() {
    return -1;
  }

  @Override
  public int getDayWindow() {
    Integer val = (Integer) ((Node) getComponent()).getProperties().get(DAY_WINDOW);
    return val == null ? getDefaultDayWindow() : val;
  }

  /**
   * Gets the default day window for {@link #getDayWindow()}.
   *
   * @return the default (-1 == disabled)
   */
  protected int getDefaultDayWindow() {
    return -1;
  }


  @Override
  public SmartDateTimeParser.TextRange test(SmartDateTimeParser<? extends Temporal> parser, String text) {
    if (!text.isEmpty()) {
      char c = text.charAt(0);
      if (c == '@') {
        return new SmartDateTimeParser.TextRange(0, 1);
      }
    }
    return null;
  }

  @Override
  @SuppressWarnings("unchecked")
  public <V extends Temporal> V parse(SmartDateTimeParser<V> parser, V currentValue, String part) {
    Object refValue = null;
    Supplier<?> dateSupplier = getReferenceDateSupplier();
    if (dateSupplier != null) {
      refValue = dateSupplier.get();
    }
    return (V) ObjectUtilities.getInstance().convert(getParser().getType(), refValue);
  }

  /**
   * Gets the list of default shortcuts.
   *
   * @return the list of shortcuts
   */
  public List<SmartDateTimeParser.Shortcut> getShortcuts() {
    if (shortcuts == null) {
      List<SmartDateTimeParser.Shortcut> scList = new ArrayList<>();
      scList.add(this);    // @ shortcut comes first
      scList.addAll(DateTimeUtilities.getInstance().getShortcuts());
      shortcuts = scList;
    }
    return shortcuts;
  }

  /**
   * Gets the list of error handlers.
   *
   * @return the handlers
   */
  public List<SmartDateTimeParser.ErrorHandler> getHandlers() {
    return DateTimeUtilities.getInstance().getHandlers();
  }

  /**
   * Gets the list of filters.
   *
   * @return the list of filters
   */
  public List<SmartDateTimeParser.TextFilter> getFilters() {
    if (filters == null) {
      filters = DateTimeUtilities.getInstance().getFilters();
    }
    return filters;
  }

  protected abstract SmartDateTimeParser<T> createParser();

  protected SmartDateTimeParser<T> getParser() {
    if (parser == null) {
      parser = createParser();
    }
    return parser;
  }

  /**
   * Formats the given value.
   *
   * @param value the temporal value
   * @return the formatted string
   */
  protected String format(T value) {
    lastValue = value;
    return getParser().format(value);
  }

  protected T getLastValue() {
    return lastValue;
  }

  protected T parse(String s) {
    return getParser().parse(this::getLastValue, s);
  }

  @Override
  public Function<T, String> toViewFunction() {
    return this::format;
  }

  @Override
  public Function<String, T> toModelFunction() {
    return this::parse;
  }

}
