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

package org.tentackle.apt;

import org.testng.annotations.Test;

import org.tentackle.reflect.interceptable.LogIt;
import org.tentackle.reflect.interceptable.Sealed;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Verifies that {@link AllInterceptorAnnotationProcessor} rejects an interceptor annotation
 * applied to a method marked {@code @NotInterceptable} (or to an override of one), and that it
 * otherwise leaves an unrelated override alone.
 * <p>
 * Uses the real {@code javac} against the fixtures in {@code org.tentackle.reflect.interceptable}
 * ({@code Sealed}, {@code LogIt}) so the compile-time check under test runs exactly as it would
 * for any downstream module.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class NotInterceptableProcessorTest {

  @Test
  public void testUnrelatedOverrideCompiles() {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(diagnostics, """
        package org.tentackle.apt.fixture;
        import org.tentackle.reflect.interceptable.LogIt;
        import org.tentackle.reflect.interceptable.Sealed;
        public interface ValidOverride extends Sealed {
          @Override
          @LogIt
          void open();
        }
        """, "org.tentackle.apt.fixture.ValidOverride");
    assertTrue(success, "unrelated override must compile: " + diagnostics.getDiagnostics());
  }

  @Test
  public void testInterceptingSealedMethodFailsToCompile() {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(diagnostics, """
        package org.tentackle.apt.fixture;
        import org.tentackle.reflect.interceptable.LogIt;
        import org.tentackle.reflect.interceptable.Sealed;
        public interface InvalidOverride extends Sealed {
          @Override
          @LogIt
          void guarded();
        }
        """, "org.tentackle.apt.fixture.InvalidOverride");
    assertFalse(success, "intercepting a @NotInterceptable method must fail to compile");
    assertTrue(diagnostics.getDiagnostics().stream().anyMatch(
        d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage(null).contains("NotInterceptable")),
        "expected a @NotInterceptable-related error, got: " + diagnostics.getDiagnostics());
  }


  private boolean compile(DiagnosticCollector<JavaFileObject> diagnostics, String source, String className) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    File outputDir = createTempDir();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      List<String> options = List.of("-d", outputDir.getPath(), "-classpath", classpath());
      JavaCompiler.CompilationTask task = compiler.getTask(
          null, fileManager, diagnostics, options, null, List.of(new StringSource(className, source)));
      task.setProcessors(List.of(new AllInterceptorAnnotationProcessor()));
      return task.call();
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * Builds the classpath the fixture sources are compiled against.
   * <p>
   * Test sources run patched into the {@code org.tentackle.core} module (module-path launch,
   * no test {@code module-info.java}), so {@code java.class.path} alone does not necessarily
   * contain the module's own {@code target/classes}/{@code target/test-classes} directories.
   * Deriving them from the code source of representative classes works regardless of how the
   * JVM was launched.
   */
  private static String classpath() {
    Set<String> elements = new LinkedHashSet<>();
    for (Class<?> clazz : List.of(Sealed.class, LogIt.class, AllInterceptorAnnotationProcessor.class)) {
      elements.add(classpathEntry(clazz));
    }
    String classPath = System.getProperty("java.class.path");
    if (classPath != null && !classPath.isBlank()) {
      elements.addAll(List.of(classPath.split(File.pathSeparator)));
    }
    return String.join(File.pathSeparator, elements);
  }

  private static String classpathEntry(Class<?> clazz) {
    try {
      return new File(clazz.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
    }
    catch (URISyntaxException ex) {
      throw new IllegalStateException("cannot locate class path entry for " + clazz, ex);
    }
  }

  private static File createTempDir() {
    try {
      return Files.createTempDirectory("notinterceptable-processor-test").toFile();
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }


  private static final class StringSource extends SimpleJavaFileObject {

    private final String code;

    StringSource(String className, String code) {
      super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

}
