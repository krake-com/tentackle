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

package org.tentackle.maven.plugin.sql;

import org.testng.annotations.Test;

import java.io.File;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class SemanticVersionFileFilterTest {

  /**
   * The directory name (not the filename) carries the version, per {@link SemanticVersionFileFilter#isValid}.
   */
  @Test
  public void isValidDelegatesToTheParentDirectoryName() {
    SemanticVersionFileFilter fileFilter = new SemanticVersionFileFilter();
    File hintFile = new File(new File("1.2.4"), "hints.txt");

    assertTrue(fileFilter.isValid("1.2.3", hintFile));
    assertFalse(fileFilter.isValid("1.2.4", hintFile));
  }

  /**
   * A directory name not starting with a digit is treated as permanent, i.e. it always applies -
   * regardless of the given version, as long as a version is given at all.
   */
  @Test
  public void isValidAlwaysAppliesIfDirectoryDoesNotStartWithADigit() {
    SemanticVersionFileFilter fileFilter = new SemanticVersionFileFilter();
    File hintFile = new File(new File("permanent"), "hints.txt");

    assertTrue(fileFilter.isValid("1.2.3", hintFile));
    assertTrue(fileFilter.isValid("anything", hintFile));
  }

  /**
   * No parent directory or no version at all: not valid - even for a permanent (non-digit)
   * directory name, a missing version makes the hint inapplicable rather than always applying.
   */
  @Test
  public void isValidRequiresAParentDirectoryAndAVersion() {
    SemanticVersionFileFilter fileFilter = new SemanticVersionFileFilter();

    assertFalse(fileFilter.isValid("1.2.3", new File("hints.txt")));
    assertFalse(fileFilter.isValid(null, new File(new File("1.2.4"), "hints.txt")));
    assertFalse(fileFilter.isValid(null, new File(new File("permanent"), "hints.txt")));
  }

  @Test
  public void testVersions() {
    SemanticVersionFileFilter fileFilter = new SemanticVersionFileFilter();

    assertTrue(fileFilter.isHintApplying("0.9.9", "1.0"));
    assertTrue(fileFilter.isHintApplying("0.9.9", "1.0-SNAPSHOT"));
    assertTrue(fileFilter.isHintApplying("1.0.0", "1.0.1"));
    assertFalse(fileFilter.isHintApplying("1.0.0", "1.0-SNAPSHOT"));
    assertFalse(fileFilter.isHintApplying("1.2.3", "1.2-SNAPSHOT"));
    assertTrue(fileFilter.isHintApplying("1.2.3", "1.3"));
    assertFalse(fileFilter.isHintApplying("1.2.3", "1.2.3"));
    assertFalse(fileFilter.isHintApplying("1.2.3", "1_2_3"));
    assertTrue(fileFilter.isHintApplying("1.2.3", "1.2.4"));
    assertFalse(fileFilter.isHintApplying("1.2.3", "1.2.3-SNAPSHOT"));
    assertTrue(fileFilter.isHintApplying("1.2.3", "1.3-SNAPSHOT"));
    assertFalse(fileFilter.isHintApplying("3.2.0", "3.2-SNAPSHOT"));
    assertFalse(fileFilter.isHintApplying("3.0.0", "3-SNAPSHOT"));
    assertFalse(fileFilter.isHintApplying("3.2.0", "3.2.0-SNAPSHOT"));
    assertFalse(fileFilter.isHintApplying("3.2.0", "3.1.9"));
    assertTrue(fileFilter.isHintApplying("3.1.9", "3.2.0-M3"));
    assertFalse(fileFilter.isHintApplying("3.2.0", ""));
    assertFalse(fileFilter.isHintApplying("", ""));
    assertTrue(fileFilter.isHintApplying("", "1.0"));
    assertTrue(fileFilter.isHintApplying("", "x"));
    assertFalse(fileFilter.isHintApplying("x", ""));
    assertFalse(fileFilter.isHintApplying("x", "1.0"));
  }
}
