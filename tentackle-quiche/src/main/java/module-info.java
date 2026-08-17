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
 * tentackle-quiche module.
 */
module org.tentackle.quiche {

  exports org.tentackle.quiche;

  opens org.tentackle.quiche to org.tentackle.core;

  requires transitive org.tentackle.core;

  // resources only: carries the libquiche binaries bound via the FFM API
  requires org.mortbay.jetty.quiche;

  provides org.tentackle.common.ModuleHook with org.tentackle.quiche.service.Hook;

}
