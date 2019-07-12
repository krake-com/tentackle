#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

module ${package}.persist {
  exports ${package}.persist;
  exports ${package}.persist.md;
  exports ${package}.persist.md.rmi;
  exports ${package}.persist.rmi;
  exports ${package}.persist.td;
  exports ${package}.persist.td.rmi;

  requires transitive ${package}.pdo;

  requires transitive org.tentackle.persistence;

  requires java.rmi;

  provides org.tentackle.common.ModuleHook with ${package}.persist.service.Hook;
}
