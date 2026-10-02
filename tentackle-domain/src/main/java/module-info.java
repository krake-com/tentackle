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
 * The domain-logic layer base.<br>
 * Provides the base classes for the domain side of the PDOs and operations defined in {@code org.tentackle.pdo},
 * the default {@code DomainDelegateLinker}, and the domain implementations of the framework's own
 * number source and security PDOs.
 * Depends on {@code org.tentackle.pdo} only, i.e. the domain layer has no access to the database.
 */
module org.tentackle.domain {

  exports org.tentackle.domain;
  exports org.tentackle.domain.ns;
  exports org.tentackle.domain.security;

  // necessary for TRIP
  opens org.tentackle.domain to org.tentackle.core;
  opens org.tentackle.domain.ns to org.tentackle.core;
  opens org.tentackle.domain.security to org.tentackle.core;

  requires transitive org.tentackle.pdo;

  provides org.tentackle.common.ModuleHook with org.tentackle.domain.service.Hook;

}
