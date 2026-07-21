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
import org.junit.jupiter.api.BeforeAll;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.opentest4j.AssertionFailedError;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.junit.TestApplication;
import org.tentackle.session.Session;

import javafx.application.Platform;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * FX Junit5 test application.
 *
 * @author harald
 */
public abstract class FxRdcTestApplication extends TestApplication {

  private static final String HEADLESS_MSG = "JVM is headless -> no FX tests";
  private static final int DEFAULT_FX_TIMEOUT = 30;       // timeout in seconds to wait for failing test
  private static final Logger LOGGER = LoggerFactory.getLogger(FxRdcTestApplication.class);
  private static boolean fxStarted;

  private final AtomicReference<Throwable> unhandledException = new AtomicReference<>();
  private Session fxSession;
  private int fxTimeout = DEFAULT_FX_TIMEOUT;

  /**
   * Creates the test application.
   *
   * @param name the test name
   * @param version the test version
   */
  public FxRdcTestApplication(String name, String version) {
    super(name, version);
  }

  /**
   * Sets the FX timeout for a subtest to complete.<br>
   * Default is 30 s.
   *
   * @param fxTimeout the timeout in seconds
   */
  public void setFxTimeout(int fxTimeout) {
    this.fxTimeout = fxTimeout;
  }

  /**
   * Gets the FX timeout.
   *
   * @return the timeout
   */
  public int getFxTimeout() {
    return fxTimeout;
  }

  @BeforeAll
  public static void startFx() {
    synchronized (FxRdcTestApplication.class) {
      if (!fxStarted) {
        if (GraphicsEnvironment.isHeadless()) {
          System.out.println(HEADLESS_MSG);   // Reporter.log ignored with SkipException
          throw new AssertionFailedError(HEADLESS_MSG);
        }
        else {
          try {
            Platform.startup(() -> LOGGER.info(() -> "FX started"));
          }
          catch (RuntimeException rex) {
            throw new AssertionFailedError("FX not supported -> no FX tests");
          }
        }
        fxStarted = true;
      }
    }
  }

  @Override
  protected void setDomainContext(DomainContext context) {
    super.setDomainContext(context);
    if (fxStarted) {
      fxSession = context.getSession().clone("FX");
      CountDownLatch latch = new CountDownLatch(1);
      Platform.runLater(() -> {
        Session.setCurrentSession(fxSession);
        unhandledException.set(null);
        Thread.currentThread().setUncaughtExceptionHandler((_, e) -> unhandledException.set(e));
        latch.countDown();
      });
      try {
        if (!latch.await(getFxTimeout(), TimeUnit.SECONDS)) {
          Assertions.fail("waiting for FX failed");
        }
      }
      catch (InterruptedException e) {
        Assertions.fail("FX setup interrupted", e);
      }
    }
    else {
      throw new AssertionFailedError(HEADLESS_MSG);
    }
  }

  @Override
  public void unregister() {
    if (fxStarted) {
      super.unregister();
      fxSession.close();
    }
  }

  /**
   * Runs an FX related test and waits until the FX-thread has completed all
   * test-related processing.
   *
   * @param name the name of the test
   * @param runnable the test
   */
  protected void runFx(String name, Runnable runnable) {
    CountDownLatch latch = new CountDownLatch(1);
    try {
      runnable.run();
    }
    catch (RuntimeException rex) {
      Assertions.fail("test '" + name + "' failed", rex);
    }
    Platform.runLater(latch::countDown);
    try {
      if (!latch.await(getFxTimeout(), TimeUnit.SECONDS)) {
        Assertions.fail("test '" + name + "' timed out");
      }
    }
    catch (InterruptedException e) {
      Assertions.fail("test '" + name + "' interrupted");
    }
    Throwable t = unhandledException.get();
    if (t != null) {
      Assertions.fail("test '" + name + "' failed", t);
    }
  }

}
