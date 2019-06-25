#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

open module ${package}.gui {
  exports ${package}.gui;
  exports ${package}.gui.about;
  exports ${package}.gui.main;
  exports ${package}.gui.message;
  exports ${package}.gui.password;
  exports ${package}.gui.prefs;
  exports ${package}.gui.user;
  exports ${package}.gui.usergroup;

  requires ${package}.pdo;

  requires org.tentackle.fx.rdc;

  requires java.desktop;
  requires java.prefs;

  provides org.tentackle.common.ModuleHook with ${package}.gui.service.Hook;
}
