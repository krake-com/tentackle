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

package org.tentackle.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wurbelizer.misc.Verbosity;

import org.tentackle.buildsupport.AbstractTentackleProcessor;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests how {@link AbstractTentackleAnnotationProcessingMojo} treats the errors of an analyze run.
 * <p>
 * The two kinds of error must not be mixed up: an error reported by one of the annotation
 * processors means that the generated service descriptors or analyze info files are wrong, so the
 * build has to stop. An error in the sources themselves is expected at this point of the build -
 * they have not been wurbeled yet - and only warrants a warning.
 * <p>
 * The mojo is driven through {@code executeImpl()} rather than {@code execute()}, which would
 * reset {@code java.util.logging} for the whole test JVM, and the parameters are injected
 * directly: {@code setMojoParameters()} needs the plugin's class realm.
 */
public class AnnotationProcessingMojoTest {

  /**
   * A processor that reports an error the way an {@code AnalyzeHandler} does.
   */
  @SupportedAnnotationTypes("*")
  public static class FailingProcessor extends AbstractTentackleProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!roundEnv.processingOver()) {
        getProcessingEnvironment().getMessager().printMessage(Kind.ERROR, "no 'value' method in @Service [TestHandler]");
      }
      return false;
    }
  }

  /**
   * A processor that is happy with whatever it is given.
   */
  @SupportedAnnotationTypes("*")
  public static class SilentProcessor extends AbstractTentackleProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      return false;
    }
  }

  /**
   * Records what the mojo logged as an error.
   */
  private static class RecordingLog extends LegacyAbstractLogger {

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial")
    private final List<String> errors = new ArrayList<>();

    @Override
    public boolean isTraceEnabled() {
      return false;
    }

    @Override
    public boolean isDebugEnabled() {
      return false;
    }

    @Override
    public boolean isInfoEnabled() {
      return false;
    }

    @Override
    public boolean isWarnEnabled() {
      return false;
    }

    @Override
    public boolean isErrorEnabled() {
      return true;
    }

    @Override
    public String getName() {
      return "recording";
    }

    @Override
    protected String getFullyQualifiedCallerName() {
      return null;
    }

    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
                                               Object[] arguments, Throwable throwable) {
      if (level == Level.ERROR) {
        errors.add(MessageFormatter.arrayFormat(messagePattern, arguments, throwable).getMessage());
      }
    }
  }


  private Path baseDir;
  private File sourceDir;
  private AbstractTentackleAnnotationProcessingMojo mojo;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() throws Exception {
    baseDir = Files.createTempDirectory("analyze-mojo");
    sourceDir = Files.createDirectories(baseDir.resolve("src/main/java")).toFile();

    MavenProject project = new MavenProject();
    project.setFile(baseDir.resolve("pom.xml").toFile());

    log = new RecordingLog();
    mojo = new AbstractTentackleAnnotationProcessingMojo() { };
    mojo.setLogger(log);
    mojo.encoding = "UTF-8";
    mojo.verbosityLevel = Verbosity.DEFAULT;
    setField(AbstractTentackleMojo.class, mojo, "mavenProject", project);
    setField(AbstractTentackleAnnotationProcessingMojo.class, mojo, "sourceDir", sourceDir);
  }

  @AfterMethod
  public void tearDown() throws IOException {
    try (Stream<Path> paths = Files.walk(baseDir)) {
      paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
    }
  }


  /**
   * An error reported by an annotation processor fails the build - the analyze info it should
   * have written is missing or wrong, and everything downstream builds on it.
   */
  @Test
  public void failsOnAnAnalyzeError() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new FailingProcessor());

    MojoFailureException ex = expectThrows(MojoFailureException.class, mojo::executeImpl);

    assertEquals(ex.getMessage(), "1 analyze errors");
    assertEquals(mojo.getTotalErrors(), 1);
    assertEquals(mojo.getTotalCompileErrors(), 0);
  }

  /**
   * ... and it is reported, even with showCompileOutput off - otherwise the build fails without
   * telling anybody why.
   */
  @Test
  public void reportsTheAnalyzeError() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new FailingProcessor());

    expectThrows(MojoFailureException.class, mojo::executeImpl);

    assertEquals(log.errors.size(), 1, log.errors.toString());
    assertTrue(log.errors.get(0).contains("no 'value' method in @Service [TestHandler]"), log.errors.toString());
    assertTrue(mojo.getCompileErrorLog().contains("no 'value' method in @Service [TestHandler]"),
               mojo.getCompileErrorLog());
  }

  /**
   * An error in the sources is not an analyze error: at this point of the build they have not
   * been wurbeled yet, so the code is expected to be incomplete.
   */
  @Test
  public void onlyWarnsAboutASourceError() throws Exception {
    writeSource("Broken.java", "public class Broken { this is not java }");
    mojo.addProcessor(new SilentProcessor());

    mojo.executeImpl();   // must not throw

    assertEquals(mojo.getTotalErrors(), 0);
    assertTrue(mojo.getTotalCompileErrors() > 0, "expected a compilation error");
    assertTrue(log.errors.isEmpty(), log.errors.toString());
  }

  /**
   * The normal case.
   */
  @Test
  public void succeedsWithoutErrors() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new SilentProcessor());

    mojo.executeImpl();

    assertEquals(mojo.getTotalErrors(), 0);
    assertEquals(mojo.getTotalCompileErrors(), 0);
    assertTrue(log.errors.isEmpty(), log.errors.toString());
  }


  /**
   * Without a source encoding, the compiler's default is used.
   */
  @Test
  public void succeedsWithoutEncoding() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new SilentProcessor());
    mojo.encoding = null;

    mojo.executeImpl();

    assertEquals(mojo.getTotalErrors(), 0);
    assertEquals(mojo.getTotalCompileErrors(), 0);
  }

  /**
   * A {@code -proc:} option in the compiler args replaces the default {@code -proc:only}.
   */
  @Test
  public void honorsProcOptionInCompilerArgs() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new FailingProcessor());
    mojo.compilerArgs = List.of("-proc:none");

    mojo.executeImpl();   // processor not invoked: must not throw

    assertEquals(mojo.getTotalErrors(), 0);
  }

  /**
   * ... as does a {@code -proc:} option given as the single compiler argument.
   */
  @Test
  public void honorsProcOptionInCompilerArgument() throws Exception {
    writeSource("Sample.java", "public class Sample { @Deprecated void x() {} }");
    mojo.addProcessor(new FailingProcessor());
    mojo.compilerArgument = "-proc:none";

    mojo.executeImpl();   // processor not invoked: must not throw

    assertEquals(mojo.getTotalErrors(), 0);
  }


  private void writeSource(String fileName, String content) throws IOException {
    Files.writeString(sourceDir.toPath().resolve(fileName), content);
  }

  private static void setField(Class<?> clazz, Object object, String fieldName, Object value) throws Exception {
    Field field = clazz.getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(object, value);
  }

}
