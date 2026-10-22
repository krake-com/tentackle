/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.fx.rdc.app;

import org.tentackle.common.Bundle;
import org.tentackle.common.BundleFactory;

import java.util.ResourceBundle;

/**
 * Bundle for the {@code org.tentackle.fx.rdc.app} package.
 *
 * @author harald
 */
@Bundle
public class AppFxRdcBundle {

  /**
   * Gets the bundle.
   *
   * @return the resource bundle
   */
  public static ResourceBundle getBundle() {
    return BundleFactory.getBundle(AppFxRdcBundle.class.getName());
  }

  /**
   * Gets a string for the given key.
   *
   * @param key the key
   * @return the string from the bundle
   */
  @SuppressWarnings("bundle")
  public static String getString(String key) {
    return getBundle().getString(key);
  }

  private AppFxRdcBundle() {
  }

}
