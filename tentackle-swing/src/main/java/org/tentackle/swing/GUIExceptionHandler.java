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

import java.io.CharArrayWriter;
import java.io.PrintWriter;
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

  private static CharArrayWriter  writer;
  private static PrintWriter      printWriter;

  static  {
    writer      = new CharArrayWriter();
    printWriter = new PrintWriter(writer);
  }

  private static Set<GUISaveable> saveables = new HashSet<GUISaveable>();    // Set of GUISaveables

  private static final String GUIExceptionHandlerKey = "sun.awt.exception.handler";
  private static final String GUIExceptionHandler    = "org.tentackle.swing.GUIExceptionHandler";


  /**
   * Installs the GUI exception handler.
   *
   * @param override is true if override existing handler, false if not set yet.
   */
  public static void install(boolean override) {
    if (override || System.getProperty(GUIExceptionHandlerKey) == null) {
      System.setProperty(GUIExceptionHandlerKey, GUIExceptionHandler);
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
    saveables.add(saveable);
  }

  /**
   * Unregisters a GUISaveable.
   *
   * @param saveable the object to save on exit
   */
  public static void unregisterSaveable(GUISaveable saveable) {
    saveables.remove(saveable);
  }


  /**
   * Invokes all registered saveables.
   */
  public static void runSaveState() {
    for (GUISaveable s: saveables)  {
      s.saveState();
    }
  }


  /**
   * Handles an exception.
   * Logs the exception, invokes all GUISaveables
   * and optionally terminates the application
   * with exit code 1.
   *
   * @param thrown is the Throwable to be handled.
   */
  public void handle(Throwable thrown)  {

    // log message into writer memory
    printWriter.println(thrown.getMessage());
    thrown.printStackTrace(printWriter);
    printWriter.flush();
    // log message to default logger
    LOGGER.severe(writer.toString());
    // clear data in writer
    writer.reset();

    // close all GUIsaveables
    try {
      runSaveState();    // save all states before
    }
    catch (Exception e) {
      // log any exceptions while saving but don't stop
      LoggerOutputStream.logException(e, LOGGER);
    }

    // terminate application
    if (exitOnException)  {
      System.exit(1);   // don't throw GUIRuntimeException to avoid loops
    }
  }

}
