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

open module org.tentackle.fx.rdc {
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

  requires org.tentackle.fx;
  requires org.tentackle.core;
  requires org.tentackle.common;
  requires org.tentackle.pdo;
  requires org.tentackle.session;

  requires java.compiler;
  requires java.desktop;
  requires java.prefs;
  requires javafx.graphics;
  requires javafx.controls;
  requires javafx.fxml;

  provides org.tentackle.common.ModuleHook with org.tentackle.fx.rdc.service.Hook;
}
