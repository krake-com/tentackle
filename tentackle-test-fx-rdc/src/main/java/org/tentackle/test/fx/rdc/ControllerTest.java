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
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;

/**
 * Tests whether controllers could be loaded and bound.<br>
 * Needs a db-connection to initialize all controllers properly.<br>
 * Simply extend this class and put that in a test source folder.
 * <p>
 * Example:
 * <pre>
 *  public class TestControllers extends ControllerTest {}
 * </pre>
 *
 * @author harald
 */
public abstract class ControllerTest extends FxRdcTestApplication {

  public ControllerTest() {
    super("fx-rdc-conroller-test", Version.RELEASE);
  }

  @Test(alwaysRun = true)
  public void testControllers() {

    for (Class<FxController> clazz : FxFactory.getInstance().getControllerClasses()) {
      FxControllerService anno = clazz.getAnnotation(FxControllerService.class);
      if (anno.test()) {
        Reporter.log("testing controller " + clazz.getName() + "<br>", true);
        try {
          FxController controller = Fx.load(clazz);
          controller.validateInjections();
          if (anno.binding() != FxControllerService.BINDING.NO) {
            controller.getBinder().assertAllBound();
          }
        }
        catch (RuntimeException rex) {
          Assert.fail("loading " + clazz + " failed: " + rex.getMessage(), rex);
        }
      }
    }
  }

}
