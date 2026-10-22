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
 * The application self-update service.<br>
 * Provides the TRIP {@code UpdateService} contract with its request and reply records,
 * the server side to publish the service, and the client side to detect an updatable
 * jlink or jpackage image and to download and verify the update.
 * The user interface is provided by {@code org.tentackle.fx.rdc.update}.
 */
module org.tentackle.update {

  exports org.tentackle.update;

  opens org.tentackle.update to org.tentackle.core; // TRIP

  requires transitive org.tentackle.core;

  requires java.net.http;

  provides org.tentackle.common.ModuleHook with org.tentackle.update.service.Hook;

}
