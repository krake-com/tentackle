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

package org.tentackle.maven.plugin.check;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * Tests how a scanned {@code getString()} reference is mapped to a bundle name.
 * <p>
 * The scanner cannot resolve symbols, so it hands over whatever the expression before the dot
 * looked like. Telling a bundle class apart from a variable, a constant or a method call is
 * therefore a matter of naming conventions, which is what this class implements - and what
 * decides which {@code .properties} file the key is looked up in.
 */
@SuppressWarnings("missing-explicit-ctor")
public class BundleKeyTest {

  /**
   * The references a scanned expression can take.
   *
   * @return the class containing the call, the scanned reference and the expected bundle name
   */
  @DataProvider(name = "references")
  public Object[][] references() {
    return new Object[][] {
      // a bundle class: taken as it is
      { "com.example.app.Client", "com.example.i18n.AppBundle", "com.example.i18n.AppBundle" },
      // a method invocation: the bundle of the calling class
      { "com.example.app.Client", "com.example.app.getBundle()", "com.example.app.Client" },
      // a variable, recognized by its lowercase first letter
      { "com.example.app.Client", "com.example.app.resources", "com.example.app.Client" },
      // a constant, recognized by being all uppercase
      { "com.example.app.Client", "com.example.app.BUNDLE", "com.example.app.Client" },
      // an underscore does not make a name lowercase
      { "com.example.app.Client", "com.example.app.MY_BUNDLE", "com.example.app.Client" },
      // a nested bundle class stays what it is
      { "com.example.app.Client", "com.example.i18n.Outer.Bundle", "com.example.i18n.Outer.Bundle" },
      // a name without a package cannot be told apart from a class: taken as it is
      { "com.example.app.Client", "AppBundle", "AppBundle" }
    };
  }

  @Test(dataProvider = "references")
  public void resolvesTheBundleName(String path, String name, String expected) {
    assertEquals(new BundleKey(path, name, "greeting").getBundleName(), expected);
  }

  /**
   * The scanned values are handed through unchanged - only {@code getBundleName()} interprets them.
   */
  @Test
  public void keepsTheScannedValues() {
    BundleKey key = new BundleKey("com.example.app.Client", "com.example.i18n.AppBundle", "greeting");

    assertEquals(key.getPath(), "com.example.app.Client");
    assertEquals(key.getName(), "com.example.i18n.AppBundle");
    assertEquals(key.getKey(), "greeting");
  }

  /**
   * The description is what the build log shows for a missing key, so it has to name all three.
   */
  @Test
  public void describesItselfForTheBuildLog() {
    BundleKey key = new BundleKey("com.example.app.Client", "com.example.i18n.AppBundle", "greeting");

    assertEquals(key.toString(), "com.example.app.Client: com.example.i18n.AppBundle -> 'greeting'");
  }

}
