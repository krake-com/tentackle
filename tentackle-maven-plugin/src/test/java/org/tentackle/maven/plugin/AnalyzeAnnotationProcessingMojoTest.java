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

package org.tentackle.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.buildsupport.AnalyzeProcessor;
import org.tentackle.maven.AbstractTentackleAnnotationProcessingMojo;

import java.io.File;
import java.util.List;
import java.util.TreeSet;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.maven.plugin.MojoTestSupport.RecordingLog;
import static org.tentackle.maven.plugin.MojoTestSupport.createProject;
import static org.tentackle.maven.plugin.MojoTestSupport.createTempDir;
import static org.tentackle.maven.plugin.MojoTestSupport.deleteRecursively;
import static org.tentackle.maven.plugin.MojoTestSupport.readLines;
import static org.tentackle.maven.plugin.MojoTestSupport.readString;
import static org.tentackle.maven.plugin.MojoTestSupport.setField;
import static org.tentackle.maven.plugin.MojoTestSupport.writeFile;

/**
 * Tests for {@link AbstractAnalyzeAnnotationProcessingMojo} and its two goals.
 * <p>
 * The annotation processing itself is covered by the reactor build - every module runs the
 * {@code analyze} goal on itself. What is covered here is the mojo's own logic around it: the
 * validation of the directory parameters, the incremental build support that decides which
 * sources have to be re-analyzed, and the two files written after processing.
 * <p>
 * The mojo is not run via {@code prepareExecute()}: setting the parameters also creates the
 * processing classloader, which requires the plugin's plexus class realm and is not available
 * in a plain test JVM. The parameters are injected instead.
 *
 * @see MojoTestSupport
 */
@SuppressWarnings("missing-explicit-ctor")
public class AnalyzeAnnotationProcessingMojoTest {

  private File basedir;
  private File sourceDir;
  private File analyzeDir;
  private File servicesDir;
  private AnalyzeAnnotationProcessingMojo mojo;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = createTempDir("analyze-mojo");
    sourceDir = new File(basedir, "src" + File.separator + "main" + File.separator + "java");
    analyzeDir = new File(basedir, "target" + File.separator + "analyze");
    servicesDir = new File(basedir, "target" + File.separator + "services");
    log = new RecordingLog();
    mojo = new AnalyzeAnnotationProcessingMojo();
    mojo.setLog(log);
    setField(mojo, "mavenProject", createProject(basedir));
    setParameters(sourceDir, analyzeDir, servicesDir);
  }

  @AfterMethod
  public void tearDown() {
    deleteRecursively(basedir);
  }


  /**
   * The analyze processor is registered by the constructor, since both goals need it.
   */
  @Test
  public void registersTheAnalyzeProcessor() {
    List<?> processors = MojoTestSupport.getField(mojo, "processors");

    assertEquals(processors.size(), 1);
    assertTrue(processors.get(0) instanceof AnalyzeProcessor, processors.toString());
  }

  @Test
  public void validateRequiresASourceDir() {
    setParameters(null, analyzeDir, servicesDir);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertEquals(ex.getMessage(), "missing tentackle.sourceDir");
  }

  @Test
  public void validateRequiresAnAnalyzeDir() {
    setParameters(sourceDir, null, servicesDir);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertEquals(ex.getMessage(), "missing tentackle.analyzeDir");
  }

  @Test
  public void validateRequiresAServicesDir() {
    setParameters(sourceDir, analyzeDir, null);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertEquals(ex.getMessage(), "missing tentackle.serviceDir");
  }

  /**
   * An unresolved property would create a directory literally named {@code ${...}}, which is
   * why it is rejected instead.
   */
  @Test
  public void validateRejectsUnresolvedVariablesInTheAnalyzeDir() {
    setParameters(sourceDir, new File("${project.build.directory}/analyze"), servicesDir);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertTrue(ex.getMessage().startsWith("undefined variable(s) in analyzeDir: "), ex.getMessage());
  }

  @Test
  public void validateRejectsUnresolvedVariablesInTheServicesDir() {
    setParameters(sourceDir, analyzeDir, new File("${tentackle.serviceDir}"));

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, mojo::validate);

    assertTrue(ex.getMessage().startsWith("undefined variable(s) in servicesDir: "), ex.getMessage());
  }

  /**
   * Both output directories are created before processing starts.
   */
  @Test
  public void createsTheOutputDirectories() throws Exception {
    assertTrue(mojo.validate());
    assertFalse(analyzeDir.exists());
    assertFalse(servicesDir.exists());

    mojo.createMissingDirs();

    assertTrue(analyzeDir.isDirectory());
    assertTrue(servicesDir.isDirectory());
  }

  /**
   * A source that has never been analyzed is processed, and its output directory is
   * created on the fly.
   */
  @Test
  public void processesSourcesWithoutAnalyzeResult() throws Exception {
    assertTrue(mojo.validate());
    writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");

    assertEquals(filterFileNames("com/example/Foo.java"), List.of(sourceName("com/example/Foo.java")));
    assertTrue(new File(analyzeDir, "com" + File.separator + "example" + File.separator + "Foo").isDirectory());
  }

  /**
   * A source modified after it was last analyzed is processed again.
   */
  @Test
  public void processesChangedSources() throws Exception {
    assertTrue(mojo.validate());
    File source = writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");
    File output = analyzeResultDir("com/example/Foo");
    assertTrue(output.setLastModified(startedMillis() - 20000));
    assertTrue(source.setLastModified(startedMillis() - 10000));

    assertEquals(filterFileNames("com/example/Foo.java"), List.of(sourceName("com/example/Foo.java")));
  }

  /**
   * An unchanged source is skipped - this is what keeps incremental builds fast.
   */
  @Test
  public void skipsUnchangedSources() throws Exception {
    assertTrue(mojo.validate());
    File source = writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");
    File output = analyzeResultDir("com/example/Foo");
    assertTrue(source.setLastModified(startedMillis() - 20000));
    assertTrue(output.setLastModified(startedMillis() - 10000));

    assertEquals(filterFileNames("com/example/Foo.java"), List.of());
  }

  /**
   * The oldest file of the analyze directory counts, not the directory itself: a partial
   * result must be completed.
   */
  @Test
  public void processesSourcesWithAPartialAnalyzeResult() throws Exception {
    assertTrue(mojo.validate());
    File source = writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");
    File output = analyzeResultDir("com/example/Foo");
    File result = writeFile(output, "Foo.class.txt", "stale\n");
    assertTrue(output.setLastModified(startedMillis() - 10000));
    assertTrue(result.setLastModified(startedMillis() - 30000));
    assertTrue(source.setLastModified(startedMillis() - 20000));

    assertEquals(filterFileNames("com/example/Foo.java"), List.of(sourceName("com/example/Foo.java")));
  }

  /**
   * An analyze directory created within this very run belongs to a source already processed
   * by another fileset. It is always re-processed, which also covers case-insensitive
   * filesystems where a class and a sub-package may share a name.
   */
  @Test
  public void alwaysProcessesSourcesAnalyzedWithinTheSameRun() throws Exception {
    assertTrue(mojo.validate());
    File source = writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");
    File output = analyzeResultDir("com/example/Foo");
    assertTrue(output.setLastModified(startedMillis() + 5000));   // i.e. created after the mojo started
    assertTrue(source.setLastModified(startedMillis() - 20000));

    assertEquals(filterFileNames("com/example/Foo.java"), List.of(sourceName("com/example/Foo.java")));
  }

  /**
   * Skipped sources are reported in debug mode.
   */
  @Test
  public void logsSkippedSourcesInDebugMode() throws Exception {
    log.setDebugEnabled(true);
    assertTrue(mojo.validate());
    File source = writeFile(sourceDir, "com/example/Foo.java", "// Foo\n");
    File output = analyzeResultDir("com/example/Foo");
    assertTrue(source.setLastModified(startedMillis() - 20000));
    assertTrue(output.setLastModified(startedMillis() - 10000));

    filterFileNames("com/example/Foo.java");

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "skipped unchanged"), log.getInfoMessages().toString());
  }

  /**
   * The optional index lists the generated service files, so that a modular application can
   * enumerate them without scanning the classpath.
   */
  @Test
  public void writesTheServiceIndex() throws Exception {
    mojo.createMissingDirs();
    mojo.index = "META-INF/RESOURCE-INDEX.LIST";
    setField(mojo, "generatedServices", new TreeSet<>(List.of("org.tentackle.common.ServiceB",
                                                              "org.tentackle.common.ServiceA")));

    mojo.finishExecute();

    File index = new File(servicesDir, "META-INF" + File.separator + "RESOURCE-INDEX.LIST");
    assertEquals(readLines(index), List.of("org.tentackle.common.ServiceA", "org.tentackle.common.ServiceB"));
  }

  /**
   * No index without an {@code index} parameter, even if services were generated.
   */
  @Test
  public void writesNoServiceIndexByDefault() throws Exception {
    mojo.createMissingDirs();
    setField(mojo, "generatedServices", new TreeSet<>(List.of("org.tentackle.common.ServiceA")));

    mojo.finishExecute();

    assertFalse(new File(servicesDir, "META-INF").exists());
  }

  /**
   * Compilation errors during the analyze phase leave the analyze info incomplete. The marker
   * file tells the subsequent build phases about it.
   */
  @Test
  public void writesTheCompileErrorLog() throws Exception {
    mojo.createMissingDirs();
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "totalCompileErrors", 2);
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "compileErrorLog",
             new StringBuilder("Foo.java: cannot find symbol\n"));

    mojo.finishExecute();

    assertEquals(readString(new File(analyzeDir, AnalyzeProcessor.COMPILE_ERROR_LOG)),
                 "Foo.java: cannot find symbol\n2 compilation errors" + System.lineSeparator());
  }

  /**
   * Without compile errors there is no marker file - its mere existence is the signal.
   */
  @Test
  public void writesNoCompileErrorLogOnSuccess() throws Exception {
    mojo.createMissingDirs();

    mojo.finishExecute();

    assertFalse(new File(analyzeDir, AnalyzeProcessor.COMPILE_ERROR_LOG).exists());
  }


  /**
   * Injects the parameters that {@code prepareExecute()} would copy over.
   */
  private void setParameters(File source, File analyze, File services) {
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "sourceDir", source);
    setField(AbstractAnalyzeAnnotationProcessingMojo.class, mojo, "analyzeDir", analyze);
    setField(AbstractAnalyzeAnnotationProcessingMojo.class, mojo, "servicesDir", services);
  }

  private List<String> filterFileNames(String... fileNames) {
    String[] names = new String[fileNames.length];
    for (int i = 0; i < fileNames.length; i++) {
      names[i] = sourceName(fileNames[i]);
    }
    return List.of(mojo.filterFileNames(sourceDir.getPath(), names));
  }

  /**
   * The file names come from the fileset scanner and are thus relative and platform-separated.
   */
  private String sourceName(String path) {
    return path.replace('/', File.separatorChar);
  }

  private File analyzeResultDir(String path) {
    File dir = new File(analyzeDir, sourceName(path));
    assertTrue(dir.mkdirs());
    return dir;
  }

  private long startedMillis() {
    return MojoTestSupport.<Long>getField(mojo, "startedMillis");
  }

}
