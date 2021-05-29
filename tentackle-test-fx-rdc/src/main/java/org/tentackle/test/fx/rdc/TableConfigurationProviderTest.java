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

package org.tentackle.test.fx.rdc;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.common.Version;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;
import org.tentackle.fx.table.TableConfigurationProviderService;
import org.tentackle.reflect.ClassMapper;

/**
 * Tests whether {@link org.tentackle.fx.table.TableConfigurationProvider}s could be loaded and bound.<br>
 * Opens a session in case some providers need it.<br>
 * Simply extend this class and put that in a test source folder.
 * <p>
 * Example:
 * <pre>
  public class TestTableConfigurationProviders extends TableConfigurationProviderTest {
    TestTableConfigurationProviders() {
      super("my.package.name");
    }
  }
 </pre>
 *
 * @author harald
 */
public abstract class TableConfigurationProviderTest extends FxRdcTestApplication {

  private final String packagePrefix;
  private final ClassMapper classMapper;

  /**
   * Creates the table configuration provider test.
   *
   * @param packagePrefix optional package prefix, null or empty if all
   */
  public TableConfigurationProviderTest(String packagePrefix) {
    super("fx-rdc-table-config-provider-test", Version.RELEASE);
    this.packagePrefix = packagePrefix;
    classMapper = ClassMapper.create(getName(), TableConfigurationProvider.class);
  }

  public TableConfigurationProviderTest() {
    this(null);
  }

  @Test(alwaysRun = true)
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testTableConfigurationProviders() {
    for (String servicedClassName : classMapper.getMap().keySet()) {
      try {
        Class<?> servicedClass = Class.forName(servicedClassName);
        TableConfigurationProvider<?> provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(servicedClass);
        if (provider == null) {
          // how??
          Assert.fail("no table configuration provider for " + servicedClass);
        }
        else if (packagePrefix == null || provider.getClass().getName().startsWith(packagePrefix)) {
          TableConfigurationProviderService anno = provider.getClass().getAnnotation(TableConfigurationProviderService.class);
          if (anno == null || anno.test()) {
            Reporter.log("testing table configuration provider " + provider.getClass().getName() + "<br>", true);
            provider.createTableConfiguration();
          }
          else {
            Reporter.log("table configuration provider " + provider.getClass().getName() + " not tested!<br>", true);
          }
        }
      }
      catch (ClassNotFoundException cx) {
        Assert.fail("could not load provider class " + servicedClassName, cx);
      }
      catch (RuntimeException ex) {
        Assert.fail("testing TableConfigurationProvider for " + servicedClassName + " failed", ex);
      }
    }
  }

}
