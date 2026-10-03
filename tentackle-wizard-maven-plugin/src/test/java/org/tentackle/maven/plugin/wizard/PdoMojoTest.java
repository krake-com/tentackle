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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the class ID ranges of the PDO profiles.
 */
public class PdoMojoTest {

  /**
   * Creates a PDO profile.<br>
   * The name is a maven parameter without a setter, hence set via reflection.
   *
   * @param name the profile name
   * @param minClassId the min class ID
   * @param maxClassId the max class ID, 0 if not configured
   * @return the profile
   */
  private PdoProfile createProfile(String name, int minClassId, int maxClassId) throws ReflectiveOperationException {
    PdoProfile profile = new PdoProfile();
    Field nameField = Profile.class.getDeclaredField("name");
    nameField.setAccessible(true);
    nameField.set(profile, name);
    profile.setMinClassId(minClassId);
    profile.setMaxClassId(maxClassId);
    return profile;
  }


  /**
   * A single profile without maxClassId is unbounded and starts with its minClassId.
   */
  @Test
  public void testSingleProfile() throws Exception {
    PdoProfile profile = createProfile("md", 1000, 0);
    PdoMojo.updateClassIDs(List.of(profile), List.of(), _ -> null);
    assertEquals(profile.getMinClassId(), 1000);
    assertEquals(profile.getMaxClassId(), Integer.MAX_VALUE);
  }

  /**
   * An explicit maxClassId on the highest profile is valid.
   */
  @Test
  public void testExplicitMaxOnHighestProfile() throws Exception {
    PdoProfile profile = createProfile("md", 1000, 1999);
    PdoMojo.updateClassIDs(List.of(profile), List.of(1000, 1001, 2000), _ -> null);
    assertEquals(profile.getMinClassId(), 1002);
    assertEquals(profile.getMaxClassId(), 1999);
  }

  /**
   * A profile without maxClassId ends right below the next higher profile.
   */
  @Test
  public void testDerivedMax() throws Exception {
    PdoProfile masterData = createProfile("md", 1000, 0);
    PdoProfile transactionData = createProfile("td", 2000, 0);
    PdoMojo.updateClassIDs(List.of(masterData, transactionData), List.of(1005, 99, 2000, 3500), _ -> null);
    assertEquals(masterData.getMinClassId(), 1006);
    assertEquals(masterData.getMaxClassId(), 1999);
    assertEquals(transactionData.getMinClassId(), 3501);
    assertEquals(transactionData.getMaxClassId(), Integer.MAX_VALUE);
  }

  /**
   * The status file wins if it holds a higher class ID.
   */
  @Test
  public void testStatusFile() throws Exception {
    PdoProfile masterData = createProfile("md", 1000, 0);
    PdoProfile transactionData = createProfile("td", 2000, 0);
    Map<String, Integer> status = Map.of("md", 1010, "td", 1500);
    PdoMojo.updateClassIDs(List.of(masterData, transactionData), List.of(1005, 2000),
                           profile -> status.get(profile.getName()));
    assertEquals(masterData.getMinClassId(), 1010);
    assertEquals(transactionData.getMinClassId(), 2001);
  }

  /**
   * Two profiles with the same minClassId overlap.
   */
  @Test
  public void testSameMin() throws Exception {
    List<PdoProfile> profiles = List.of(createProfile("md", 1000, 0), createProfile("td", 1000, 0));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> PdoMojo.updateClassIDs(profiles, List.of(), _ -> null));
    assertTrue(ex.getMessage().contains("must not overlap"), ex.getMessage());
  }

  /**
   * An explicit maxClassId reaching into the next higher profile overlaps.
   */
  @Test
  public void testOverlappingMax() throws Exception {
    List<PdoProfile> profiles = List.of(createProfile("md", 1000, 2000), createProfile("td", 2000, 0));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> PdoMojo.updateClassIDs(profiles, List.of(), _ -> null));
    assertTrue(ex.getMessage().contains("must not overlap"), ex.getMessage());
  }

  /**
   * A maxClassId below the minClassId is invalid.
   */
  @Test
  public void testMaxBelowMin() throws Exception {
    List<PdoProfile> profiles = List.of(createProfile("md", 1000, 999));
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> PdoMojo.updateClassIDs(profiles, List.of(), _ -> null));
    assertTrue(ex.getMessage().contains("lower than its minClassId"), ex.getMessage());
  }

}
