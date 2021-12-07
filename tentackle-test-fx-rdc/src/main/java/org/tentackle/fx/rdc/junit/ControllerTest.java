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
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

import org.tentackle.common.Version;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;

/**
 * Junit5 test to verify that controllers can be loaded and bound.<br>
 * Opens a session in case some controllers need it.<br>
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

  private static final Logger LOGGER = LoggerFactory.getLogger(ControllerTest.class);

  private final String packagePrefix;

  /**
   * Creates the controller test.
   *
   * @param packagePrefix optional package prefix, null or empty if all
   */
  public ControllerTest(String packagePrefix) {
    super("fx-rdc-conroller-test", Version.RELEASE);
    this.packagePrefix = packagePrefix;
  }

  public ControllerTest() {
    this(null);
  }

  @Test
  public void testControllers() {

    for (Class<FxController> clazz : FxFactory.getInstance().getControllerClasses()) {
      if (packagePrefix == null || clazz.getName().startsWith(packagePrefix)) {
        FxControllerService anno = clazz.getAnnotation(FxControllerService.class);
        if (anno == null || anno.test()) {
          LOGGER.info(() -> "testing controller " + clazz.getName());
          try {
            FxController controller = Fx.load(clazz);
            controller.validateInjections();
            if (anno == null || anno.binding() != FxControllerService.BINDING.NO) {
              controller.getBinder().assertAllBound();
            }
          }
          catch (RuntimeException rex) {
            Assertions.fail("loading " + clazz + " failed: " + rex.getMessage(), rex);
          }
        }
        else {
          LOGGER.info(() -> "controller " + clazz.getName() + " not tested!");
        }
      }
    }
  }

}
