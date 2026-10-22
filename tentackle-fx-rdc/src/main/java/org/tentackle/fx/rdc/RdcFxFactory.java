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

import org.tentackle.common.Service;
import org.tentackle.fx.DefaultFxFactory;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.rdc.table.RdcTableConfiguration;
import org.tentackle.fx.table.TableConfiguration;

/**
 * PDO-aware {@link FxFactory}.<br>
 * Creates {@link RdcTableConfiguration}s, which use the PDO cells for PDO-valued columns.
 */
@Service(FxFactory.class)
public class RdcFxFactory extends DefaultFxFactory {

  /**
   * Creates the RDC FX factory.
   */
  public RdcFxFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public <S> TableConfiguration<S> createTableConfiguration(S template, String name) {
    return new RdcTableConfiguration<>(template, name);
  }

  @Override
  public <S> TableConfiguration<S> createTableConfiguration(Class<S> objectClass, String name) {
    return new RdcTableConfiguration<>(objectClass, name);
  }

}
