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



package org.tentackle.validate.scope;

import org.tentackle.validate.ValidationScope;

/**
 * Validation scope that applies to all scopes.<br>
 * Validators configured with this scope are applied in any effective scope, and an effective
 * scope of this kind applies all validators.
 *
 * @author harald
 */
public interface AllScope extends ValidationScope {

  /** the scope's name. */
  String NAME = "ALL";

}
