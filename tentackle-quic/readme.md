Since version 0.10.9, kwik illegally requires the automatic module `io.whitfin.siphash`.

When using the tentackle-jlink-maven-plugin, make sure to move
kwik and tentackle-quic to the module path. Otherwise, the _all_ modules
would be moved to the module path.

      <plugin>
        <groupId>org.tentackle</groupId>
        <artifactId>tentackle-jlink-maven-plugin</artifactId>
        <configuration>
          <modulePathOnly>
            <module>org.tentackle.quic</module>
            <module>tech.kwik.core</module>
          </modulePathOnly>
          ...

This is possible since tentackle-quic itself is not required 
by any other tentackle module, but instead configures the QUIC transports 
via `@TransportService`.
