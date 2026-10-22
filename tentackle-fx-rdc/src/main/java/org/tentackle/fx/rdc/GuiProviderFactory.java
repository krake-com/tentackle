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

import org.tentackle.common.ServiceFactory;
import org.tentackle.pdo.PersistentDomainObject;


interface GuiProviderFactoryHolder {
  GuiProviderFactory INSTANCE = ServiceFactory.createService(GuiProviderFactory.class, DefaultGuiProviderFactory.class);
}


/**
 * A factory for {@link GuiProvider}s.
 *
 * @author harald
 */
public interface GuiProviderFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static GuiProviderFactory getInstance() {
    return GuiProviderFactoryHolder.INSTANCE;
  }

  /**
   * Creates the GUI provider for a PDO.
   * <p>
   * Throws a {@link RdcRuntimeException} if the provider could not be created,
   * or no {@link GuiProviderService} was found at all.
   *
   * @param <T> the PDO type
   * @param pdo the PDO
   * @return the GUI provider, never null
   */
  <T extends PersistentDomainObject<T>> GuiProvider<T> createGuiProvider(T pdo);

  /**
   * Returns whether a {@link GuiProviderService} is available for the given PDO class.
   * <p>
   * Throws a {@link RdcRuntimeException} if the provider class has no public constructor
   * with the PDO as its only argument.
   *
   * @param pdoClass the PDO class
   * @param <T> the PDO type
   * @return true if a provider exists, false if no {@link GuiProviderService} was found
   */
  <T extends PersistentDomainObject<T>> boolean isGuiProviderAvailable(Class<T> pdoClass);

}
