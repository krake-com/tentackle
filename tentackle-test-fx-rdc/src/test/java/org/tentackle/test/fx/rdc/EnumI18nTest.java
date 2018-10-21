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

package org.tentackle.test.fx.rdc;

import java.util.MissingResourceException;
import org.reflections.Reflections;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Tests the translations for enums.<br>
 * Those are not covered by the tentackle-check:bundles mojo because the parameter
 * of getString is not a literal.
 *
 * @author harald
 */
public class EnumI18nTest {

  @Test
  public void testEnums() {
    Reflections reflections = new Reflections("com.hydro");
    reflections.getSubTypesOf(Enum.class).forEach(c -> {
      Reporter.log("enum " + c.getName() + "<br>");
      for (Object constant: c.getEnumConstants()) {
        try {
          constant.toString();    // this will use the bundle
        }
        catch (MissingResourceException mx) {
          Assert.fail("missing resource for " + c.getName() + ": " + mx.getMessage());
        }
      }
    });
  }

}