tentackle-quiche binds the native Cloudflare quiche library, so it needs
native access, which no other Tentackle transport does.

Loading and calling libquiche are restricted operations. Add

    --enable-native-access=org.tentackle.quiche

to the JVM arguments (ALL-UNNAMED on the class path), or the JVM prints a
warning on the first restricted call today and will refuse the calls in a
future release. For a jlink/jpackage image, add it to the launcher's JVM
options.

The binaries in org.mortbay.jetty.quiche cover linux-x86-64, linux-aarch64,
darwin-x86-64, darwin-aarch64 and win32-x86-64. On anything else, point

    -Dtentackle.quiche.library=/absolute/path/to/libquiche.so

at a locally built library.

See src/main/docs/quiche.md for the full documentation.
