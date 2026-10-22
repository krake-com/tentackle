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
import java.util.function.Function;

/**
 * Timestamp translator.<br>
 * Applies to both {@link java.sql.Timestamp} and {@link org.tentackle.common.Timestamp}.
 * 
 * @author harald
 */
@ValueTranslatorService(modelClass = java.sql.Timestamp.class, viewClass = String.class)
public class TimestampStringTranslator extends ValueStringTranslator<java.sql.Timestamp> {

  private final LocalDateTimeStringTranslator translator;

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public TimestampStringTranslator(FxTextComponent component) {
    super(component);
    translator = new LocalDateTimeStringTranslator(component);
  }

  @Override
  public Function<java.sql.Timestamp, String> toViewFunction() {
    return v -> translator.format(v == null ? null : v.toLocalDateTime());
  }

  @Override
  public Function<String, java.sql.Timestamp> toModelFunction() {
    return s -> {
      LocalDateTime localDateTime = translator.parse(s);
      if (localDateTime == null) {
        return null;
      }
      java.sql.Timestamp sqlTimestamp = java.sql.Timestamp.valueOf(localDateTime);
      if (java.sql.Timestamp.class == getComponent().getType()) {
        return sqlTimestamp;
      }
      return new org.tentackle.common.Timestamp(sqlTimestamp.getTime());
    };
  }

}
