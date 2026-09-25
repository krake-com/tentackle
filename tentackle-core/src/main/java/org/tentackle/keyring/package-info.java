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

/**
 * Secrets kept on the local machine, such as the tokens of a single sign-on.
 * <p>
 * The entry point is {@link org.tentackle.keyring.Keyring#getInstance()}. Tentackle core keeps the secrets in
 * the Java preferences or in memory. The module {@code tentackle-keyring} adds the credential store of the
 * operating system.
 */
package org.tentackle.keyring;
