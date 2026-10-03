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

package org.tentackle.common;

import org.testng.annotations.Test;

import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link LayerBootstrap}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class LayerBootstrapTest {

  private static final Path WORK_DIR = Path.of("/home/user").toAbsolutePath();

  /**
   * Without {@value LayerBootstrap#LAYERS_BASEDIR_PROPERTY}, relative layer directories resolve
   * against the working directory.
   */
  @Test
  public void baseDirDefaultsToTheWorkingDirectory() {
    assertEquals(LayerBootstrap.layersBaseDir(WORK_DIR, null), WORK_DIR);
    assertEquals(LayerBootstrap.layersBaseDir(WORK_DIR, " "), WORK_DIR);
  }

  /**
   * A jpackage launcher starts in an arbitrary working directory, so the image root must be
   * passable explicitly.
   */
  @Test
  public void baseDirCanBeGivenExplicitly() {
    Path imageDir = Path.of("/opt/myapp/lib/runtime").toAbsolutePath();
    assertEquals(LayerBootstrap.layersBaseDir(WORK_DIR, imageDir.toString()), imageDir);
    assertEquals(LayerBootstrap.layersBaseDir(WORK_DIR, "image"), WORK_DIR.resolve("image"));
  }

}
