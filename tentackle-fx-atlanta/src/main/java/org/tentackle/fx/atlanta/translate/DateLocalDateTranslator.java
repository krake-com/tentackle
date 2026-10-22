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

package org.tentackle.fx.atlanta.translate;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.translate.AbstractValueTranslator;

import java.time.LocalDate;
import java.util.Date;
import java.util.function.Function;

/**
 * Date to LocalDate translator.<br>
 * Provided for the {@link atlantafx.base.controls.Calendar} control.
 * <p>
 * Applies to {@link java.util.Date} and all its subclasses. When translating back to the model,
 * the returned object is of the same class as the component's model type:
 * {@link java.util.Date}, {@link java.sql.Date}, {@link java.sql.Timestamp},
 * {@link org.tentackle.common.Timestamp} or, for all other types, {@link org.tentackle.common.Date}.<br>
 * Timestamps are set to the start of the picked day in the default time zone.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = Date.class, viewClass = LocalDate.class)
public class DateLocalDateTranslator extends AbstractValueTranslator<Date, LocalDate> {

  /**
   * Creates a date to local date translator.
   *
   * @param component the component
   */
  public DateLocalDateTranslator(FxComponent component) {
    super(component);
  }

  @Override
  public Function<Date, LocalDate> toViewFunction() {
    return v -> v == null ? null : toSqlDate(v).toLocalDate();
  }

  @Override
  public Function<LocalDate, Date> toModelFunction() {
    return d -> {
      if (d == null) {
        return null;
      }
      java.util.Date sqlDate = java.sql.Date.valueOf(d);
      Class<?> type = getComponent().getType();
      if (java.sql.Date.class == type) {
        return sqlDate;
      }
      if (java.util.Date.class == type) {
        return new java.util.Date(sqlDate.getTime());
      }
      if (org.tentackle.common.Timestamp.class == type) {
        return new org.tentackle.common.Timestamp(sqlDate.getTime());
      }
      if (java.sql.Timestamp.class == type) {
        return new java.sql.Timestamp(sqlDate.getTime());
      }
      return new org.tentackle.common.Date(sqlDate.getTime());
    };
  }


  private java.sql.Date toSqlDate(java.util.Date date) {
    return date instanceof java.sql.Date ? (java.sql.Date) date : new java.sql.Date(date.getTime());
  }

}
