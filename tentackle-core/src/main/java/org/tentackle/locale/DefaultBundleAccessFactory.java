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

import org.tentackle.common.Service;

/**
 * Default implementation of bundle access factory.<br>
 * Uses the standard JDK resource bundles based on property files.
 *
 * @author harald
 */
@Service(BundleAccessFactory.class)
public class DefaultBundleAccessFactory implements BundleAccessFactory {

  @Override
  public BundleAccess createBundleAccess(String baseName) throws BundleException {
    // @todo
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
