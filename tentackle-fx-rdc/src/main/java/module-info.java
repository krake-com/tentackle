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
 * The rich desktop client.<br>
 * Bridges the PDO layer and the extended JavaFX layer: per-entity GUI providers, generic CRUD and search
 * dialogs, PDO trees, tables and context menus, PDO-aware components and value translators,
 * background execution, the security rules editor, and the desktop application lifecycle with login.
 */
module org.tentackle.fx.rdc {

  exports org.tentackle.fx.rdc;
  exports org.tentackle.fx.rdc.admin;
  exports org.tentackle.fx.rdc.app;
  exports org.tentackle.fx.rdc.apt;
  exports org.tentackle.fx.rdc.component;
  exports org.tentackle.fx.rdc.component.build;
  exports org.tentackle.fx.rdc.component.delegate;
  exports org.tentackle.fx.rdc.contextmenu;
  exports org.tentackle.fx.rdc.crud;
  exports org.tentackle.fx.rdc.login;
  exports org.tentackle.fx.rdc.search;
  exports org.tentackle.fx.rdc.security;
  exports org.tentackle.fx.rdc.table;
  exports org.tentackle.fx.rdc.translate;

  opens org.tentackle.fx.rdc.admin;
  opens org.tentackle.fx.rdc.crud;
  opens org.tentackle.fx.rdc.login;
  opens org.tentackle.fx.rdc.search;
  opens org.tentackle.fx.rdc.security;
  opens org.tentackle.fx.rdc.table;
  opens org.tentackle.fx.rdc.translate;

  requires transitive org.tentackle.fx;
  requires transitive org.tentackle.pdo;

  provides org.tentackle.common.ModuleHook with org.tentackle.fx.rdc.service.Hook;

}
