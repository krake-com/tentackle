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
import org.apache.maven.project.MavenProject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.maven.plugin.MojoTestSupport.RecordingLog;
import static org.tentackle.maven.plugin.MojoTestSupport.callValidate;
import static org.tentackle.maven.plugin.MojoTestSupport.createProject;
import static org.tentackle.maven.plugin.MojoTestSupport.createTempDir;
import static org.tentackle.maven.plugin.MojoTestSupport.deleteRecursively;
import static org.tentackle.maven.plugin.MojoTestSupport.setField;

/**
 * Tests for the {@link PropertiesMojo}.
 * <p>
 * The mojo derives maven properties from other maven properties by running them through a
 * converter, which is a {@code Function<String,String>} loaded either by its FQCN or - with a
 * leading {@code @} - via {@code META-INF/services}. Since the converter is looked up in the
 * plugin's classloader, the converters below are ordinary classes of this test module and are
 * referenced by their binary names.
 *
 * @see MojoTestSupport
 */
@SuppressWarnings("missing-explicit-ctor")
public class PropertiesMojoTest {

  /** The service name backed by {@code src/test/resources/META-INF/services}. */
  private static final String CONVERTER_SERVICE = "org.tentackle.maven.plugin.TestConverter";

  private static final String UPPERCASE_CONVERTER = UpperCaseConverter.class.getName();

  private File basedir;
  private PropertiesMojo mojo;
  private MavenProject project;
  private RecordingLog log;


  @BeforeMethod
  public void setUp() {
    basedir = createTempDir("properties-mojo");
    project = createProject(basedir);
    log = new RecordingLog();
    mojo = new PropertiesMojo();
    mojo.setLogger(log);
    setField(mojo, "mavenProject", project);
    UpperCaseConverter.instanceCount = 0;
  }

  @AfterMethod
  public void tearDown() {
    deleteRecursively(basedir);
  }


  /**
   * The converted value ends up as a property of the current project.
   */
  @Test
  public void createsThePropertyFromTheConvertedInput() throws Exception {
    execute(descriptor("secret", UPPERCASE_CONVERTER, "encryptedPasswd"));

    assertEquals(project.getProperties().getProperty("encryptedPasswd"), "SECRET");
  }

  /**
   * Several descriptors are applied in order, each one creating its own property.
   */
  @Test
  public void createsAllProperties() throws Exception {
    execute(descriptor("alpha", UPPERCASE_CONVERTER, "first"),
            descriptor("beta", UPPERCASE_CONVERTER, "second"));

    assertEquals(project.getProperties().getProperty("first"), "ALPHA");
    assertEquals(project.getProperties().getProperty("second"), "BETA");
  }

  /**
   * Converters are cached, which is why they must be stateless.
   */
  @Test
  public void instantiatesEachConverterOnlyOnce() throws Exception {
    execute(descriptor("alpha", UPPERCASE_CONVERTER, "first"),
            descriptor("beta", UPPERCASE_CONVERTER, "second"));

    assertEquals(UpperCaseConverter.instanceCount, 1);
  }

  /**
   * Nothing configured, nothing to do. The mojo is bound to a lifecycle phase, so this is
   * the common case.
   */
  @Test
  public void doesNothingWithoutDescriptors() throws Exception {
    mojo.executeImpl();

    assertTrue(project.getProperties().isEmpty());
  }

  /**
   * A null input is passed through to the converter, which is free to interpret it.
   */
  @Test
  public void passesANullInputToTheConverter() throws Exception {
    execute(descriptor(null, UPPERCASE_CONVERTER, "empty"));

    assertEquals(project.getProperties().getProperty("empty"), "");
  }

  /**
   * A converter can be resolved via {@code META-INF/services} to keep the FQCN out of the pom.
   */
  @Test
  public void resolvesTheConverterViaTheServiceRegistry() throws Exception {
    execute(descriptor("secret", "@" + CONVERTER_SERVICE, "encryptedPasswd"));

    assertEquals(project.getProperties().getProperty("encryptedPasswd"), "SECRET");
  }

  /**
   * The property is logged with its value if the verbosity says so.
   */
  @Test
  public void logsTheCreatedProperty() throws Exception {
    setField(mojo, "verbosity", "info");

    execute(descriptor("secret", UPPERCASE_CONVERTER, "encryptedPasswd"));

    assertTrue(RecordingLog.contains(log.getInfoMessages(), "created property: encryptedPasswd = SECRET"),
               log.getInfoMessages().toString());
  }

  /**
   * With the default verbosity the value is not logged - it may well be a password.
   */
  @Test
  public void doesNotLogTheCreatedPropertyByDefault() throws Exception {
    execute(descriptor("secret", UPPERCASE_CONVERTER, "encryptedPasswd"));

    assertTrue(log.getInfoMessages().isEmpty(), log.getInfoMessages().toString());
  }

  @Test
  public void failsWithoutAPropertyName() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", UPPERCASE_CONVERTER, null)));

    assertEquals(ex.getMessage(), "missing property name in property descriptor");
  }

  @Test
  public void failsWithoutAConverter() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", null, "encryptedPasswd")));

    assertEquals(ex.getMessage(), "missing converter in property descriptor");
  }

  @Test
  public void failsOnAnUnknownConverterClass() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", "com.example.NoSuchConverter", "x")));

    assertEquals(ex.getMessage(), "no such converter: com.example.NoSuchConverter");
  }

  @Test
  public void failsOnAnUnknownConverterService() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", "@com.example.NoSuchService", "x")));

    assertEquals(ex.getMessage(), "cannot load converter @com.example.NoSuchService");
  }

  @Test
  public void failsIfTheConverterIsNoFunction() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", NoFunction.class.getName(), "x")));

    assertTrue(ex.getMessage().endsWith(" is not a java.util.Function"), ex.getMessage());
  }

  /**
   * A {@code Function} of some other type has no {@code apply(String)} after erasure.
   */
  @Test
  public void failsIfTheConverterDoesNotAcceptAString() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", IntegerFunction.class.getName(), "x")));

    assertEquals(ex.getMessage(),
                 IntegerFunction.class.getName() + " does not provide a method apply(String)");
  }

  @Test
  public void failsIfTheConverterDoesNotReturnAString() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", LengthFunction.class.getName(), "x")));

    assertEquals(ex.getMessage(),
                 "method apply(String) of " + LengthFunction.class.getName() + " does not return a String");
  }

  @Test
  public void failsIfTheConverterCannotBeInstantiated() {
    MojoExecutionException ex = expectThrows(MojoExecutionException.class,
                                             () -> execute(descriptor("secret", AbstractConverter.class.getName(), "x")));

    assertEquals(ex.getMessage(), AbstractConverter.class.getName() + " cannot be instantiated");
  }

  /**
   * The descriptor is a plain bean configured by maven.
   */
  @Test
  public void descriptorIsABean() {
    PropertyDescriptor descriptor = new PropertyDescriptor();

    assertNull(descriptor.getInput());
    assertNull(descriptor.getConverter());
    assertNull(descriptor.getProperty());

    descriptor.setInput("in");
    descriptor.setConverter("conv");
    descriptor.setProperty("prop");

    assertEquals(descriptor.getInput(), "in");
    assertEquals(descriptor.getConverter(), "conv");
    assertEquals(descriptor.getProperty(), "prop");
  }


  private void execute(PropertyDescriptor... descriptors) throws Exception {
    setField(mojo, "propertyDescriptors", List.of(descriptors));
    assertTrue(callValidate(mojo));
    mojo.executeImpl();
  }

  private PropertyDescriptor descriptor(String input, String converter, String property) {
    PropertyDescriptor descriptor = new PropertyDescriptor();
    descriptor.setInput(input);
    descriptor.setConverter(converter);
    descriptor.setProperty(property);
    return descriptor;
  }


  /** A converter mapping its input to upper case. */
  public static class UpperCaseConverter implements Function<String, String> {

    private static int instanceCount;

    /** Creates the converter. */
    public UpperCaseConverter() {
      instanceCount++;
    }

    @Override
    public String apply(String input) {
      return input == null ? "" : input.toUpperCase(Locale.ROOT);
    }
  }

  /** Not a converter at all. */
  public static class NoFunction {

    /** Creates the non-converter. */
    public NoFunction() {
    }

    /**
     * Looks like a converter, but isn't one.
     *
     * @param input the input
     * @return the input
     */
    public String apply(String input) {
      return input;
    }
  }

  /** A function that does not accept a string. */
  public static class IntegerFunction implements Function<Integer, Integer> {

    /** Creates the function. */
    public IntegerFunction() {
    }

    @Override
    public Integer apply(Integer input) {
      return input;
    }
  }

  /** A function accepting a string but returning something else. */
  public static class LengthFunction implements Function<String, Integer> {

    /** Creates the function. */
    public LengthFunction() {
    }

    @Override
    public Integer apply(String input) {
      return input.length();
    }
  }

  /** A converter that cannot be instantiated. */
  public abstract static class AbstractConverter implements Function<String, String> {

    /** Creates the converter. */
    public AbstractConverter() {
    }

    @Override
    public abstract String apply(String input);
  }

}
