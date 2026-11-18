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

package org.tentackle.common;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Holds the optional application-specific {@link Cryptor} singleton.<br>
 * Loaded lazily on first access of {@link Cryptor#getInstance()}.
 */
final class CryptorHolder {

  /**
   * The result of loading the application-specific cryptor.
   *
   * @param cryptor the cryptor, null if none configured or loading failed
   * @param failure the failure, null if loaded or none configured
   */
  record Loaded(Cryptor cryptor, TentackleRuntimeException failure) {

    Cryptor get() {
      if (failure != null) {
        // fail closed: never fall back to storing secrets unencrypted
        throw new TentackleRuntimeException("application-specific Cryptor is configured but could not be created", failure);
      }
      return cryptor;
    }
  }

  static final Loaded INSTANCE = load(
      () -> !ServiceFactory.getServiceFinder().findServiceConfigurations(Cryptor.class.getName()).isEmpty(),
      () -> ServiceFactory.createService(Cryptor.class));


  /**
   * Loads the cryptor.
   *
   * @param configured tells whether a cryptor is configured
   * @param factory creates the cryptor
   * @return the result
   */
  static Loaded load(BooleanSupplier configured, Supplier<Cryptor> factory) {
    if (!configured.getAsBoolean()) {
      // No application-specific cryptor configured! That's ok, although not recommended.
      return new Loaded(null, null);
    }
    try {
      Cryptor cryptor = factory.get();
      if (cryptor == null) {
        throw new TentackleRuntimeException("cryptor factory returned null");
      }
      return new Loaded(cryptor, null);
    }
    catch (TentackleRuntimeException rx) {
      return new Loaded(null, rx);
    }
    catch (RuntimeException rx) {
      return new Loaded(null, new TentackleRuntimeException(rx));
    }
  }

  private CryptorHolder() {}
}
