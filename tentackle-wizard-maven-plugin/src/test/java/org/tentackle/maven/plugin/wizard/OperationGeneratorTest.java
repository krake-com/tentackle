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

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.tentackle.maven.PackageInfo;
import org.tentackle.model.ModelManager;
import org.tentackle.validate.ValidationResult;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the operation generator with the bundled templates.
 */
public class OperationGeneratorTest {

  private Path outputDir;
  private OperationGenerator gen;


  @BeforeMethod
  public void setUp() throws Exception {
    outputDir = Files.createTempDirectory("operation-wizard");
    OperationProfile profile = new OperationProfile();
    setField(Profile.class, profile, "name", "operation");
    setPackage(profile, OperationProfile.class, "operationPackage", "operationPackageInfo", "com.example.pdo");
    setPackage(profile, Profile.class, "domainPackage", "domainPackageInfo", "com.example.domain");
    setPackage(profile, Profile.class, "persistencePackage", "persistencePackageInfo", "com.example.persist");
    setPackage(profile, Profile.class, "domainImplPackage", "domainImplPackageInfo", "com.example.domain.impl");
    setPackage(profile, Profile.class, "persistenceImplPackage", "persistenceImplPackageInfo", "com.example.persist.impl");

    URL templateUrl = getClass().getResource("/templates/" + Constants.CATEGORY_OPERATION);
    assertNotNull(templateUrl, "operation templates not found");

    gen = new OperationGenerator(ModelManager.getInstance().getModel("operation-generator-test"));
    gen.setProfile(profile);
    gen.setTemplateDirectory(new File(templateUrl.toURI()));
    gen.setOperationName("SendInvoice");
    gen.setShortDescription("sends an invoice");
  }

  @AfterMethod
  public void tearDown() throws Exception {
    try (Stream<Path> paths = Files.walk(outputDir)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }


  /**
   * An abstract operation with a domain part only.
   */
  @Test
  public void testAbstractDomainOnly() throws Exception {
    gen.setAbstractOperation(true);
    OperationMojo.applyNames(gen, null, "-", null, null);
    List<ValidationResult> results = gen.generate();
    assertTrue(results.isEmpty(), results.toString());

    String source = Files.readString(outputDir.resolve("com.example.pdo/SendInvoice.java"), StandardCharsets.UTF_8);
    assertTrue(source.contains("extends Operation<T>, SendInvoiceDomain<T> {"), source);
    assertTrue(Files.exists(outputDir.resolve("com.example.domain/SendInvoiceDomain.java")));
    assertFalse(Files.exists(outputDir.resolve("com.example.persist/SendInvoicePersistence.java")));
  }

  /**
   * Dropping an interface drops its implementation, even if an implementation name is given.
   */
  @Test
  public void testDroppedInterfaceIgnoresImplementation() {
    OperationMojo.applyNames(gen, "-", null, "MyDomainImpl", "MyPersistenceImpl");
    assertNull(gen.getDomainInterface());
    assertNull(gen.getDomainImplementation());
    assertEquals(gen.getPersistenceImplementation(), "MyPersistenceImpl");
    for (File file : gen.getTargetFiles()) {
      assertFalse(file.getName().startsWith("MyDomainImpl"), file.getName());
    }
  }

  /**
   * A renamed interface keeps an explicitly dropped implementation dropped.
   */
  @Test
  public void testRenamedInterfaceWithoutImplementation() {
    OperationMojo.applyNames(gen, "MyDomain", null, "-", null);
    assertEquals(gen.getDomainInterface(), "MyDomain");
    assertNull(gen.getDomainImplementation());
  }


  /**
   * Fully qualified super types are imported, unless they live in the package of the generated type.
   */
  @Test
  public void testFullyQualifiedSuperTypes() throws Exception {
    OperationProfile profile = gen.getProfile();
    setField(OperationProfile.class, profile, "operationInterface", "com.example.base.BaseOperation");
    setField(Profile.class, profile, "domainImplementation", "com.example.domain.impl.AbstractMyDomainOperation");
    OperationMojo.applyNames(gen, null, null, null, null);
    List<ValidationResult> results = gen.generate();
    assertTrue(results.isEmpty(), results.toString());

    String source = Files.readString(outputDir.resolve("com.example.pdo/SendInvoice.java"), StandardCharsets.UTF_8);
    assertTrue(source.contains("import com.example.base.BaseOperation;"), source);
    assertTrue(source.contains("extends BaseOperation<SendInvoice>"), source);

    source = Files.readString(outputDir.resolve("com.example.domain.impl/SendInvoiceDomainImpl.java"), StandardCharsets.UTF_8);
    assertTrue(source.contains("extends AbstractMyDomainOperation<SendInvoice, SendInvoiceDomainImpl>"), source);
    assertFalse(source.contains("import com.example.domain.impl.AbstractMyDomainOperation;"), source);   // same package
  }


  private void setPackage(Profile profile, Class<?> clazz, String packageField, String infoField, String packageName)
          throws ReflectiveOperationException, IOException {
    setField(clazz, profile, packageField, packageName);
    // the package directories exist in a real project
    Path packageDir = Files.createDirectories(outputDir.resolve(packageName));
    setField(clazz, profile, infoField, new PackageInfo(packageName, null, packageDir.toFile()));
  }

  private void setField(Class<?> clazz, Object object, String name, Object value) throws ReflectiveOperationException {
    Field field = clazz.getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }

}
