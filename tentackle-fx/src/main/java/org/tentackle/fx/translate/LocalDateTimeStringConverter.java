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

import java.time.LocalDateTime;
import java.util.Date;
import java.util.function.Function;

/**
 * LocalDateTime translator.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = LocalDateTime.class, viewClass = String.class)
public class LocalDateTimeStringConverter extends ValueStringTranslator<LocalDateTime> {

  private final DateStringTranslator translator;

  public LocalDateTimeStringConverter(FxTextComponent component) {
    super(component);
    translator = new DateStringTranslator(component);
  }

  @Override
  public Function<LocalDateTime, String> toViewFunction() {
    return v -> translator.format(v == null ? null : java.sql.Timestamp.valueOf(v));
  }

  @Override
  public Function<String, LocalDateTime> toModelFunction() {
    return s -> {
      Date date = translator.parse(s);
      return date == null ? null : new java.sql.Timestamp(date.getTime()).toLocalDateTime();
    };
  }

}
