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
 * AtlantaFX themes and AtlantaFX-based components and containers for Tentackle FX clients.
 */
open module org.tentackle.fx.atlanta {

  exports org.tentackle.fx.atlanta;
  exports org.tentackle.fx.atlanta.component;
  exports org.tentackle.fx.atlanta.component.build;
  exports org.tentackle.fx.atlanta.component.config;
  exports org.tentackle.fx.atlanta.component.delegate;
  exports org.tentackle.fx.atlanta.container;
  exports org.tentackle.fx.atlanta.container.build;
  exports org.tentackle.fx.atlanta.container.config;
  exports org.tentackle.fx.atlanta.container.delegate;
  exports org.tentackle.fx.atlanta.translate;

  requires transitive org.tentackle.fx.rdc;
  requires transitive atlantafx.base;

  provides org.tentackle.common.ModuleHook with org.tentackle.fx.atlanta.service.Hook;

}
