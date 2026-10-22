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

import java.time.LocalTime;
import java.util.function.Function;

/**
 * Time translator.<br>
 * Applies to both {@link java.sql.Time} and {@link org.tentackle.common.Time}.
 * 
 * @author harald
 */
@ValueTranslatorService(modelClass = java.sql.Time.class, viewClass = String.class)
public class TimeStringTranslator extends ValueStringTranslator<java.sql.Time> {

  private final LocalTimeStringTranslator translator;

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public TimeStringTranslator(FxTextComponent component) {
    super(component);
    translator = new LocalTimeStringTranslator(component);
  }

  @Override
  public Function<java.sql.Time, String> toViewFunction() {
    return v -> translator.format(v == null ? null : v.toLocalTime());
  }

  @Override
  public Function<String, java.sql.Time> toModelFunction() {
    return s -> {
      LocalTime localTime = translator.parse(s);
      if (localTime == null) {
        return null;
      }
      java.sql.Time sqlTime = java.sql.Time.valueOf(localTime);
      if (java.sql.Time.class == getComponent().getType()) {
        return sqlTime;
      }
      return new org.tentackle.common.Time(sqlTime.getTime());
    };
  }

}
