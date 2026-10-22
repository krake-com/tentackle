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

import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.misc.FormatHelper;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.text.ParseException;

/**
 * Abstract base class for number translators.
 *
 * @author harald
 * @param <M> the model's number type
 */
public abstract class NumberStringTranslator<M extends Number> extends ValueStringTranslator<M> {

  private static final String DIGITS = "0123456789";

  /** The cached pattern. */
  protected String pattern;

  /** The cached format. */
  protected DecimalFormat format;

  /** Cached valid chars for signed numbers. */
  protected String signedValidChars;

  /** Cached valid chars for unsigned numbers. */
  protected String unsignedValidChars;


  /**
   * Creates a number translator.
   *
   * @param component the text component
   */
  public NumberStringTranslator(FxTextComponent component) {
    super(component);
  }

  @Override
  public String getValidChars() {
    if (signedValidChars == null) {
      initializeValidChars();
    }
    FxTextComponent component = getComponent();
    return component != null && component.isUnsigned() ? unsignedValidChars : signedValidChars;
  }

  /**
   * Gets the default pattern according to the type.
   *
   * @return the pattern
   */
  public abstract String getDefaultPattern();


  /**
   * Gets the decimal format.<br>
   * Created from the component's pattern, or the {@linkplain #getDefaultPattern() default pattern},
   * and the component's scale. The format is re-created if the pattern changes.
   *
   * @return the decimal format
   */
  protected DecimalFormat getFormat() {
    FxTextComponent component = getComponent();
    String pat = component == null ? null : component.getPattern();
    if (pat == null) {
      pat = getDefaultPattern();
    }
    if (format == null || !pat.equals(pattern)) {
      pattern = pat;
      format = new DecimalFormat(pattern);
      if (component != null) {
        FormatHelper.setScale(format, component.getScale());
      }
    }
    return format;
  }

  /**
   * Initializes the valid chars from the format.
   */
  protected void initializeValidChars() {
    DecimalFormatSymbols symbols = getFormat().getDecimalFormatSymbols();
    unsignedValidChars = DIGITS + symbols.getDecimalSeparator() + symbols.getGroupingSeparator();
    signedValidChars = unsignedValidChars + symbols.getMinusSign();
  }

  /**
   * Parses a string to a number.<br>
   * On failure, the error and error offset are set in the component.
   *
   * @param s the string
   * @return the number, null if the string is null
   * @throws FxRuntimeException if parsing failed
   */
  protected Number parse(String s) {
    if (s != null) {
      try {
        return getFormat().parse(s);
      }
      catch (ParseException pex) {
        getComponent().setErrorOffset(pex.getErrorOffset());
        // ParseExceptions are not localized, unfortunately
        getComponent().setError(MessageFormat.format(FxFxBundle.getString("INVALID NUMBER: {0}"), s));
        getComponent().setErrorTemporary(true);
        throw new FxRuntimeException("conversion failed for '" + s + "'", pex);
      }
    }
    return null;
  }

}
