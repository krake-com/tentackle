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
import org.tentackle.misc.FormatHelper;
import org.tentackle.misc.time.SmartDateTimeParser;

import java.time.OffsetTime;

/**
 * Translator for {@link OffsetTime}.
 */
@ValueTranslatorService(modelClass = OffsetTime.class, viewClass = String.class)
public class OffsetTimeStringTranslator extends TemporalStringTranslator<OffsetTime> {

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public OffsetTimeStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  protected SmartDateTimeParser<OffsetTime> createParser() {
    return new SmartDateTimeParser<>(OffsetTime.class, OffsetTime::from, OffsetTime::truncatedTo,
                                     FormatHelper.getOffsetTimeFormatter(), FormatHelper.getTimePatternWithOffset(),
                                     getFilters(), getShortcuts(), getHandlers());
  }

}
