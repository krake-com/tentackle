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

import java.util.spi.ResourceBundleProvider;

/**
 * Interface to determine the module hierarchy.<br>
 * Provides a hook to access resources from within a module.
 * <p>
 * Each module must include exactly one such implementation and provide it via module-info.
 * <p>
 * Since ModuleHook extends {@link ResourceBundleProvider}, all classes must implement the following
 * method as follows:
 *
 * <pre>
 *    &#64;Override
 *    public ResourceBundle getBundle(String baseName, Locale locale) {
 *      return ResourceBundle.getBundle(baseName, locale);
 *    }
 * </pre>
 *
 * Notice: it must be implemented in the hook and not an abstract super class because
 * it is used by the BundleFactory to access the bundles from within the framework
 * and ResourceBundle uses the callerclass to determine the caller module.
 *
 * @author harald
 */
public interface ModuleHook extends ResourceBundleProvider {

}
