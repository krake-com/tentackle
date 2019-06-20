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
 * License along with this library; if not, write to the DFree Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.test.fx.rdc;

import org.reflections.Reflections;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PdoRuntimeException;
import org.tentackle.pdo.PersistentDomainObject;

/**
 * Tests whether GUI-providers could be loaded and bound.<br>
 * Needs a db-connection to initialize all providers properly.<br>
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

  public GuiProviderTest(String packagePrefix) {
    super("fx-rdc-provider-test");
    this.packagePrefix = packagePrefix;
  }

  @Test(alwaysRun = true)
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testGuiProviders() {

    Reflections reflections = new Reflections(packagePrefix);
    reflections.getSubTypesOf(PersistentDomainObject.class).forEach(c -> {
      try {
        PersistentDomainObject pdo = PdoFactory.getInstance().create(c, getDomainContext());
        GuiProvider provider = GuiProviderFactory.getInstance().createGuiProvider(pdo);
        Reporter.log("testing GuiProvider for PDO " + c.getName() + "<br>");
        if (provider.editorExists()) {
          provider.createEditor();
        }
        if (provider.finderExists()) {
          provider.createFinder();
        }
        provider.createIcon();
        provider.createTableView();
        provider.createTreeCell();
        provider.createTreeItem();
      }
      catch (PdoRuntimeException nx) {
        if (!(nx.getCause() instanceof ClassNotFoundException)) {
          Assert.fail("creating GuiProvider for " + c.getName() + " failed", nx);
        }
        else {
          Reporter.log("no GuiProvider for " + c.getName() + "<br>");
        }
      }
      catch (RuntimeException ex) {
        Assert.fail("Testing GuiProvider for " + c.getName() + " failed", ex);
      }
    });

  }

}
