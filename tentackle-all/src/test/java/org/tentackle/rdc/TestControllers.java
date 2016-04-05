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

package org.tentackle.rdc;

import com.sun.javafx.application.PlatformImpl;
import org.tentackle.app.AbstractApplication;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoTracker;
import org.tentackle.pdo.PersistenceException;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.Session;
import org.tentackle.pdo.SessionInfo;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests whether controllers could be loaded and bound.<br>
 * Needs a db-connection to initialize all controllers properly.
 *
 * @author harald
 */
public class TestControllers extends AbstractApplication {

  /**
   * the logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(TestControllers.class);

  private Session session;
  private DomainContext context;

  public TestControllers() {
    super("test");
  }

  @BeforeClass
  public void setUpClass() throws Exception {
    SessionInfo sessionInfo = Pdo.createSessionInfo(null, null, "test-backend");
    session = Pdo.createSession(sessionInfo);
    try {
      session.open();
    }
    catch (PersistenceException ex) {
      LOGGER.warning("cannot connect to backend: ", ex);
      // no database? wrong database? whatever: testing environment incomplete
      session = null;
      return;
    }
    Session.setCurrentSession(session);
    PdoTracker.getInstance().setSession(session);
    context = Pdo.createDomainContext(session);
    register();
  }

  @AfterClass
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

  @Test
  public void testControllers() {

    if (session == null) {
      String msg = "no backend found -> no FX tests";
      LOGGER.warning(msg);
      Reporter.log(msg + "<br>");
      return;
    }

    try {
      PlatformImpl.startup(() -> Reporter.log("FX started<br>", true));
    }
    catch(RuntimeException rex) {
      String msg = "FX not supported -> no FX tests<br>";
      LOGGER.warning(msg);
      Reporter.log(msg + "<br>");
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
          Assert.fail("loading " + clazz + " failed", rex);
        }
      }
    }
  }

  @Override
  public boolean isServer() {
    return false;
  }

  @Override
  public <U extends PersistentDomainObject<U>> U getUser(DomainContext context, long userId) {
    return null;
  }

}
