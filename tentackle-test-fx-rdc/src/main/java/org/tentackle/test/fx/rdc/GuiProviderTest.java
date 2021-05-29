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
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.GuiProviderService;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.reflect.ClassMapper;

/**
 * Tests whether GUI-providers could be loaded and bound.<br>
 * Opens a session in case some providers need it.<br>
 * Simply extend this class and put that in a test source folder.
 * <p>
 * Example:
 * <pre>
  public class TestGuiProviders extends GuiProviderTest {
    TestGuiProviders() {
      super("my.package.name");
    }
  }
 </pre>
 *
 * @author harald
 */
public abstract class GuiProviderTest extends FxRdcTestApplication {

  private final String packagePrefix;
  private final ClassMapper classMapper;

  /**
   * Creates the GUI provider test.
   *
   * @param packagePrefix optional package prefix, null or empty if all
   */
  public GuiProviderTest(String packagePrefix) {
    super("fx-rdc-provider-test", Version.RELEASE);
    this.packagePrefix = packagePrefix;
    classMapper = ClassMapper.create(getName(), GuiProvider.class);
  }

  public GuiProviderTest() {
    this(null);
  }

  @Test(alwaysRun = true)
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testGuiProviders() {
    for (String pdoClassName : classMapper.getMap().keySet()) {
      PersistentDomainObject pdo = PdoFactory.getInstance().create(pdoClassName, getDomainContext());
      try {
        GuiProvider provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
        if (packagePrefix == null || provider.getClass().getName().startsWith(packagePrefix)) {
          GuiProviderService anno = provider.getClass().getAnnotation(GuiProviderService.class);
          if (anno == null || anno.test()) {
            Reporter.log("testing gui provider " + provider.getClass().getName() + "<br>", true);
            if (provider.editorExists()) {
              provider.createEditor();
            }
            if (provider.finderExists()) {
              provider.createFinder();
            }
            provider.createIcon();
            provider.createTableView();
            provider.createTreeItem();
          }
          else {
            Reporter.log("gui provider " + provider.getClass().getName() + " not tested!<br>", true);
          }
        }
      }
      catch (PdoRuntimeException nx) {
        if (!(nx.getCause() instanceof ClassNotFoundException)) {
          Assert.fail("creating GuiProvider for " + pdo.getClassBaseName() + " failed", nx);
        }
        else {
          Reporter.log("no GuiProvider for " + pdo.getClassBaseName() + "<br>", true);
        }
      }
      catch (RuntimeException ex) {
        Assert.fail("testing GuiProvider for " + pdo.getClassBaseName() + " failed", ex);
      }
    }
  }

}
