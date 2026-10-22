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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

/**
 * Tests the use of {@link ResourceBundle.Control}s by the {@link DefaultBundleFactory}.
 * <p>
 * Depending on how surefire runs the tests, the factory lives in the named module {@code org.tentackle.common}
 * or in the unnamed module. The expectations differ accordingly.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultBundleFactoryTest {

  /** a test bundle in src/test/resources, not registered via {@link Bundle}. */
  private static final String BUNDLE = "org.tentackle.common.FactoryTestBundle";

  /** the control handed out by the test provider. */
  private static final ResourceBundle.Control CONTROL =
      ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);

  /**
   * A factory with a control provider for all bundles.
   */
  private static class ControlledBundleFactory extends DefaultBundleFactory {
    @Override
    protected List<ResourceBundleControlProvider> createProviders() {
      return List.of(baseName -> CONTROL);
    }
  }

  private static boolean isNamed() {
    return DefaultBundleFactory.class.getModule().isNamed();
  }


  @Test
  public void testControlOnlyInUnnamedModule() {
    ResourceBundle.Control control = new ControlledBundleFactory().getControl(BUNDLE);
    if (isNamed()) {
      assertNull(control);
    }
    else {
      assertSame(control, CONTROL);
    }
  }

  /**
   * In a named module, passing a control to {@link ResourceBundle#getBundle} throws an
   * {@link UnsupportedOperationException}.
   */
  @Test
  public void testFindBundleWithControlProvider() {
    ResourceBundle bundle = new ControlledBundleFactory().findBundle(BUNDLE, Locale.ROOT);
    assertEquals(bundle.getString("greeting"), "hello");
  }

  @Test
  public void testFindBundleWithControlProviderAndClassLoader() {
    ResourceBundle bundle = new ControlledBundleFactory().findBundle(BUNDLE, Locale.ROOT, getClass().getClassLoader());
    assertEquals(bundle.getString("greeting"), "hello");
  }

  @Test
  public void testFindBundleWithControlProviderAndFactoryClassLoader() {
    DefaultBundleFactory factory = new ControlledBundleFactory();
    factory.setClassLoader(getClass().getClassLoader());
    ResourceBundle bundle = factory.findBundle(BUNDLE, Locale.ROOT);
    assertEquals(bundle.getString("greeting"), "hello");
  }

}
