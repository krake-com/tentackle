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

import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.misc.FormatHelper;

/**
 * Abstract base class for number translators.
 *
 * @author harald
 * @param <M> the model's number type
 */
public abstract class NumberStringTranslator<M extends Number> extends ValueStringTranslator<M> {

  private String pattern;         // the cached pattern
  private DecimalFormat format;   // the cached format

  /**
   * Creates a number translator.
   *
   * @param component the text component
   */
  public NumberStringTranslator(FxTextComponent component) {
    super(component);
  }


  /**
   * Parses a string to a number.
   *
   * @param s the string
   * @return the number
   */
  protected Number parse(String s) {
    try {
      return getFormat().parse(s);
    }
    catch (ParseException pex) {
      getComponent().setErrorOffset(pex.getErrorOffset());
          // ParseExceptions are not localized, unfortunately
      getComponent().setError(MessageFormat.format(FxFxBundle.getString("INVALID NUMBER: {0}"), s));
      throw new FxRuntimeException("conversion failed for '" + s + "'", pex);
    }
  }

  @Override
  public String getValidChars() {
    return getComponent().isUnsigned() ? ".,0123456789" : ".,-0123456789";
  }

  /**
   * Gets the default pattern according to the type.
   *
   * @return the pattern
   */
  abstract public String getDefaultPattern();

  /**
   * Gets the decimal format.
   *
   * @return the decimal format
   */
  protected DecimalFormat getFormat() {
    String pat = getComponent().getPattern();
    if (pat == null) {
      pat = getDefaultPattern();
    }
    if (format == null || !pat.equals(pattern)) {
      pattern = pat;
      format = new DecimalFormat(pattern);
      FormatHelper.setScale(format, getComponent().getScale());
    }
    return format;
  }

}
