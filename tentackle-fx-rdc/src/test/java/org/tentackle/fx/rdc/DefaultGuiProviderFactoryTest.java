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

package org.tentackle.fx.rdc;

import org.testng.annotations.Test;

import org.tentackle.fx.rdc.PdoStubs.Alpha;
import org.tentackle.fx.rdc.security.SecurityGuiProvider;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.security.pdo.Security;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Unit tests for the provider lookup of {@link DefaultGuiProviderFactory}.
 * <p>
 * Uses the {@code @GuiProviderService} mapping this module ships for {@link Security}
 * ({@link SecurityGuiProvider}), its unmapped sub type {@link SpecialSecurity} and the unmapped
 * test PDO {@link Alpha}.
 * No provider is instantiated, hence no JavaFX toolkit and no session are needed.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultGuiProviderFactoryTest {

  /**
   * A PDO type without a provider of its own, served by the provider of its super type.
   */
  public interface SpecialSecurity extends Security {}

  /**
   * Counts the constructor lookups to verify the caching.
   */
  private static class CountingFactory extends DefaultGuiProviderFactory {

    private final List<Class<?>> lookups = new ArrayList<>();

    @Override
    @SuppressWarnings("rawtypes")
    protected <T extends PersistentDomainObject<T>> Optional<Constructor> getConstructor(Class<T> pdoClass) {
      lookups.add(pdoClass);
      return super.getConstructor(pdoClass);
    }
  }


  @Test
  public void testUnmappedPdoHasNoProvider() {
    CountingFactory factory = new CountingFactory();
    assertFalse(factory.isGuiProviderAvailable(Alpha.class));
    assertFalse(factory.isGuiProviderAvailable(Alpha.class));
    assertEquals(factory.lookups, List.of(Alpha.class), "the lookup result must be cached");
  }

  @Test
  public void testMappedPdoHasProvider() {
    CountingFactory factory = new CountingFactory();
    assertTrue(factory.isGuiProviderAvailable(Security.class));
    assertTrue(factory.isGuiProviderAvailable(Security.class));
    assertEquals(factory.lookups, List.of(Security.class), "the lookup result must be cached");
  }

  @Test
  @SuppressWarnings("rawtypes")
  public void testConstructorOfMappedProvider() throws NoSuchMethodException {
    Optional<Constructor> constructor = new DefaultGuiProviderFactory().getConstructor(Security.class);
    assertTrue(constructor.isPresent());
    assertSame(constructor.get().getDeclaringClass(), SecurityGuiProvider.class);
    assertEquals(constructor.get(), SecurityGuiProvider.class.getConstructor(Security.class));
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testProviderOfSuperType() throws NoSuchMethodException {
    Class pdoClass = SpecialSecurity.class;
    DefaultGuiProviderFactory factory = new DefaultGuiProviderFactory();
    assertTrue(factory.isGuiProviderAvailable(pdoClass), "provider of the super type must be found");
    Optional<Constructor> constructor = factory.getConstructor(pdoClass);
    assertTrue(constructor.isPresent());
    assertEquals(constructor.get(), SecurityGuiProvider.class.getConstructor(Security.class),
                 "the constructor taking the super type must accept the PDO");
  }

  @Test
  public void testCreateWithoutMappingFails() {
    Alpha alpha = PdoStubs.create(Alpha.class);
    RdcRuntimeException ex = expectThrows(RdcRuntimeException.class,
                                          () -> new DefaultGuiProviderFactory().createGuiProvider(alpha));
    assertTrue(ex.getMessage().contains("no @GuiProviderService"), ex.getMessage());
  }

}
