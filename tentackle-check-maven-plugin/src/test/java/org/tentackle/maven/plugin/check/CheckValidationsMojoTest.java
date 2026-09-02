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

package org.tentackle.maven.plugin.check;

import org.apache.maven.plugin.MojoExecutionException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.plugin.check.CheckTestSupport.RecordingLog;
import org.tentackle.maven.plugin.check.CheckTestSupport.TestValidationsMojo;
import org.tentackle.maven.plugin.check.CheckValidationsProcessor.ValidatorEntry;
import org.tentackle.validate.validator.MandatoryImpl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@code validations} goal end to end.
 * <p>
 * A temporary project is compiled with the real {@code javac}, so the processor sees real
 * validation annotations and instantiates the real validator implementations from
 * {@code tentackle-core}. What matters is that every annotation is found exactly once, that it is
 * attributed to the class a developer has to fix, and that a validator which cannot be evaluated
 * fails the build instead of surfacing at runtime.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class CheckValidationsMojoTest {

  private File basedir;
  private File sourceDir;
  private File classesDir;
  private RecordingLog log;
  private TestValidationsMojo mojo;


  @BeforeMethod
  public void setUp() {
    basedir = CheckTestSupport.createTempDir("check-validations");
    sourceDir = CheckTestSupport.createDir(basedir, "src/main/java");
    classesDir = CheckTestSupport.createDir(basedir, "target/classes");
    log = new RecordingLog();

    mojo = new TestValidationsMojo();
    mojo.setLog(log);
    CheckTestSupport.setField(mojo, "mavenProject", CheckTestSupport.createProject(basedir));
    CheckTestSupport.setField(mojo, "sourceDir", sourceDir);
    CheckTestSupport.setField(mojo, "classpathElements", CheckTestSupport.classpathElements(classesDir));
    CheckTestSupport.setField(mojo, "encoding", "UTF-8");
    CheckTestSupport.setField(mojo, "locales", "en_US");
  }

  @AfterMethod
  public void tearDown() {
    CheckTestSupport.deleteRecursively(basedir);
  }


  /**
   * A validation annotation is turned into its validator implementation.
   */
  @Test
  public void createsTheValidatorOfAnAnnotation() throws Exception {
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;

        public class Entity {
          @Mandatory
          private String name;

          public String getName() {
            return name;
          }
        }
        """);

    run();

    assertEquals(validators().size(), 1);
    assertEquals(validators().getFirst().validator().getClass(), MandatoryImpl.class);
    assertEquals(validators().getFirst().className(), "com.example.app.Entity");
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "1 annotation, 0 scripts checked for en_US, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * Every annotated element is checked, on fields as well as on methods.
   */
  @Test
  public void findsEveryAnnotatedElement() throws Exception {
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;
        import org.tentackle.validate.validator.NotNull;

        public class Entity {
          @Mandatory
          private String name;

          @NotNull
          public String getName() {
            return name;
          }
        }
        """);

    run();

    assertEquals(validators().size(), 2);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "2 annotations, 0 scripts checked for en_US, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A repeated annotation is checked once per occurrence.
   * <p>
   * Repeating an annotation replaces it with its container ({@code @MandatoryContainer}), which
   * carries {@code @RepeatableValidation} rather than {@code @Validation}, so the round offers the
   * container only. The processor has to look through it to the annotation it holds.
   */
  @Test
  public void findsRepeatedAnnotations() throws Exception {
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.scope.AllScope;
        import org.tentackle.validate.validator.Mandatory;

        public class Entity {
          @Mandatory
          @Mandatory(scope = AllScope.class)
          private String name;
        }
        """);

    run();

    assertEquals(validators().size(), 2);
    assertEquals(classNames(), List.of("com.example.app.Entity", "com.example.app.Entity"));
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "2 annotations, 0 scripts checked for en_US, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A repeated and a single occurrence of the same annotation in one round are each checked once.
   * <p>
   * The round then offers both the annotation and its container, which must not make the repeated
   * element be processed twice.
   */
  @Test
  public void countsRepeatedAndSingleAnnotationsOfTheSameTypeOnce() throws Exception {
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.scope.AllScope;
        import org.tentackle.validate.validator.Mandatory;

        public class Entity {
          @Mandatory
          @Mandatory(scope = AllScope.class)
          private String name;

          @Mandatory
          private String description;
        }
        """);

    run();

    assertEquals(validators().size(), 3);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "3 annotations, 0 scripts checked for en_US, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * The validator is attributed to the enclosing class, which is the source a developer has to
   * open - not to the field or method itself.
   * <p>
   * The name is the binary one, because the mojo loads the class to evaluate the message scripts
   * against it, and {@link ClassLoader#loadClass} does not accept the canonical name of a nested
   * class.
   */
  @Test
  public void attributesTheValidatorToItsEnclosingClass() throws Exception {
    source("Outer.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;

        public class Outer {
          public static class Inner {
            @Mandatory
            private String name;
          }
        }
        """);

    run();

    assertEquals(classNames(), List.of("com.example.app.Outer$Inner"));
    assertTrue(log.getErrorMessages().isEmpty(), log.getErrorMessages().toString());
  }

  /**
   * Records and enums are enclosing classes too.
   */
  @Test
  public void attributesTheValidatorToAnEnclosingRecord() throws Exception {
    source("Point.java", """
        package com.example.app;

        import org.tentackle.validate.validator.NotNull;

        public record Point(int x, int y) {
          @NotNull
          public String label() {
            return x + "/" + y;
          }
        }
        """);

    run();

    assertEquals(classNames(), List.of("com.example.app.Point"));
    assertTrue(log.getErrorMessages().isEmpty(), log.getErrorMessages().toString());
  }

  /**
   * Generics are not part of the name, or it could not be loaded as a class later on.
   */
  @Test
  public void cutsTheGenericsOffTheEnclosingClass() throws Exception {
    source("Box.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;

        public class Box<T extends Number> {
          @Mandatory
          private T value;
        }
        """);

    run();

    assertEquals(classNames(), List.of("com.example.app.Box"));
  }

  /**
   * Interfaces carry validations too - that is where the tentackle PDO interfaces declare them.
   */
  @Test
  public void findsValidationsOnInterfaceMethods() throws Exception {
    source("Named.java", """
        package com.example.app;

        import org.tentackle.validate.validator.NotNull;

        public interface Named {
          @NotNull
          String getName();
        }
        """);

    run();

    assertEquals(classNames(), List.of("com.example.app.Named"));
  }

  /**
   * A project without a single validation passes.
   */
  @Test
  public void aProjectWithoutValidationsPasses() throws Exception {
    source("Plain.java", """
        package com.example.app;

        public class Plain {
          private String name;
        }
        """);

    run();

    assertEquals(validators().size(), 0);
    assertTrue(RecordingLog.contains(log.getInfoMessages(), "0 annotations, 0 scripts checked for en_US, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * A script that cannot be compiled fails the build and names the validator.
   * <p>
   * The plugin brings no scripting language of its own, so a project using script conditions has
   * to add the provider (such as {@code tentackle-script-groovy}) as a plugin dependency. Without
   * it every script is unusable, which is exactly what is reproduced here.
   */
  @Test
  public void anUnusableScriptFailsTheBuild() {
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;

        public class Entity {
          @Mandatory(condition = "{name != null}")
          private String name;
        }
        """);

    MojoExecutionException ex = expectThrows(MojoExecutionException.class, this::run);

    assertEquals(ex.getMessage(), "1 annotation, 0 scripts checked for en_US, 1 errors");
    assertTrue(RecordingLog.contains(log.getErrorMessages(),
                                     "validation condition check failed for validator " +
                                     MandatoryImpl.class.getName() + " in com.example.app.Entity"),
               log.getErrorMessages().toString());
  }

  /**
   * Every validator is checked once per configured locale, since the messages are translated.
   */
  @Test
  public void checksEveryLocale() throws Exception {
    CheckTestSupport.setField(mojo, "locales", "en_US, de_DE");
    source("Entity.java", """
        package com.example.app;

        import org.tentackle.validate.validator.Mandatory;

        public class Entity {
          @Mandatory
          private String name;
        }
        """);

    run();

    assertTrue(RecordingLog.contains(log.getInfoMessages(),
                                     "1 annotation, 0 scripts checked for en_US, de_DE, no errors"),
               log.getInfoMessages().toString());
  }

  /**
   * The validator entry describes itself by implementation and location, which is what the error
   * messages are built from.
   */
  @Test
  public void theValidatorEntryDescribesItself() {
    ValidatorEntry entry = new ValidatorEntry(new MandatoryImpl(), "com.example.app.Entity");

    assertEquals(entry.toString(), MandatoryImpl.class.getName() + " in com.example.app.Entity");
  }


  // ---------------- fixture ----------------

  private void source(String name, String contents) {
    CheckTestSupport.writeFile(sourceDir, "com/example/app/" + name, contents);
  }

  /**
   * Runs the goal the way {@code execute()} would, minus the global logging handler.
   *
   * @throws Exception if the goal failed
   */
  private void run() throws Exception {
    // the goal runs in process-classes: the annotated classes must already be compiled
    CheckTestSupport.compile(sourceDir, classesDir,
                             CheckTestSupport.getField(mojo, "classpathElements"));
    mojo.prepareExecute();
    assertTrue(CheckTestSupport.callValidate(mojo));
    mojo.executeImpl();
    mojo.finishExecute();
  }

  private List<ValidatorEntry> validators() {
    CheckValidationsProcessor processor = CheckTestSupport.getField(mojo, "processor");
    return processor.getValidators();
  }

  private List<String> classNames() {
    List<String> names = new ArrayList<>();
    validators().forEach(entry -> names.add(entry.className()));
    return names;
  }

}
