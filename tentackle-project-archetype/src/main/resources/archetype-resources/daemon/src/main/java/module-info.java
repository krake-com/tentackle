#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

module ${package}.daemon {
  exports ${package}.daemon;

  requires ${package}.persist;
  requires ${package}.domain;

  requires java.rmi;

  provides org.tentackle.common.ModuleHook with ${package}.daemon.service.Hook;

  // SLF4J isnt a module yet. For some odd reason, we need a lookup scan for deps on the classpath.
  // Otherwise non-modularized deps cannot be located via META-INF/services.
  uses org.tentackle.log.Logger;
}
