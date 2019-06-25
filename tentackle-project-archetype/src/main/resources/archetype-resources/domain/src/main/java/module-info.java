#set( $symbol_pound = '#' )
#set( $symbol_dollar = '$' )
#set( $symbol_escape = '\' )

module ${package}.domain {
  exports ${package}.domain;
  exports ${package}.domain.md;
  exports ${package}.domain.td;

  requires transitive ${package}.pdo;

  requires transitive org.tentackle.domain;

  provides org.tentackle.common.ModuleHook with ${package}.domain.service.Hook;
}
