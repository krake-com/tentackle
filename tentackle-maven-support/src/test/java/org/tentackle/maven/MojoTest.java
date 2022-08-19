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

import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertEquals;

public class MojoTest {

  private final AbstractTentackleMojo mojo = new AbstractTentackleMojo() {
    @Override
    public void executeImpl() {}
  };


  @Test
  public void testToDescriptorName() {
    String name = mojo.toDescriptorName(new File("/home/jenkins/.m2/repository/blah/jsyntaxpane/0.9.4-SNAPSHOT/jsyntaxpane-0.9.4-SNAPSHOT.jar"));
    assertEquals(name, "jsyntaxpane");

    name = mojo.toDescriptorName(new File("/home/jenkins/.m2/repository/blah/xx-native/1.8.4-SNAPSHOT/xx-native-1.8.4-SNAPSHOT-win32-amd64.jar"));
    assertEquals(name, "xx-native");

    name = mojo.toDescriptorName(new File("/home/jenkins/.m2/repository/org/codehaus/groovy/groovy/2.4.12/groovy-2.4.12.jar"));
    assertEquals(name, "groovy");

    name = mojo.toDescriptorName(new File("/home/jenkins/.m2/repository/x/y/z/.hidden-1.2.3.jar"));
    assertEquals(name, "hidden");

    name = mojo.toDescriptorName(new File("/home/jenkins/.m2/repository/x/y/z/0815.jar"));
    assertEquals(name, "0815");
  }
}
