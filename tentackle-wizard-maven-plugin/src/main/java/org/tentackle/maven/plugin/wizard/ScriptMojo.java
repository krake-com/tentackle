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

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.maven.plugin.wizard.fx.ScriptContainer;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.pdo.Pdo;
import org.tentackle.script.Script;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import javafx.application.Platform;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Runs a {@link org.tentackle.script.Script} using the application's dependencies.<br>
 * Brings up an FX UI to edit and run a script or runs it in batch mode.
 * <p>
 * The code a developer writes is only a <em>fragment</em>. It is woven into a
 * <em>harness</em>: a freemarker template (provided via the {@link #harnesses}
 * filesets) that owns the boilerplate &mdash; it connects to the database,
 * implements a {@code DomainContextProvider} so that {@code on()} and {@code op()}
 * can be used, and defines the {@code run()} method the fragment is inserted into.
 * The first line of a harness is a she-bang selecting the scripting language
 * (for example {@code #!groovy}); it does not land in the generated script.
 * <p>
 * Since the fragment is inserted into the {@code run()} method, it cannot contain
 * import statements itself. Instead, the fragment marks such lines with a
 * leading {@code @}-sign, for example {@code @import org.tentackle.misc.*} in
 * Groovy or {@code @require 'json'} in Ruby. Every line starting with an
 * {@code @}-sign is removed from the fragment, stripped of the {@code @}-sign,
 * and provided to the harness via the {@code imports} model variable, which
 * places it at the top level of the generated script.
 * <p>
 * The freemarker model provides four variables: {@code content} (the fragment),
 * {@code imports} (the import statements extracted from the fragment),
 * {@code language} (the language name) and {@code harness} (the harness filename).
 */
@Mojo(name = "script", aggregator = true)
public class ScriptMojo extends AbstractWizardMojo {

  /** marks an import line within a fragment. */
  private static final String IMPORT_MARKER = "@";

  /**
   * The backend URL.
   */
  @Parameter(required = true)
  protected String url;

  /**
   * The backend username.
   */
  @Parameter(required = true)
  public String user;

  /**
   * The backend password.
   */
  @Parameter
  public String password;

  /**
   * The harness scripts.<br>
   * By default, the harness scripts are loaded from the {@code script} sub-directory
   * of the {@code templateDir}. When given, the filesets are used instead.<br>
   * Each harness must start with a she-bang line specifying the scripting language,
   * for example, {@code #!groovy}. The scripting language must be on the classpath.<br>
   * The filenames must be unique among all filesets.
   */
  @Parameter
  public List<FileSet> harnesses;

  /**
   * If given, pre-selects the harness to use.<br>
   * This is the filename without the directory.
   */
  @Parameter(property = "harness")
  public String harness;

  /**
   * The script fragment to run.<br>
   * If given, enters batch mode and runs the fragment with the selected harness.
   * This is the path to a file holding the fragment.
   */
  @Parameter(property = "script")
  public String script;


  private Map<String, ScriptHarness> harnessMap;   // unique harness name :: harness

  private Stage stage;
  private ScriptContainer scriptContainer;


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();
    EncryptedProperties props = PropertiesUtilities.getInstance().create();
    props.setProperty(Constants.BACKEND_URL, url);
    props.setProperty(Constants.BACKEND_USER, user);
    props.setProperty(Constants.BACKEND_PASSWORD, password);
    SessionInfo sessionInfo = Pdo.createSessionInfo(props);
    try {
      Session session = Pdo.createSession(sessionInfo);
      try {
        ModificationTracker pdoTracker = ModificationTracker.getInstance();
        pdoTracker.setSession(session.clone("MT"));
        pdoTracker.start();   // update caches in background

        harnessMap = loadHarnesses();

        if (script != null) {
          // batch mode
          ScriptHarness scriptHarness = selectHarness();
          String fragment;
          try {
            fragment = Files.readString(new File(script).toPath(), StandardCharsets.UTF_8);
          }
          catch (IOException e) {
            throw new MojoExecutionException("cannot read script " + script, e);
          }
          session.makeCurrent();
          runFragment(scriptHarness, fragment);
        }
        else {
          // interactive mode
          CountDownLatch latch = new CountDownLatch(1);

          Platform.startup(() -> {
            try {
              configureFx();
              registerUncaughtExceptionHandler();
              session.makeCurrent();
              scriptContainer = new ScriptContainer(this, session, harnessMap.values());
              stage = scriptContainer.show();
              stage.setOnHidden(_ -> latch.countDown());
            }
            catch (Throwable t) {
              getLog().error(t);
              latch.countDown();
            }
          });

          try {
            latch.await();
          }
          catch (InterruptedException e) {
            throw new InterruptedRuntimeException(e);
          }
        }
      }
      catch (PersistenceException ex) {
        // no database? wrong database? whatever: testing environment incomplete
        throw new MojoExecutionException("no backend found", ex);
      }
      finally {
        if (session != null) {
          ModificationTracker.getInstance().terminate();
          session.close();
        }
      }
    }
    catch (PersistenceException ex) {
      throw new MojoExecutionException("failed to connect to the backend", ex);
    }
  }


  /**
   * Gets the pre-selected harness name.
   *
   * @return the harness filename, null if none
   */
  public String getHarness() {
    return harness;
  }


  /**
   * Weaves a fragment into a harness and runs the resulting script.<br>
   * The session must already be current on the invoking thread.
   *
   * @param scriptHarness the harness
   * @param fragment the script fragment
   * @throws MojoFailureException if weaving or execution failed
   */
  public void runFragment(ScriptHarness scriptHarness, String fragment) throws MojoFailureException {
    String source;
    try {
      source = weave(scriptHarness, fragment);
    }
    catch (IOException | TemplateException e) {
      throw new MojoFailureException("weaving harness '" + scriptHarness.getName() + "' failed", e);
    }
    getLog().debug("generated script:\n" + source);
    try {
      Script compiled = scriptHarness.getLanguage().createScript(source, false, false, null);
      compiled.execute();
    }
    catch (ScriptRuntimeException e) {
      throw new MojoFailureException("script execution failed for harness '" + scriptHarness.getName() + "'", e);
    }
  }


  /**
   * Weaves a fragment into a harness.<br>
   * Lines of the fragment starting with an {@code @}-sign are moved to the
   * {@code imports} model variable, stripped of the {@code @}-sign.
   *
   * @param scriptHarness the harness
   * @param fragment the script fragment
   * @return the generated script source
   * @throws IOException if reading the harness template failed
   * @throws TemplateException if the harness template is invalid
   */
  public String weave(ScriptHarness scriptHarness, String fragment) throws IOException, TemplateException {
    FragmentParts parts = extractImports(fragment);
    if (!parts.imports().isEmpty() && !scriptHarness.getContent().contains("imports")) {
      getLog().warn("harness '" + scriptHarness.getName() +
                    "' does not use the 'imports' variable -> fragment imports dropped");
    }
    ScriptTemplateModel model = new ScriptTemplateModel(
        parts.content(), parts.imports(), scriptHarness.getLanguage().getName(), scriptHarness.getName());
    Configuration cfg = new Configuration(Configuration.VERSION_2_3_34);
    cfg.setDefaultEncoding("UTF-8");
    cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    cfg.setLogTemplateExceptions(false);
    cfg.setWrapUncheckedExceptions(true);
    Template template = new Template(scriptHarness.getName(), new StringReader(scriptHarness.getContent()), cfg);
    StringWriter writer = new StringWriter();
    template.process(model, writer);
    return writer.toString();
  }


  @Override
  protected Stage getStage() {
    return stage;
  }


  /**
   * A fragment split into import statements and the remaining content.
   *
   * @param imports the import statements, one per line, stripped of the {@code @}-sign
   * @param content the fragment without the import lines
   */
  record FragmentParts(String imports, String content) {
  }

  /**
   * Extracts the import lines from a fragment.<br>
   * A line marks an import if it starts with an {@code @}-sign, optionally preceded
   * by whitespace. Such lines are removed from the fragment and collected in the
   * order of appearance, stripped of the {@code @}-sign.
   *
   * @param fragment the script fragment
   * @return the fragment parts
   */
  static FragmentParts extractImports(String fragment) {
    StringBuilder imports = new StringBuilder();
    StringBuilder content = new StringBuilder();
    boolean firstContentLine = true;
    for (String line : fragment.split("\n", -1)) {
      String stripped = line.strip();
      if (stripped.startsWith(IMPORT_MARKER)) {
        if (!imports.isEmpty()) {
          imports.append('\n');
        }
        imports.append(stripped.substring(IMPORT_MARKER.length()));
      }
      else {
        if (firstContentLine) {
          firstContentLine = false;
        }
        else {
          content.append('\n');
        }
        content.append(line);
      }
    }
    return new FragmentParts(imports.toString(), content.toString());
  }


  private ScriptHarness selectHarness() throws MojoFailureException {
    if (harness != null) {
      ScriptHarness scriptHarness = harnessMap.get(harness);
      if (scriptHarness == null) {
        throw new MojoFailureException("no such harness: " + harness);
      }
      return scriptHarness;
    }
    if (harnessMap.size() == 1) {
      return harnessMap.values().iterator().next();
    }
    throw new MojoFailureException("multiple harnesses configured -> select one with -Dharness=<name>");
  }


  private Map<String, ScriptHarness> loadHarnesses() throws MojoExecutionException, MojoFailureException {
    if (harnesses == null) {
      harnesses = new ArrayList<>();
      FileSet fileSet = new FileSet();
      fileSet.setDirectory(new File(getTemplateDir(), org.tentackle.maven.plugin.wizard.Constants.CATEGORY_SCRIPT).getAbsolutePath());
      harnesses.add(fileSet);
    }
    Map<String, ScriptHarness> map = new LinkedHashMap<>();
    for (FileSet fileSet : harnesses) {
      if (fileSet.getDirectory() == null) {
        fileSet.setDirectory(getMavenProject().getBasedir().getAbsolutePath());
      }
      File dir = new File(fileSet.getDirectory());
      for (String fileName : getIncludedFiles(fileSet)) {
        File file = new File(dir, fileName);
        String content;
        try {
          content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
          throw new MojoExecutionException("cannot read harness " + file, e);
        }
        ScriptHarness scriptHarness = new ScriptHarness(file.getName(), content);
        if (map.putIfAbsent(scriptHarness.getName(), scriptHarness) != null) {
          throw new MojoFailureException("duplicate harness name: " + scriptHarness.getName());
        }
      }
    }
    if (map.isEmpty()) {
      throw new MojoFailureException("no harnesses found");
    }
    getLog().info(map.size() + " harness(es) loaded");
    return map;
  }

}
