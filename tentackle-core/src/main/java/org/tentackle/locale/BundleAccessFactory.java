/**
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


package org.tentackle.locale;

import org.tentackle.common.ServiceFactory;


interface BundleAccessFactory$Singleton {
  BundleAccessFactory INSTANCE = ServiceFactory.createService(
              BundleAccessFactory.class, DefaultBundleAccessFactory.class);
}


/**
 * The bundle factory.
 * 
 *
 * @author harald
 */
public interface BundleAccessFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static BundleAccessFactory getInstance() {
    return BundleAccessFactory$Singleton.INSTANCE;
  }


  /**
   * Creates a bundle access.
   *
   * @param baseName the basename (usually the package name)
   * @return the bundle access
   * @throws BundleException if creating the bundle access failed
   */
  BundleAccess createBundleAccess(String baseName) throws BundleException;

}
