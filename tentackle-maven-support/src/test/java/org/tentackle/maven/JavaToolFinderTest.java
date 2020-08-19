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

package org.tentackle.maven;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.common.ToolRunner;

import java.io.File;

/**
 * Tests for the {@link JavaToolFinder}.
 */
public class JavaToolFinderTest {

  @Test
  public void findProgs() {
    File tool = new JavaToolFinder().find("java");
    Assert.assertNotNull(tool);
    Reporter.log("java is " + tool.getAbsolutePath() + "<br>");
    tool = new JavaToolFinder().find("mvn");
    if (tool != null) {
      Reporter.log("maven is " + tool.getAbsolutePath() + "<br>");
      try {
        Reporter.log(new ToolRunner(tool).arg("--version").run().getOutputAsString() + "<br>");
      }
      catch (TentackleRuntimeException e) {
        Assert.fail("maven execution failed", e);
      }
    }
    tool = new JavaToolFinder().find("jdeps");
    Assert.assertNotNull(tool);
    Reporter.log("jdeps is " + tool.getAbsolutePath() + "<br>");
  }

}
