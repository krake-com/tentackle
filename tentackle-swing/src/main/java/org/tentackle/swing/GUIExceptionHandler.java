/**
 * Tentackle - http://www.tentackle.org
 * Copyright (C) 2001-2008 Harald Krake, harald@krake.de, +49 7722 9508-0
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



package org.tentackle.swing;

import java.util.HashSet;
import java.util.Set;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.LoggerOutputStream;


/**
 * The GUI Exception Handler.
 *
 * @author harald
 */
public class GUIExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GUIExceptionHandler.class);

  private static boolean exitOnException = true;


  private static final Set<GUISaveable> SAVEABLES = new HashSet<>();    // Set of GUISaveables

  private static final String HANDLER_KEY   = "sun.awt.exception.handler";
  private static final String HANDLER_CLASS = "org.tentackle.swing.GUIExceptionHandler";


  /**
   * Installs the GUI exception handler.
   *
   * @param override is true if override existing handler, false if not set yet.
   */
  public static void install(boolean override) {
    if (override || System.getProperty(HANDLER_KEY) == null) {
      System.setProperty(HANDLER_KEY, HANDLER_CLASS);
    }
  }


  /**
   * Sets the exit on exception flag.
   *
   * @param flag true if application should exit on exception (default)
   */
  public static void setExitOnException(boolean flag) {
    exitOnException = flag;
  }


  /**
   * Gets the exit on exception flag.
   *
   * @return true if application should exit on exception (default)
   */
  public static boolean isExitOnException() {
    return exitOnException;
  }


  /**
   * Registers a GUISaveable.
   *
   * @param saveable the object to save on exit
   */
  public static void registerSaveable(GUISaveable saveable) {
    SAVEABLES.add(saveable);
  }

  /**
   * Unregisters a GUISaveable.
   *
   * @param saveable the object to save on exit
   */
  public static void unregisterSaveable(GUISaveable saveable) {
    SAVEABLES.remove(saveable);
  }


  /**
   * Invokes all registered saveables.
   */
  public static void runSaveState() {
    for (GUISaveable s: SAVEABLES)  {
      s.saveState();
    }
  }


  /**
   * Handles an exception.
   * Logs the exception, invokes all GUISaveables
   * and optionally terminates the application
   * with exit code 1.
   *
   * @param t the Throwable to be handled.
   */
  public void handle(Throwable t)  {

    try {
      LOGGER.severe("unhandled GUI exception", t);
    }
    catch (Throwable t2) {
      // ignore in case logging failed
    }

    // close all GUIsaveables
    try {
      runSaveState();    // save all states before
    }
    catch (Exception ex) {
      // log any exceptions while saving but don't stop
      LoggerOutputStream.logException(ex, LOGGER);
    }

    // terminate application
    if (exitOnException)  {
      System.exit(1);   // don't throw GUIRuntimeException to avoid loops
    }
  }

}
