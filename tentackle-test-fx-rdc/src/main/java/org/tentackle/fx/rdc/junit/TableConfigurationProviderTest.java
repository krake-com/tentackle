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

package org.tentackle.fx.rdc.junit;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import org.tentackle.common.Version;
import org.tentackle.fx.table.TableConfigurationProvider;
import org.tentackle.fx.table.TableConfigurationProviderFactory;
import org.tentackle.fx.table.TableConfigurationProviderService;
import org.tentackle.reflect.ClassMapper;

/**
 * Junit5 test to verify that all {@link TableConfigurationProvider}s can be loaded and bound.<br>
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

  /**
   * Creates the table configuration provider test w/o package prefix.
   */
  public TableConfigurationProviderTest() {
    this(null);
  }

  @Test
  public void testTableConfigurationProviders(TestReporter reporter) {
    for (String servicedClassName : classMapper.getMap().keySet()) {
      try {
        Class<?> servicedClass = Class.forName(servicedClassName);
        TableConfigurationProvider<?> provider = TableConfigurationProviderFactory.getInstance().createTableConfigurationProvider(servicedClass);
        if (provider == null) {
          // how??
          Assertions.fail("no table configuration provider for " + servicedClass);
        }
        else if (packagePrefix == null || provider.getClass().getName().startsWith(packagePrefix)) {
          TableConfigurationProviderService anno = provider.getClass().getAnnotation(TableConfigurationProviderService.class);
          if (anno == null || anno.test()) {
            runFx(provider.getClass().getName(), () -> {
              reporter.publishEntry("testing table configuration provider " + provider.getClass().getName());
              provider.createTableConfiguration();
            });
          }
          else {
            reporter.publishEntry("table configuration provider " + provider.getClass().getName() + " not tested!");
          }
        }
      }
      catch (ClassNotFoundException cx) {
        Assertions.fail("could not load provider class " + servicedClassName, cx);
      }
      catch (RuntimeException ex) {
        Assertions.fail("testing TableConfigurationProvider for " + servicedClassName + " failed", ex);
      }
    }
  }

}
