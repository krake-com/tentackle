#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

module ${package}.common {
  exports ${package}.common;

  requires transitive org.tentackle.pdo;

  provides org.tentackle.common.ModuleHook with ${package}.common.service.Hook;
}
