/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc.app;

import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.log.Logger;

import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Tentackle FX application base class.<br>
 * The JavaFX application launched by the {@link DesktopApplication}.
 * <p>
 * Usually the application initially launched is the {@link LoginApplication}.
 *
 * @author harald
 */
public abstract class FxApplication extends Application {

  private static final Logger LOGGER = Logger.get(FxApplication.class);

  private static final String FATAL = "runtime or configuration error";

  private int exitValue;              // delayed exit code (usually due to a failed login)
  private Throwable exitThrowable;    // and optional reason


  /**
   * Parent constructor.
   */
  public FxApplication() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void start(Stage stage) {
    try {
      DesktopApplication<?> application = (DesktopApplication<?>) org.tentackle.app.Application.getInstance();
      application.setFxApplication(this);
      application.registerUncaughtExceptionHandler();
      startApplication(stage);
    }
    // log the stacktrace, because javafx.application.Application just terminates without a detailed error message
    catch (RuntimeException ex) {
      LOGGER.severe("starting tentackle fx application failed", ex);
      throw ex;
    }
    catch (Throwable t) {
      LOGGER.severe(FATAL, t);
      throw new RdcRuntimeException(FATAL, t);
    }
  }

  /**
   * Sets the exit value and throwable to be picked up by the stop method when the last window is closed
   * and stop is not invoked explicitly from within the application with a non-zero exit value.
   *
   * @param exitValue the exit value
   * @param exitThrowable the exit throwable
   */
  public void setStopReason(int exitValue, Throwable exitThrowable) {
    this.exitValue = exitValue;
    this.exitThrowable = exitThrowable;
  }

  @Override
  public void stop() {
    org.tentackle.app.Application tentackleApplication = org.tentackle.app.Application.getInstance();
    if (tentackleApplication != null) {   // not already terminated
      tentackleApplication.stop(exitValue, exitThrowable);
    }
  }

  /**
   * The main entry point for all Tentackle JavaFX applications.
   * <p>
   * This is just a replacement for {@link Application#start(javafx.stage.Stage)} to make sure the
   * client application really implements this method.
   *
   * @param primaryStage the primary stage
   */
  public abstract void startApplication(Stage primaryStage);

  /**
   * Shows the initialization status during application startup.
   *
   * @param msg the message shown in the view
   * @param progress the progress, 0 to disable, negative if infinite, 1.0 if done
   */
  public abstract void showApplicationStatus(String msg, double progress);

}
