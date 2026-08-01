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
 * tentackle-database module.
 */
module org.tentackle.database {

  exports org.tentackle.dbms;
  exports org.tentackle.dbms.dsl;
  exports org.tentackle.dbms.prefs;
  exports org.tentackle.dbms.prefs.trip;
  exports org.tentackle.dbms.trip;

  opens org.tentackle.dbms to org.tentackle.core;           // necessary for TRIP
  opens org.tentackle.dbms.prefs to org.tentackle.core;     // necessary for TRIP

  requires transitive org.tentackle.session;
  requires transitive org.tentackle.sql;

  provides org.tentackle.common.ModuleHook with org.tentackle.dbms.service.Hook;

}
