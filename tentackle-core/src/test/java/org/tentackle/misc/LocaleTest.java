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
package org.tentackle.misc;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Figures out what happens when locales are created with illegal language/country combinations.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LocaleTest {

  @Test
  public void testLocale() {
    Locale locale = new Locale("en", "US");
    SimpleDateFormat format = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale);
    Assert.assertEquals(format.toPattern(), "MMM d, y, h:mm:ss a");
    Assert.assertEquals(format.toLocalizedPattern(), "MMM d, y, h:mm:ss a");
    locale = new Locale("en", "DE");
    format = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale);
    Assert.assertEquals(format.toPattern(), "d MMM y, HH:mm:ss");
    Assert.assertEquals(format.toLocalizedPattern(), "d MMM y, HH:mm:ss");
    locale = new Locale("de", "US");
    format = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale);
    Assert.assertEquals(format.toPattern(), "dd.MM.y, HH:mm:ss");
    Assert.assertEquals(format.toLocalizedPattern(), "dd.MM.y, HH:mm:ss");
    locale = new Locale("de", "DE");
    format = (SimpleDateFormat) SimpleDateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, locale);
    Assert.assertEquals(format.toPattern(), "dd.MM.y, HH:mm:ss");
    Assert.assertEquals(format.toLocalizedPattern(), "dd.MM.y, HH:mm:ss");
  }
}
