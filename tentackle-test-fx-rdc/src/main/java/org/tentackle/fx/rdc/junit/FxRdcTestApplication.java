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

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.opentest4j.AssertionFailedError;

import org.tentackle.pdo.junit.TestApplication;

import java.awt.GraphicsEnvironment;

/**
 * FX Junit5 test application.
 *
 * @author harald
 */
public abstract class FxRdcTestApplication extends TestApplication {

  private static final String HEADLESS_MSG = "JVM is headless -> no FX tests";
  private static final Logger LOGGER = LoggerFactory.getLogger(FxRdcTestApplication.class);
  private static boolean fxStarted;

  public FxRdcTestApplication(String name, String version) {
    super(name, version);
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

}
