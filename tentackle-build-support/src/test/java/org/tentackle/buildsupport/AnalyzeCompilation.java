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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertNotNull;

/**
 * Runs the {@link AnalyzeProcessor} in-process over in-memory sources, the way the analyze goals of the
 * tentackle-maven-plugin do.
 * <p>
 * The sources are only processed, not compiled ({@code -proc:only}).
 * The handlers write to a fresh analyze- and service-directory below {@code target}.
 *
 * @author harald
 */
final class AnalyzeCompilation {

  private final File analyzeDir;
  private final File serviceDir;
  private final List<Diagnostic<? extends JavaFileObject>> diagnostics;

  /**
   * Processes the given sources.
   *
   * @param processingClassLoader the processing classloader, null if default
   * @param sources the sources, mapped by their fully qualified classnames
   * @return the compilation result
   * @throws IOException if processing failed
   */
  static AnalyzeCompilation process(ClassLoader processingClassLoader, Map<String, String> sources) throws IOException {
    return new AnalyzeCompilation(processingClassLoader, sources);
  }

  /**
   * Processes the given sources with the default classloader.
   *
   * @param sources the sources, mapped by their fully qualified classnames
   * @return the compilation result
   * @throws IOException if processing failed
   */
  static AnalyzeCompilation process(Map<String, String> sources) throws IOException {
    return process(null, sources);
  }


  private AnalyzeCompilation(ClassLoader processingClassLoader, Map<String, String> sources) throws IOException {
    Path baseDir = Files.createTempDirectory(Files.createDirectories(Path.of("target", "analyze-tests")), "run");
    analyzeDir = baseDir.resolve("analyze").toFile();
    serviceDir = baseDir.resolve("services").toFile();

    List<JavaFileObject> units = new ArrayList<>();
    for (Map.Entry<String, String> entry : sources.entrySet()) {
      units.add(new SourceFile(entry.getKey(), entry.getValue()));
    }

    AnalyzeProcessor processor = new AnalyzeProcessor();
    processor.setAnalyzeDir(analyzeDir);
    processor.setServiceDir(serviceDir);
    processor.setProcessingClassLoader(processingClassLoader);

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "no system java compiler");
    DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(collector, null, StandardCharsets.UTF_8)) {
      JavaCompiler.CompilationTask task = compiler.getTask(
          null, fileManager, collector,
          List.of("-proc:only", "-classpath", System.getProperty("java.class.path")),
          null, units);
      task.setProcessors(List.of(processor));
      task.call();
    }
    finally {
      processor.cleanup();
    }
    diagnostics = collector.getDiagnostics();
  }

  /**
   * Gets a file in the service directory.
   *
   * @param relativeName the slash-separated name relative to the service directory
   * @return the file
   */
  File serviceFile(String relativeName) {
    return new File(serviceDir, relativeName);
  }

  /**
   * Gets a file in the analyze directory.
   *
   * @param relativeName the slash-separated name relative to the analyze directory
   * @return the file
   */
  File analyzeFile(String relativeName) {
    return new File(analyzeDir, relativeName);
  }

  /**
   * Reads the lines of a file in the service directory.
   *
   * @param relativeName the slash-separated name relative to the service directory
   * @return the lines
   * @throws IOException if the file does not exist
   */
  List<String> serviceLines(String relativeName) throws IOException {
    return Files.readAllLines(serviceFile(relativeName).toPath());
  }

  /**
   * Reads the lines of a file in the analyze directory.
   *
   * @param relativeName the slash-separated name relative to the analyze directory
   * @return the lines
   * @throws IOException if the file does not exist
   */
  List<String> analyzeLines(String relativeName) throws IOException {
    return Files.readAllLines(analyzeFile(relativeName).toPath());
  }

  /**
   * Gets the messages of all diagnostics of the given kind.
   *
   * @param kind the diagnostic kind
   * @return the messages
   */
  List<String> messages(Diagnostic.Kind kind) {
    List<String> messages = new ArrayList<>();
    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      if (diagnostic.getKind() == kind) {
        messages.add(diagnostic.getMessage(null));
      }
    }
    return messages;
  }


  /**
   * An in-memory source file.
   */
  private static class SourceFile extends SimpleJavaFileObject {

    private final String code;

    private SourceFile(String className, String code) {
      super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

}
