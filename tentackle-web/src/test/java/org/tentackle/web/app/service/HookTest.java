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

package org.tentackle.web.app.service;

import org.testng.annotations.Test;

import org.tentackle.common.ModuleHook;

import java.util.Locale;
import java.util.MissingResourceException;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Tests for {@link Hook}, the {@code ModuleHook} the web module provides so that resource bundles
 * of this module can be resolved from outside it.
 */
@SuppressWarnings("missing-explicit-ctor")
public class HookTest {

  private static final String BUNDLE = "org.tentackle.web.app.service.HookBundle";

  private final ModuleHook hook = new Hook();


  @Test
  public void resolvesBundleOfTheWebModule() {
    assertEquals(hook.getBundle(BUNDLE, Locale.US).getString("sessionExpired"), "your session has expired");
  }

  @Test
  public void resolvesLocalizedBundle() {
    assertEquals(hook.getBundle(BUNDLE, Locale.GERMANY).getString("sessionExpired"), "Ihre Sitzung ist abgelaufen");
  }

  @Test
  public void fallsBackToTheDefaultBundle() {
    assertEquals(hook.getBundle(BUNDLE, Locale.FRANCE).getString("sessionExpired"), "your session has expired");
  }

  @Test
  public void reportsAnUnknownBundle() {
    expectThrows(MissingResourceException.class,
                 () -> hook.getBundle("org.tentackle.web.app.service.NoSuchBundle", Locale.US));
  }

}
