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

package org.tentackle.buildsupport;

import org.testng.annotations.Test;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.File;

import static org.testng.Assert.assertEquals;

/**
 * Tests for {@link AbstractAnalyzeHandler}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class AbstractAnalyzeHandlerTest {

  private static class TestHandler extends AbstractAnalyzeHandler {

    @Override
    public void setProcessor(AnalyzeProcessor processor) {
      // not needed for this test
    }

    @Override
    public AnalyzeProcessor getProcessor() {
      return null;
    }

    @Override
    public void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {
      // not needed for this test
    }
  }

  @Test
  public void turnsADottedClassNameIntoNestedDirectories() {
    File baseDir = new File("base");
    File directory = new TestHandler().getDirectory(baseDir, "com.example.Foo");

    File expected = new File(new File(new File(baseDir, "com"), "example"), "Foo");
    assertEquals(directory, expected);
  }

  @Test
  public void aSimpleClassNameWithoutPackageYieldsASingleLevelDirectory() {
    File baseDir = new File("base");
    File directory = new TestHandler().getDirectory(baseDir, "Foo");

    assertEquals(directory, new File(baseDir, "Foo"));
  }

}
