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

package org.tentackle.test.rdc;

import com.sun.javafx.application.PlatformImpl;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.test.AbstractPdoTest;
import org.testng.Reporter;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Tests whether controllers could be loaded and bound.<br>
 * Needs a db-connection to initialize all controllers properly.
 *
 * @author harald
 */
public class ControllersTest extends AbstractPdoTest {

  @Test
  public void testControllers() {

    try {
      PlatformImpl.startup(() -> Reporter.log("FX started<br>", true));
    }
    catch(RuntimeException rex) {
      fail("FX not supported -> no FX tests", rex);
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
          fail("loading " + clazz + " failed: " + rex.getMessage(), rex);
        }
      }
    }
  }

}
