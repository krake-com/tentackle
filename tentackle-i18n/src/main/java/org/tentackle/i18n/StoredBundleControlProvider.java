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

package org.tentackle.i18n;

import org.tentackle.common.Service;

import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;

/**
 * Bundle control provider to use stored bundles.
 * <p>
 * Only used for non-modular applications! Not supported by jigsaw.
 *
 * @author harald
 */
@Service(ResourceBundleControlProvider.class)
public class StoredBundleControlProvider implements ResourceBundleControlProvider {

  /** the control instance. */
  private static final ResourceBundle.Control CONTROL = new StoredBundleControl();

  /**
   * Creates the resource bundle control provider.
   */
  public StoredBundleControlProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public ResourceBundle.Control getControl(String baseName) {
    return CONTROL;
  }

}
