/*
 * Tentackle - http://www.tentackle.org
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

import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;
import org.tentackle.common.Service;

/**
 * Bundle control provider to use stored bundles.
 * <p>
 * Only used for non-modular applications! Not supported by jigsaw.
 *
 * @author harald
 */
@Service(ResourceBundleControlProvider.class)
public class StoredBundleControlProvider implements ResourceBundleControlProvider {

  /**
   * Flag to disable the control provider.<br>
   * By default, the provider is enabled.
   */
  public static boolean enabled = true;

  /** the control instance. */
  private static final ResourceBundle.Control CONTROL = new StoredBundleControl();

  @Override
  public ResourceBundle.Control getControl(String baseName) {
    return enabled ? CONTROL : null;
  }

}
