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
import org.tentackle.app.AbstractApplication;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
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
public class ControllerTest extends AbstractApplication {

  private Session session;
  private DomainContext context;

  public ControllerTest() {
    super("fx-test");
  }

  @BeforeClass(alwaysRun = true)
  public void setUpClass() throws Exception {
    SessionInfo sessionInfo = Pdo.createSessionInfo();
    session = Pdo.createSession(sessionInfo);
    try {
      session.open();
    }
    catch (PersistenceException ex) {
      // no database? wrong database? whatever: testing environment incomplete
      session = null;
      return;
    }
    Session.setCurrentSession(session);
    ModificationTracker.getInstance().setSession(session);
    context = Pdo.createDomainContext(session);
    register();
  }

  @AfterClass(alwaysRun = true)
  public void tearDownClass() throws Exception {
    if (session != null) {
      session.close();
      Session.setCurrentSession(null);
      unregister();
    }
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public boolean isServer() {
    return false;
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;
  }

  @Test(alwaysRun = true)
  public void testControllers() {

    if (session == null) {
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
