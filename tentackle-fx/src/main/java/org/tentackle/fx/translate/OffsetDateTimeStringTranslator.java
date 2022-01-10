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

import java.time.OffsetDateTime;

/**
 * Translator for {@link OffsetDateTime}.
 */
@ValueTranslatorService(modelClass = OffsetDateTime.class, viewClass = String.class)
public class OffsetDateTimeStringTranslator extends TemporalStringTranslator<OffsetDateTime> {

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  public OffsetDateTimeStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  protected SmartDateTimeParser<OffsetDateTime> createParser() {
    return new SmartDateTimeParser<>(OffsetDateTime.class, OffsetDateTime::from, OffsetDateTime::truncatedTo,
                                     FormatHelper.getOffsetDateTimeFormatter(), FormatHelper.getTimestampPatternWithOffset(),
                                     getFilters(), getShortcuts(), getHandlers());
  }

}
