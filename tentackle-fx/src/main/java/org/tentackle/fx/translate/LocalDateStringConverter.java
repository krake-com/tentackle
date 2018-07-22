/*
 * Tentackle - http://www.tentackle.org
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
import java.util.Date;
import java.util.function.Function;

/**
 * LocalDate translator.
 * 
 * @author harald
 */
@ValueTranslatorService(modelClass = LocalDate.class, viewClass = String.class)
public class LocalDateStringConverter extends ValueStringTranslator<LocalDate> {

  private final DateStringTranslator translator;

  public LocalDateStringConverter(FxTextComponent component) {
    super(component);
    translator = new DateStringTranslator(component);
  }

  @Override
  public Function<LocalDate, String> toViewFunction() {
    return v -> translator.format(v == null ? null : java.sql.Date.valueOf(v));
  }

  @Override
  public Function<String, LocalDate> toModelFunction() {
    return s -> {
      Date date = translator.parse(s);
      return date == null ? null : new java.sql.Date(date.getTime()).toLocalDate();
    };
  }

}
