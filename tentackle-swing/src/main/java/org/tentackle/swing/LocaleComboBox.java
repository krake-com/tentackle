/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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


package org.tentackle.swing;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import org.tentackle.common.Compare;
import org.tentackle.misc.ShortLongText;

/**
 * A Combobox of Locales.
 *
 * @author harald
 */
@SuppressWarnings("serial")
public class LocaleComboBox extends FormComboBox<ShortLongText> {


  /**
   * Creates a combobox for locales.
   */
  public LocaleComboBox() {
    setShortLongPopupEnabled(true);
    setMultiKeySelectionManager(true);
    Locale locales[] = Locale.getAvailableLocales();
    TLocale tlocales[] = new TLocale[locales.length];
    for (int i=0; i < locales.length; i++) {
      tlocales[i] = new TLocale(locales[i]);
    }
    Arrays.sort(tlocales);
    addAllItems(tlocales);
  }



  /**
   * Sets the selected locale.
   *
   * @param locale the locale, null to deselect
   */
  public void setLocaleValue(Locale locale)  {
    setFormValue(locale == null ? null : new TLocale(locale));
  }

  /**
   * Gets the selected locale.
   * @return the locale, null nothing selected
   */
  public Locale getLocaleValue() {
    TLocale tloc = (TLocale)getFormValue();
    return tloc == null ? null : tloc.locale;
  }


  /**
   * to implement long/short display.
   * The class Locale is final, so we must decorate and cannot extend.
   */
  private static class TLocale implements ShortLongText, Comparable<TLocale> {

    private final Locale locale;

    public TLocale(Locale locale) {
      this.locale = locale;
    }

    @Override
    public String toString()  {
      return locale.toString();
    }

    @Override
    public String getShortText() {
      return locale.toString();
    }

    @Override
    public String getLongText() {
      return locale.getDisplayName();
    }

    public Locale getLocale() {
      return locale;
    }

    @Override
    public boolean equals(Object obj) {
      return Objects.equals(locale, obj instanceof TLocale ? ((TLocale)obj).locale : null);
    }

    @Override
    public int hashCode() {
      int hash = 7;
      hash = 61 * hash + Objects.hashCode(this.locale);
      return hash;
    }

    @Override
    public int compareTo(LocaleComboBox.TLocale o) {
      return Compare.compare(locale.toString(), o == null ? null : o.locale.toString());
    }
  }

}
