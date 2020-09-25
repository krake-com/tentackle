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

import javafx.application.Application;
import javafx.stage.Stage;

import org.tentackle.app.AbstractApplication;
import org.tentackle.fx.FxRuntimeException;
import org.tentackle.log.Logger;

/**
 * Tentackle FX application base class.
 * <p>
 * Usually the application initially launched is the {@link LoginApplication}.
 *
 * @author harald
 */
public abstract class FxApplication extends Application {

  private static final Logger LOGGER = Logger.get(FxApplication.class);

  private static final String FATAL = "runtime or configuration error";


  @Override
  public void start(Stage stage) throws Exception {
    try {
      DesktopApplication<?> application = (DesktopApplication<?>) AbstractApplication.getRunningApplication();
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
      throw new FxRuntimeException(FATAL, t);
    }
  }

  @Override
  public void stop() throws Exception {
    DesktopApplication<?> application = (DesktopApplication<?>) AbstractApplication.getRunningApplication();
    application.stop();
  }


  /**
   * The main entry point for all Tentackle JavaFX applications.
   * <p>
   * This is just a replacement for {@link Application#start(javafx.stage.Stage)} to make sure the
   * client application is really implementing this method.
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
