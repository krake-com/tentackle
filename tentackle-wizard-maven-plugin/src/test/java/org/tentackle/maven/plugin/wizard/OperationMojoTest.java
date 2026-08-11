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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the parameter handling of the operation wizard's batch mode.
 */
public class OperationMojoTest {

  /**
   * Creates a profile with a given name.<br>
   * The name is a maven parameter without a setter, hence the anonymous subclass.
   *
   * @param name the profile name
   * @return the profile
   */
  private OperationProfile createProfile(String name) {
    return new OperationProfile() {
      @Override
      public String getName() {
        return name;
      }
    };
  }


  @Test
  public void testSingleProfileWithoutName() throws MojoExecutionException {
    OperationProfile profile = createProfile("operation");
    assertSame(OperationMojo.selectProfile(List.of(profile), null), profile);
  }

  @Test
  public void testProfileSelectedCaseInsensitively() throws MojoExecutionException {
    OperationProfile masterData = createProfile("masterdata");
    OperationProfile business = createProfile("Business");
    List<OperationProfile> profiles = List.of(masterData, business);
    assertSame(OperationMojo.selectProfile(profiles, "BUSINESS"), business);
    assertSame(OperationMojo.selectProfile(profiles, "MasterData"), masterData);
  }

  @Test
  public void testAmbiguousProfile() {
    List<OperationProfile> profiles = List.of(createProfile("masterdata"), createProfile("business"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> OperationMojo.selectProfile(profiles, null));
    assertTrue(ex.getMessage().contains("masterdata, business"), ex.getMessage());
  }

  @Test
  public void testUnknownProfile() {
    List<OperationProfile> profiles = List.of(createProfile("operation"));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> OperationMojo.selectProfile(profiles, "nosuch"));
    assertTrue(ex.getMessage().contains("nosuch"), ex.getMessage());
    assertTrue(ex.getMessage().contains("operation"), ex.getMessage());
  }

  @Test
  public void testBlankToNull() {
    assertNull(OperationMojo.blankToNull(null));
    assertNull(OperationMojo.blankToNull(""));
    assertNull(OperationMojo.blankToNull("   "));
    assertEquals(OperationMojo.blankToNull(" SendInvoice "), "SendInvoice");
  }

  @Test
  public void testNoneToNull() {
    assertNull(OperationMojo.noneToNull(null));
    assertNull(OperationMojo.noneToNull(""));
    assertNull(OperationMojo.noneToNull(" - "));
    assertEquals(OperationMojo.noneToNull("SendInvoiceDomain"), "SendInvoiceDomain");
  }
}
