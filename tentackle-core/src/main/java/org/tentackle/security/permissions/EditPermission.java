/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.security.permissions;

/**
 * Edit permission interface.
 * <p>
 * While WritePermission allows the business logic on behalf of the current user to update the data,
 * it does not mean that the user is allowed to edit the data intentionally via the UI.
 * This requires EditPermission.
 *
 * @author harald
 */
public interface EditPermission extends WritePermission, ViewPermission {

  /** the permission's name. */
  String NAME = "EDIT";

}
