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
 */

package org.tentackle.pdo;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.session.SessionUtilities;

import java.util.MissingResourceException;

/**
 * Tests the pdo utilities.<br>
 * This is also a template for applications that have replaced PdoUtilities
 * to map the names via a resource bundle for &#64;Singular and &#64;Plural.
 *
 * @author harald
 */
public class PdoUtilitiesTest {

  @Test(groups = "local")
  public void testSingular() {
    for (String pdoName: SessionUtilities.getInstance().getClassNames()) {
      try {
        Class<?> clazz = Class.forName(pdoName);
        if (PersistentDomainObject.class.isAssignableFrom(clazz)) {   // singular/plural does not apply to AbstractDbObjects
          String name = PdoUtilities.getInstance().getSingular(clazz);
          Assert.assertNotNull(name, "no @Singular for " + clazz.getName());
          name = PdoUtilities.getInstance().getPlural(clazz);
          Assert.assertNotNull(name, "no @Plural for " + clazz.getName());
        }
      }
      catch (MissingResourceException mx) {
        Assert.fail("in PDO class " + pdoName + ": " + mx.getMessage());
      }
      catch (ClassNotFoundException cx) {
        Assert.fail("no such PDO class: " + pdoName);
      }
    }
  }

}