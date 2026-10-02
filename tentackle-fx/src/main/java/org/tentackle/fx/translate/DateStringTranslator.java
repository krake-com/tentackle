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

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * Date translator.<br>
 * Applies to {@link java.util.Date}, {@link java.sql.Date} and {@link org.tentackle.common.Date}.
 * 
 * @author harald
 */
@ValueTranslatorService(modelClass = java.util.Date.class, viewClass = String.class)
public class DateStringTranslator extends ValueStringTranslator<java.util.Date> {

  private final LocalDateStringTranslator translator;

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public DateStringTranslator(FxTextComponent component) {
    super(component);
    translator = new LocalDateStringTranslator(component);
  }

  @Override
  public Function<java.util.Date, String> toViewFunction() {
    return v -> translator.format(v == null ? null : toSqlDate(v).toLocalDate());
  }

  @Override
  public Function<String, java.util.Date> toModelFunction() {
    return s -> {
      LocalDate localdate = translator.parse(s);
      if (localdate == null) {
        return null;
      }
      java.util.Date sqlDate = java.sql.Date.valueOf(localdate);
      if (java.sql.Date.class == getComponent().getType()) {
        return sqlDate;
      }
      else if (java.util.Date.class == getComponent().getType()) {
        return new java.util.Date(sqlDate.getTime());
      }
      return new org.tentackle.common.Date(sqlDate.getTime());
    };
  }


  private java.sql.Date toSqlDate(java.util.Date date) {
    return date instanceof java.sql.Date ? (java.sql.Date) date : new java.sql.Date(date.getTime());
  }

}
