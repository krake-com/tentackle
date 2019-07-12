#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

open module ${package}.gui {
  exports ${package}.gui;
  exports ${package}.gui.main;

  requires ${package}.pdo;

  requires org.tentackle.fx.rdc;

  requires java.desktop;
  requires java.prefs;

  provides org.tentackle.common.ModuleHook with ${package}.gui.service.Hook;
}
