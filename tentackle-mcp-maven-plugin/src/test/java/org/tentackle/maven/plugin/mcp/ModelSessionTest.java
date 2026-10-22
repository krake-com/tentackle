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

package org.tentackle.maven.plugin.mcp;

import org.tentackle.maven.plugin.mcp.tool.ModelTool;
import org.tentackle.maven.plugin.mcp.tool.ToolArguments;
import org.tentackle.maven.plugin.mcp.tool.ToolRegistry;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests how the session picks up changed model files.
 * <p>
 * The fixture is copied into a scratch directory so that a test may edit and delete model files without
 * disturbing the tests that read the fixture in place. The staleness interval is zero and the timestamps are set
 * explicitly, so that nothing here waits for a clock.
 */
public class ModelSessionTest {

  private Path modelDir;
  private ModelSession session;
  private ToolRegistry registry;

  @BeforeMethod
  public void copyModelAndLoad() throws Exception {
    modelDir = Files.createTempDirectory("mcp-session-test");
    Path fixture = new File(ModelToolTest.modelDir()).toPath();
    try (var files = Files.list(fixture)) {
      for (Path file : files.toList()) {
        Files.copy(file, modelDir.resolve(file.getFileName()));
      }
    }
    ModelConfiguration config = new ModelConfiguration("mcp-session-test", List.of(modelDir.toString()), false,
                                                       new ModelDefaults(ModelToolTest.MODEL_DEFAULTS),
                                                       null, false, null);
    session = new ModelSession(config, 0);    // check on every call: the test controls the timestamps
    session.load();
    registry = new ToolRegistry(1000);
  }

  @AfterMethod(alwaysRun = true)
  public void unloadModelAndRemoveCopy() throws Exception {
    session.getModel().clearModel();
    ModelManager.getInstance().removeModel(session.getModel());
    try (var paths = Files.walk(modelDir)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }

  private String call(String tool, Map<String, Object> args) throws ModelException {
    ModelTool modelTool = registry.getTool(tool);
    return session.call(() -> modelTool.execute(session, ToolArguments.of(args)));
  }

  /**
   * Rewrites a model file and dates it into the future, so that the change is detected regardless of the
   * timestamp resolution of the file system.
   *
   * @param fileName the file name
   * @param search the text to replace
   * @param replacement the replacement
   * @throws IOException if the file could not be rewritten
   */
  private void edit(String fileName, String search, String replacement) throws IOException {
    Path file = modelDir.resolve(fileName);
    String source = Files.readString(file);
    assertTrue(source.contains(search), search + " is not in " + fileName);
    Files.writeString(file, source.replace(search, replacement));
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 10_000));
  }


  @Test
  public void neverAnswersFromAModelThatCouldNotBeLoaded() throws Exception {
    // the removed file forces the full reload, which clears the model before reading the first source
    Files.delete(modelDir.resolve("product.map"));
    edit("customer.map", "name := Customer", "@@@ this is not a model @@@");
    try {
      String text = call("model_list_entities", Map.of());
      fail("answered from a model that could not be loaded: " + text);
    }
    catch (ModelException mex) {
      assertTrue(mex.getMessage().contains("customer.map"), mex.getMessage());
    }
    assertTrue(session.getWarnings().stream().anyMatch(w -> w.contains("still serving the model loaded before")),
               session.getWarnings().toString());
  }

  @Test
  public void recoversWhenTheSourceParsesAgain() throws Exception {
    Files.delete(modelDir.resolve("product.map"));
    edit("customer.map", "name := Customer", "@@@ this is not a model @@@");
    assertThrows(ModelException.class, () -> call("model_list_entities", Map.of()));
    edit("customer.map", "@@@ this is not a model @@@", "name := Customer");
    String text = call("model_list_entities", Map.of());
    assertTrue(text.startsWith("3 of 3 entities"), text);
  }

  @Test
  public void withdrawsTheReloadWarningOnceTheSourceParsesAgain() throws Exception {
    // no file removed: the session refreshes instead of loading, which must not keep the stale warning
    edit("customer.map", "name := Customer", "@@@ this is not a model @@@");
    try {
      call("model_list_entities", Map.of());
    }
    catch (ModelException mex) {
      // whether the broken source surfaces here is up to the model: the warning is what counts
    }
    assertTrue(session.getWarnings().stream().anyMatch(w -> w.startsWith(ModelSession.RELOAD_FAILED_WARNING)),
               session.getWarnings().toString());
    edit("customer.map", "@@@ this is not a model @@@", "name := Customer");
    String text = call("model_overview", Map.of());
    assertFalse(text.contains(ModelSession.RELOAD_FAILED_WARNING), text);
  }

  @Test
  public void picksUpAChangedFileBeforeTheNextCall() throws Exception {
    edit("product.map", "the list price", "what it costs");
    String text = call("model_source", Map.of("entity", "Product", "format", "raw"));
    assertTrue(text.contains("what it costs"), text);
  }

  @Test
  public void reportsWhatAnAutomaticRefreshPickedUp() throws Exception {
    // the staleness check runs before the tool and reloads: model_reload must still name what changed
    edit("product.map", "the list price", "what it costs");
    String text = call("model_reload", Map.of());
    assertTrue(text.contains("changed: Product"), text);
  }

  @Test
  public void reportsAChangeOnlyOnce() throws Exception {
    edit("product.map", "the list price", "what it costs");
    call("model_reload", Map.of());
    String text = call("model_reload", Map.of());
    assertTrue(text.contains("nothing changed"), text);
  }

  @Test
  public void reportsThatNothingChangedWhenNothingDid() throws Exception {
    String text = call("model_reload", Map.of());
    assertTrue(text.contains("nothing changed"), text);
  }

  @Test
  public void dropsAnEntityWhoseFileIsGone() throws Exception {
    // a removed file forces the full reload: a refresh alone keeps the entity in the name and table maps
    Files.delete(modelDir.resolve("product.map"));
    String text = call("model_list_entities", Map.of());
    assertTrue(text.startsWith("3 of 3 entities"), text);
    assertFalse(text.contains("Product"), text);
  }

  @Test
  public void countsTheModelFilesAfterARefresh() throws Exception {
    Files.delete(modelDir.resolve("product.map"));
    call("model_overview", Map.of());
    assertEquals(session.getModelFileCount(), 3);
  }

}
