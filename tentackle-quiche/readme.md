tentackle-quiche binds the native Cloudflare quiche library, so two things
have to be arranged that no other Tentackle module needs.

1. Native access

   Loading and calling libquiche are restricted operations. Add

       --enable-native-access=org.tentackle.quiche

   to the JVM arguments, or the JVM prints a warning per call site today and
   will refuse the calls in a future release.

2. Module path

   When using the tentackle-jlink-maven-plugin, make sure to move
   tentackle-quiche and the native library artifact to the module path.
   Otherwise, _all_ modules would be moved to the module path.

       <plugin>
         <groupId>org.tentackle</groupId>
         <artifactId>tentackle-jlink-maven-plugin</artifactId>
         <configuration>
           <modulePathOnly>
             <module>org.tentackle.quiche</module>
             <module>org.mortbay.jetty.quiche</module>
           </modulePathOnly>
           ...

   This is possible since tentackle-quiche itself is not required
   by any other tentackle module, but instead configures the QUIC transports
   via `@TransportService`.

The binaries in org.mortbay.jetty.quiche cover linux-x86-64, linux-aarch64,
darwin-x86-64, darwin-aarch64 and win32-x86-64. On anything else, point

    -Dtentackle.quiche.library=/path/to/libquiche.so

at a locally built library.
