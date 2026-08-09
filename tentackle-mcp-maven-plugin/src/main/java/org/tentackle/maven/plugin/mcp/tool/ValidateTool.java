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

package org.tentackle.maven.plugin.mcp.tool;

import org.tentackle.maven.plugin.mcp.ModelConfiguration;
import org.tentackle.maven.plugin.mcp.ModelSession;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validates the model or a candidate definition that has not been written anywhere.
 * <p>
 * Candidate validation is the point of the whole server: it turns "write a model block, run the reactor, read
 * the {@code WurbelTerminationException}" into a sub-second loop. The candidate is staged into a scratch copy of
 * the model sources, so nothing on disk and nothing in the live model is touched.
 *
 * @author harald
 */
public class ValidateTool implements ModelTool {

  /** The tool name. */
  public static final String NAME = "model_validate";

  /** The name suffix of the scratch model a candidate is validated in. */
  public static final String CANDIDATE_MODEL_SUFFIX = "-mcp-candidate";

  /**
   * Creates the tool.
   */
  public ValidateTool() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getTitle() {
    return "Validate model";
  }

  @Override
  public String getDescription() {
    return """
        Validates the model. Without arguments it reloads and checks everything that is configured. With \
        'source' it validates a candidate model definition that exists nowhere on disk: the candidate is \
        staged into a scratch copy of the model sources -- replacing 'entity' if given, added as a new entity \
        otherwise -- relations are resolved across the whole set and the custom validators run. Nothing is \
        written and the live model is untouched. Use this before writing a '@> ... @<' block into a PDO \
        interface. Read the tentackle-model://grammar/model-definition resource for the DSL.""";
  }

  @Override
  public String getInputSchema() {
    return new SchemaBuilder()
        .string("source", "A candidate model definition in the model-definition DSL, without the '@>' and " +
                          "'@<' delimiters. Omit to validate what is loaded.")
        .string("entity", "The entity the candidate replaces. Omit to validate it as a new entity.")
        .build();
  }

  @Override
  public String execute(ModelSession session, ToolArguments args) throws ModelException {
    String source = args.string("source", null);
    String entityName = args.string("entity", null);

    if (source == null) {
      if (entityName != null) {
        throw new IllegalArgumentException("'entity' only makes sense together with 'source'");
      }
      return validateLoaded(session);
    }
    return validateCandidate(session, source, entityName);
  }

  private String validateLoaded(ModelSession session) throws ModelException {
    List<String> changed = session.reload(true);
    StringBuilder buf = new StringBuilder(512);
    buf.append("model is consistent: ").append(session.getModel().getAllEntities().size())
       .append(" entities, ").append(session.getModelFileCount()).append(" files\n");
    if (!changed.isEmpty()) {
      buf.append("changed since the last check: ").append(String.join(", ", changed)).append('\n');
    }
    appendWarnings(buf, session);
    return buf.toString();
  }

  private String validateCandidate(ModelSession session, String source, String entityName) throws ModelException {
    ModelConfiguration config = session.getConfiguration();
    if (config.modelDirs().isEmpty()) {
      return "candidate validation needs a directory-backed model, but this server was configured " +
             "without a model directory";
    }

    Entity replaced = null;
    String fileName = null;
    int dirIndex = config.modelDirs().size() - 1;
    if (entityName != null) {
      replaced = session.getModel().getByEntityName(entityName);
      if (replaced == null) {
        throw new IllegalArgumentException("no entity named '" + entityName + "' to replace");
      }
      EntityInfo info = session.getModel().getEntityInfo(replaced);
      if (info != null && info.isFile()) {
        Path file = Path.of(info.getURL().getPath());
        fileName = file.getFileName().toString();
        for (int i = 0; i < config.modelDirs().size(); i++) {
          if (file.getParent() != null
              && file.getParent().toAbsolutePath().equals(Path.of(config.modelDirs().get(i)).toAbsolutePath())) {
            dirIndex = i;
            break;
          }
        }
      }
    }
    if (fileName == null) {
      fileName = "candidate" + ModelSession.MODEL_FILE_EXTENSION;
    }

    Path scratch = null;
    Model candidateModel = null;
    try {
      scratch = Files.createTempDirectory("tentackle-mcp-candidate");
      List<Path> dirs = stage(config, scratch, dirIndex, fileName, source);

      candidateModel = ModelManager.getInstance().getModel(config.modelName() + CANDIDATE_MODEL_SUFFIX);
      candidateModel.clearModel();
      candidateModel.setModelDefaults(config.modelDefaults());
      candidateModel.setEntityAliases(config.entityAliases());
      candidateModel.setSchemaNameMapped(config.schemaNameMapped());
      if (!session.getBackends().isEmpty()) {
        candidateModel.getEntityFactory().setBackends(session.getBackends());
      }

      for (Path dir : dirs) {
        candidateModel.loadFromDirectory(dir.toString(), false);
      }
      candidateModel.updateRelations();

      StringBuilder buf = new StringBuilder(512);
      buf.append("candidate (").append(replaced == null ? "new entity" : "replacing " + replaced.getName())
         .append(") is consistent: ").append(candidateModel.getAllEntities().size()).append(" entities\n");
      appendWarnings(buf, session);
      buf.append("\nNothing was written. Paste the definition into the PDO interface yourself.");
      return buf.toString();
    }
    catch (ModelException mex) {
      StringBuilder buf = new StringBuilder(512);
      buf.append("candidate (").append(replaced == null ? "new entity" : "replacing " + replaced.getName())
         .append(") is NOT consistent:\n\n").append(mex.getMessage()).append('\n');
      appendWarnings(buf, session);
      return buf.toString();
    }
    catch (IOException iox) {
      throw new UncheckedIOException("cannot stage the candidate model", iox);
    }
    finally {
      if (candidateModel != null) {
        candidateModel.clearModel();
        ModelManager.getInstance().removeModel(candidateModel);
      }
      deleteRecursively(scratch);
    }
  }

  /**
   * Copies the model directories into the scratch area, with the candidate in place of (or in addition to) the
   * replaced entity's file.
   *
   * @param config the model configuration
   * @param scratch the scratch directory
   * @param dirIndex the index of the directory receiving the candidate
   * @param fileName the file name of the candidate
   * @param source the candidate source
   * @return the staged directories, in load order
   * @throws IOException if staging failed
   */
  private List<Path> stage(ModelConfiguration config, Path scratch, int dirIndex, String fileName, String source)
         throws IOException {
    List<Path> staged = new ArrayList<>();
    for (int i = 0; i < config.modelDirs().size(); i++) {
      Path target = scratch.resolve(String.valueOf(i));
      Files.createDirectories(target);
      Path original = Path.of(config.modelDirs().get(i));
      if (Files.isDirectory(original)) {
        try (var files = Files.list(original)) {
          for (Path file : files.toList()) {
            if (Files.isRegularFile(file)) {
              Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
          }
        }
      }
      if (i == dirIndex) {
        Files.writeString(target.resolve(fileName), source, StandardCharsets.UTF_8);
      }
      staged.add(target);
    }
    return staged;
  }

  private void appendWarnings(StringBuilder buf, ModelSession session) {
    List<String> warnings = session.getWarnings();
    if (!warnings.isEmpty()) {
      buf.append("\nconfiguration warnings (these affect what is validated):\n");
      for (String warning : warnings) {
        buf.append("- ").append(warning).append('\n');
      }
    }
  }

  private void deleteRecursively(Path path) {
    if (path == null || !Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      for (Path file : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(file);
      }
    }
    catch (IOException iox) {
      throw new UncheckedIOException("cannot remove the scratch directory " + path, iox);
    }
  }

}
