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

package org.tentackle.model;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.Collection;

/**
 * Loads the framework's model from the classpath.
 */
public class ModelTest {

  @Test(groups = "local")
  public void loadFromClasspath() {
    Reporter.log("ModelTest.loadFromClasspath <br>", true);
    Model model = Model.getInstance();
    model.clearModel();
    try {
      Collection<EntityInfo> entityInfos = model.loadFromResources(true);   // tentackle-test-pdo is in classpath!
      Assert.assertEquals(entityInfos.size(), 10);    // 10 tentackle entities
      for (EntityInfo entityInfo : entityInfos) {
        Reporter.log(entityInfo.getEntity() + " in " + entityInfo.getURL() + "<br>");
      }
    }
    catch (ModelException e) {
      e.printStackTrace();
    }
  }

}
