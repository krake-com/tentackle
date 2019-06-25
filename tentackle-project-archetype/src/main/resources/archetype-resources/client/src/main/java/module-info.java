#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

module ${package}.client {
  exports ${package}.client;

  requires ${package}.gui;
  requires ${package}.persist;
  requires ${package}.domain;

  requires org.tentackle.fx.rdc;
  requires org.tentackle.fx.rdc.update;

  requires javafx.graphics;
  requires java.rmi;

  provides org.tentackle.common.ModuleHook with ${package}.client.service.Hook;

  // SLF4J isnt a module yet. For some odd reason, we need a lookup scan for deps on the classpath.
  // Otherwise non-modularized deps cannot be located via META-INF/services.
  uses org.tentackle.log.Logger;
}
