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

module org.tentackle.persistence {
  exports org.tentackle.persist;
  exports org.tentackle.persist.app;
  exports org.tentackle.persist.ns;
  exports org.tentackle.persist.ns.rmi;
  exports org.tentackle.persist.rmi;
  exports org.tentackle.persist.security;
  exports org.tentackle.persist.security.rmi;

  requires org.tentackle.pdo;
  requires org.tentackle.session;
  requires org.tentackle.core;
  requires org.tentackle.database;
  requires org.tentackle.sql;
  requires org.tentackle.common;

  requires java.rmi;
  requires java.sql;
  requires java.naming;

  provides org.tentackle.common.ModuleHook with org.tentackle.persist.service.Hook;
}
