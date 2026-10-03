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

package org.tentackle.fx.rdc.testng;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.BeforeSuite;

import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.testng.TestApplication;
import org.tentackle.session.Session;

import javafx.application.Platform;
import java.awt.GraphicsEnvironment;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base class for TestNG tests of the FX rich desktop client.<br>
 * Extends {@link TestApplication} by the JavaFX toolkit and a session for the FX thread.
 * <p>
 * The FX toolkit is started once by {@link #startFx()} ({@code @BeforeSuite}).
 * The tests are skipped if the JVM is headless or FX is not supported.
 *
 * @author harald
 */
public abstract class FxRdcTestApplication extends TestApplication {

  private static final String HEADLESS_MSG = "JVM is headless -> no FX tests";
  private static final int DEFAULT_FX_TIMEOUT = 30;       // timeout in seconds for the FX thread to catch up

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
   * Sets the timeout for the FX thread to complete the processing of a subtest.<br>
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
   * @return the timeout in seconds
   */
  public int getFxTimeout() {
    return fxTimeout;
  }

  /**
   * Starts the FX toolkit.
   *
   * @throws SkipException if the JVM is headless or FX is not supported
   */
  @BeforeSuite(alwaysRun = true)
  public void startFx() {
    if (GraphicsEnvironment.isHeadless()) {
      System.out.println(HEADLESS_MSG);   // Reporter.log ignored with SkipException
      throw new SkipException(HEADLESS_MSG);
    }
    else {
      try {
        Platform.startup(() -> Reporter.log("FX started<br>", true));
      }
      catch (IllegalStateException ix) {
        Reporter.log("FX already started<br>", true);   // toolkit already initialized by someone else
      }
      catch (RuntimeException rex) {
        throw new SkipException("FX not supported -> no FX tests", rex);
      }
    }
  }

  /**
   * Sets the domain context and prepares the FX thread.<br>
   * Clones the session for the FX thread, makes the clone the FX thread's current session and
   * installs an uncaught exception handler that records exceptions thrown on the FX thread.
   * Such an exception fails the next {@link #runFx(String, Runnable)}.
   *
   * @param context the domain context
   */
  @Override
  protected void setDomainContext(DomainContext context) {
    super.setDomainContext(context);
    if (GraphicsEnvironment.isHeadless()) {
      throw new SkipException(HEADLESS_MSG);
    }
    else {
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
          Assert.fail("waiting for FX failed");
        }
      }
      catch (InterruptedException e) {
        Assert.fail("FX setup interrupted", e);
      }
    }
  }

  /**
   * Unregisters the application and closes the FX thread's session.<br>
   * Does nothing if the FX thread was never prepared, since then the application was not registered either.
   */
  @Override
  public void unregister() {
    if (fxSession != null) {
      super.unregister();
      fxSession.close();
    }
  }

  /**
   * Runs an FX related test and waits until the FX thread has completed all
   * test-related processing.
   * <p>
   * The test runs on the calling thread. Afterward, a marker is posted to the FX thread via
   * {@link Platform#runLater(Runnable)}, and the method waits until it has been processed
   * (see {@link #getFxTimeout()}), i.e. until all FX events queued by the test so far have been handled.
   * The test fails if it throws a runtime exception, times out, or if an exception was thrown on the FX thread.
   * <p>
   * Notice that an exception recorded on the FX thread is not cleared, i.e. it fails all subsequent tests as well.
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
      Assert.fail("test '" + name + "' failed", rex);
    }
    Platform.runLater(latch::countDown);
    try {
      if (!latch.await(getFxTimeout(), TimeUnit.SECONDS)) {
        Assert.fail("test '" + name + "' timed out");
      }
    }
    catch (InterruptedException e) {
      Assert.fail("test '" + name + "' interrupted");
    }
    Throwable t = unhandledException.get();
    if (t != null) {
      Assert.fail("test '" + name + "' failed", t);
    }
  }

}
