/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Products GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.test.fx.rdc;

import com.sun.javafx.application.PlatformImpl;
import java.awt.GraphicsEnvironment;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.test.pdo.TestApplication;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

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
public abstract class ControllerTest extends TestApplication {

  public ControllerTest() {
    super("fx-rdc-conroller-test");
  }

  @Test(alwaysRun = true)
  public void testControllers() {

    if (getDomainContext().getSession() == null) {
      Reporter.log("no backend found -> no FX tests<br>");
      return;
    }

    if (GraphicsEnvironment.isHeadless()) {
      Reporter.log("JVM is headless -> no FX tests<br>");
      return;
    }

    try {
      PlatformImpl.startup(() -> Reporter.log("FX started<br>", true));
    }
    catch(RuntimeException rex) {
      Reporter.log("FX not supported -> no FX tests<br>");
      return;
    }

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
