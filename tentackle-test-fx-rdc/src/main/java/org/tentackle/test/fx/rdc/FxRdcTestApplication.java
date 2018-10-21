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

package org.tentackle.test.fx.rdc;

import javafx.application.Platform;
import org.testng.Reporter;
import org.testng.SkipException;
import org.testng.annotations.BeforeSuite;

import org.tentackle.test.pdo.TestApplication;

import java.awt.GraphicsEnvironment;

/**
 *
 * @author harald
 */
public abstract class FxRdcTestApplication extends TestApplication {

  public FxRdcTestApplication(String name) {
    super(name);
  }

  @BeforeSuite(alwaysRun = true)
  public void startFx() {
    if (GraphicsEnvironment.isHeadless()) {
      throw new SkipException("JVM is headless -> no FX tests");
    }
    else {

      try {
        Platform.startup(() -> Reporter.log("FX started<br>", true));
      }
      catch(RuntimeException rex) {
        throw new SkipException("FX not supported -> no FX tests");
      }
    }
  }

}
