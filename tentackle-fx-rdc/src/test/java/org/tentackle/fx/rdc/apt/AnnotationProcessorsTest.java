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

package org.tentackle.fx.rdc.apt;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.rdc.DefaultGuiProvider;
import org.tentackle.pdo.PersistentDomainObject;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.annotation.processing.Processor;
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
 * Verifies the compile-time checks of {@link GuiProviderServiceAnnotationProcessor},
 * {@link PdoTableContextMenuItemServiceAnnotationProcessor} and
 * {@link PdoTreeContextMenuItemServiceAnnotationProcessor}.
 * <p>
 * Each processor runs in the real {@code javac} against small in-memory fixtures:
 * a valid service class must compile, whereas a missing public constructor, a constructor
 * with the wrong parameter type, a missing interface or an abstract class must be rejected.
 * No JavaFX toolkit is needed, since nothing is instantiated.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class AnnotationProcessorsTest {

  private static final String CLASS_NAME = "org.tentackle.fx.rdc.apt.fixture.Fixture";


  /**
   * Describes one processor under test.
   *
   * @param name the name shown in the test report
   * @param processor creates the processor
   * @param annotation the annotation, including its mandatory attributes
   * @param paramType the type of the mandatory constructor parameter
   * @param superTypes the extends/implements clause of a valid service class
   * @param superCall the constructor body of a valid service class
   * @param missingInterface the error message fragment if the interface is not implemented
   */
  public record ProcessorSpec(String name, Supplier<Processor> processor, String annotation, String paramType,
                               String superTypes, String superCall, String missingInterface) {
    @Override
    public String toString() {
      return name;
    }
  }


  @DataProvider
  public Object[][] processors() {
    return new Object[][] {
        { new ProcessorSpec("GuiProviderService", GuiProviderServiceAnnotationProcessor::new,
                            "@org.tentackle.fx.rdc.GuiProviderService(PersistentDomainObject.class)",
                            "T",
                            "extends org.tentackle.fx.rdc.DefaultGuiProvider<T>",
                            "super(arg);",
                            "must implement org.tentackle.fx.rdc.GuiProvider") },
        { new ProcessorSpec("PdoTableContextMenuItemService", PdoTableContextMenuItemServiceAnnotationProcessor::new,
                            "@org.tentackle.fx.rdc.PdoTableContextMenuItemService(value = PersistentDomainObject.class, ordinal = 1)",
                            "org.tentackle.fx.rdc.PdoTableCell<?, T>",
                            "implements org.tentackle.fx.rdc.PdoTableContextMenuItem<T>",
                            "",
                            "must implement org.tentackle.fx.rdc.PdoTableContextMenuItem") },
        { new ProcessorSpec("PdoTreeContextMenuItemService", PdoTreeContextMenuItemServiceAnnotationProcessor::new,
                            "@org.tentackle.fx.rdc.PdoTreeContextMenuItemService(value = PersistentDomainObject.class, ordinal = 1)",
                            "org.tentackle.fx.rdc.PdoTreeCell<T>",
                            "implements org.tentackle.fx.rdc.PdoTreeContextMenuItem<T>",
                            "",
                            "must implement org.tentackle.fx.rdc.PdoTreeContextMenuItem") }
    };
  }


  @Test(dataProvider = "processors")
  public void testValidServiceCompiles(ProcessorSpec spec) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(spec, diagnostics, fixture(spec.annotation(), "", spec.superTypes(),
                                                         "public", spec.paramType(), spec.superCall()));
    assertTrue(success, spec + ": valid service class must compile: " + diagnostics.getDiagnostics());
  }

  @Test(dataProvider = "processors")
  public void testNonPublicConstructorIsRejected(ProcessorSpec spec) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(spec, diagnostics, fixture(spec.annotation(), "", spec.superTypes(),
                                                         "protected", spec.paramType(), spec.superCall()));
    assertFalse(success, spec + ": a non-public constructor must be rejected");
    assertError(diagnostics, "needs constructor");
  }

  @Test(dataProvider = "processors")
  public void testWrongConstructorParameterIsRejected(ProcessorSpec spec) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    // a (String) constructor: for the GuiProvider fixture the super constructor gets a null PDO
    String superCall = spec.superCall().isEmpty() ? "" : "super(null);";
    boolean success = compile(spec, diagnostics, fixture(spec.annotation(), "", spec.superTypes(),
                                                         "public", "String", superCall));
    assertFalse(success, spec + ": a constructor with the wrong parameter type must be rejected");
    assertError(diagnostics, "needs constructor");
  }

  @Test(dataProvider = "processors")
  public void testMissingInterfaceIsRejected(ProcessorSpec spec) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(spec, diagnostics, fixture(spec.annotation(), "", "",
                                                         "public", spec.paramType(), ""));
    assertFalse(success, spec + ": a class not implementing the service interface must be rejected");
    assertError(diagnostics, spec.missingInterface());
  }

  @Test(dataProvider = "processors")
  public void testAbstractClassIsRejected(ProcessorSpec spec) {
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    boolean success = compile(spec, diagnostics, fixture(spec.annotation(), "abstract ", spec.superTypes(),
                                                         "public", spec.paramType(), spec.superCall()));
    assertFalse(success, spec + ": an abstract class must be rejected");
    assertError(diagnostics, "cannot be abstract");
  }


  private static String fixture(String annotation, String classModifier, String superTypes,
                                String ctorModifier, String paramType, String body) {
    return """
        package org.tentackle.fx.rdc.apt.fixture;
        import org.tentackle.pdo.PersistentDomainObject;
        %s
        public %sclass Fixture<T extends PersistentDomainObject<T>> %s {
          %s Fixture(%s arg) {
            %s
          }
        }
        """.formatted(annotation, classModifier, superTypes, ctorModifier, paramType, body);
  }

  private static void assertError(DiagnosticCollector<JavaFileObject> diagnostics, String fragment) {
    assertTrue(diagnostics.getDiagnostics().stream().anyMatch(
        d -> d.getKind() == Diagnostic.Kind.ERROR && d.getMessage(null).contains(fragment)),
        "expected an error containing '" + fragment + "', got: " + diagnostics.getDiagnostics());
  }

  private boolean compile(ProcessorSpec spec, DiagnosticCollector<JavaFileObject> diagnostics, String source) {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    File outputDir = createTempDir();
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
      List<String> options = List.of("-d", outputDir.getPath(), "-classpath", classpath());
      JavaCompiler.CompilationTask task = compiler.getTask(
          null, fileManager, diagnostics, options, null, List.of(new StringSource(CLASS_NAME, source)));
      task.setProcessors(List.of(spec.processor().get()));
      return task.call();
    }
    catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  /**
   * Builds the classpath the fixture sources are compiled against.
   * <p>
   * Test sources run patched into the {@code org.tentackle.fx.rdc} module, so the dependencies
   * are usually on the module path rather than the class path. The fixtures are compiled as
   * unnamed-module code, hence both paths plus the code sources of representative classes
   * are put on the class path.
   */
  private static String classpath() {
    Set<String> elements = new LinkedHashSet<>();
    for (Class<?> clazz : List.of(DefaultGuiProvider.class, FxComponent.class, PersistentDomainObject.class,
                                  javafx.scene.control.MenuItem.class, javafx.beans.Observable.class)) {
      elements.add(classpathEntry(clazz));
    }
    for (String property : List.of("java.class.path", "jdk.module.path")) {
      String path = System.getProperty(property);
      if (path != null && !path.isBlank()) {
        elements.addAll(List.of(path.split(File.pathSeparator)));
      }
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
      File dir = Files.createTempDirectory("fx-rdc-processor-test").toFile();
      dir.deleteOnExit();
      return dir;
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
