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
 * tentackle-quic module.
 */
module org.tentackle.quic {

  exports org.tentackle.quic;

  opens org.tentackle.quic to org.tentackle.core;

  requires transitive tech.kwik.core;
  requires tech.kwik.agent15;
  requires tech.kwik.agent15.pqc;
  requires transitive org.tentackle.core;

  provides org.tentackle.common.ModuleHook with org.tentackle.quic.service.Hook;

}
