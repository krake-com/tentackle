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
 * The PDO-aware persistence layer.<br>
 * Implements the persistence side of the PDOs and operations defined in {@code org.tentackle.pdo}
 * on top of {@code org.tentackle.database}, both locally via JDBC and remotely via TRIP.
 * Furthermore, provides the token lock manager and the base classes for application servers.
 */
module org.tentackle.persistence {

  exports org.tentackle.persist;
  exports org.tentackle.persist.app;
  exports org.tentackle.persist.lock;
  exports org.tentackle.persist.ns;
  exports org.tentackle.persist.ns.trip;
  exports org.tentackle.persist.trip;
  exports org.tentackle.persist.security;
  exports org.tentackle.persist.security.trip;

  // necessary for TRIP
  opens org.tentackle.persist to org.tentackle.core;
  opens org.tentackle.persist.lock to org.tentackle.core;
  opens org.tentackle.persist.ns to org.tentackle.core;
  opens org.tentackle.persist.security to org.tentackle.core;
  opens org.tentackle.persist.trip to org.tentackle.core;

  requires transitive org.tentackle.pdo;
  requires transitive org.tentackle.database;

  requires java.naming;

  provides org.tentackle.common.ModuleHook with org.tentackle.persist.service.Hook;

}
