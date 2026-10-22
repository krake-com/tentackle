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

import org.testng.annotations.Test;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests the imports the templates need for super types and validation annotations.
 */
public class SuperTypeImportTest {

  @Test
  public void testValidationImports() throws Exception {
    Path dir = Files.createTempDirectory("validation-imports");
    try {
      Files.writeString(dir.resolve("Asset.txt"), """
          name := Asset
          id := 1010
          table := asset

          ## attributes
          String(20)   code       code       the code
          String(80)   name       name       the name
          long         parentId   parent_id  the parent id

          ## relations
          Asset:
            name = Parent,
            @NotNull

          ## validations
          code: @NotNull(message="missing code"), @com.example.validate.MyCheck
          name: @NotEmpty, @MyAppSpecific
          """, StandardCharsets.UTF_8);

      Model model = ModelManager.getInstance().getModel("validation-imports-test");
      model.loadFromDirectory(dir.toString(), true);
      Entity entity = model.getByEntityName("Asset");
      assertNotNull(entity);

      // fully qualified and unknown annotations are not imported, duplicates only once, sorted
      assertEquals(PdoGenerator.determineValidationImports(entity),
                   "import org.tentackle.validate.validator.NotEmpty;\n" +
                   "import org.tentackle.validate.validator.NotNull;\n");
    }
    finally {
      try (Stream<Path> paths = Files.walk(dir)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(path);
        }
      }
    }
  }

}
