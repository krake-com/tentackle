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

package org.tentackle.validate.validator;

import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ValidatorBundle} resource bundle accessor.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ValidatorBundleTest {

  private static final String KEY = "{0} is empty";

  @Test
  public void testGetBundle() {
    assertNotNull(ValidatorBundle.getBundle());
    ResourceBundle german = ValidatorBundle.getBundle(Locale.GERMAN);
    assertNotNull(german);
    assertEquals(german.getString(KEY), "{0} ist leer");
  }

  @Test
  public void testGetStringUsesConfiguredLocale() {
    // tests run with the US locale forced -> default bundle
    assertEquals(ValidatorBundle.getString(KEY), "{0} is empty");
  }

  @Test
  public void testGetStringWithExplicitLocale() {
    assertEquals(ValidatorBundle.getString(Locale.GERMAN, KEY), "{0} ist leer");
    assertEquals(ValidatorBundle.getString(Locale.ENGLISH, KEY), "{0} is empty");
  }

  @Test
  public void testUnknownKeyThrows() {
    assertThrows(MissingResourceException.class, () -> ValidatorBundle.getString("no.such.key"));
  }

  @Test
  public void testConstructorIsPrivate() throws Exception {
    Constructor<ValidatorBundle> ctor = ValidatorBundle.class.getDeclaredConstructor();
    assertTrue(Modifier.isPrivate(ctor.getModifiers()));
  }
}
