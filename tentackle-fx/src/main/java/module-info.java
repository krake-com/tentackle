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
 * tentackle-fx module.
 */
module org.tentackle.fx {

  exports org.tentackle.fx;
  exports org.tentackle.fx.apt;
  exports org.tentackle.fx.bind;
  exports org.tentackle.fx.component;
  exports org.tentackle.fx.component.auto;
  exports org.tentackle.fx.component.build;
  exports org.tentackle.fx.component.config;
  exports org.tentackle.fx.component.delegate;
  exports org.tentackle.fx.component.skin;
  exports org.tentackle.fx.container;
  exports org.tentackle.fx.container.build;
  exports org.tentackle.fx.container.config;
  exports org.tentackle.fx.container.delegate;
  exports org.tentackle.fx.nofxml;
  exports org.tentackle.fx.table;
  exports org.tentackle.fx.table.filter;
  exports org.tentackle.fx.table.type;
  exports org.tentackle.fx.translate;

  opens org.tentackle.fx;                             // to access tentackle.css from StyleManager
  opens org.tentackle.fx.translate to javafx.fxml;    // to enable FXML for I18NEditor

  requires transitive org.tentackle.core;

  requires transitive javafx.fxml;
  requires transitive javafx.web;   // transitive java.xml, javafx.base, javafx.controls, javafx.graphics

  requires transitive org.kordamp.ikonli.javafx;
  requires transitive org.kordamp.ikonli.materialdesign2;

  requires java.desktop;

  provides org.tentackle.common.ModuleHook with org.tentackle.fx.service.Hook;

}
