/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.quiche.ffm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Set;

/**
 * Loads the native Cloudflare quiche library.
 * <p>
 * The binaries ship in the {@code org.mortbay.jetty.quiche} artifact, one directory per platform, and are
 * extracted to a temporary file before being loaded: a shared library cannot be loaded out of a jar.
 * The directory names contain a hyphen and are therefore not valid package names, which is what keeps them
 * accessible: resources in a named module are only encapsulated when their directory maps to a package.
 * <p>
 * Setting the system property {@value #LIBRARY_PROPERTY} to the path of a {@code libquiche} bypasses all of
 * this and loads that file instead, for platforms the artifact does not cover or to run against a
 * locally built quiche.
 */
public final class NativeLibrary {

  /**
   * The system property naming an alternative {@code libquiche} to load.
   */
  public static final String LIBRARY_PROPERTY = "tentackle.quiche.library";

  /**
   * The prefix of the temporary file the library is extracted to.
   */
  public static final String TEMPFILE_PREFIX = "tentackle-quiche-";


  /**
   * The symbol lookup for the loaded library.
   * <p>
   * Loading happens exactly once, in this class's initializer, so that a failure surfaces as an
   * {@link ExceptionInInitializerError} at the first use of any binding instead of being retried
   * over and over.
   */
  private static final SymbolLookup LOOKUP;

  static {
    String library = System.getProperty(LIBRARY_PROPERTY);
    if (library == null) {
      library = extract().toString();
    }
    load(library);
    // the library was loaded by this class's loader, so the loader lookup finds its symbols
    LOOKUP = SymbolLookup.loaderLookup();
  }

  /**
   * Loads the shared library.
   * <p>
   * Restricted: loading native code cannot be checked by the JVM, which is what
   * {@code --enable-native-access=org.tentackle.quiche} acknowledges at runtime.
   *
   * @param library the absolute path of the library
   */
  @SuppressWarnings("restricted")
  private static void load(String library) {
    System.load(library);
  }


  /**
   * Gets the symbol lookup for the native quiche library.
   *
   * @return the lookup
   */
  public static SymbolLookup getLookup() {
    return LOOKUP;
  }

  /**
   * Gets the platform directory the binaries are stored in.<br>
   * The names are the ones used by the {@code jetty-quiche-native} artifact.
   *
   * @return the directory name, for example {@code darwin-aarch64}
   * @throws UnsupportedOperationException if there is no binary for this platform
   */
  public static String getPlatform() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    String os;
    if (osName.startsWith("mac") || osName.startsWith("darwin")) {
      os = "darwin";
    }
    else if (osName.startsWith("linux")) {
      os = "linux";
    }
    else if (osName.startsWith("windows")) {
      os = "win32";
    }
    else {
      throw new UnsupportedOperationException(
        "no quiche binary for operating system '" + System.getProperty("os.name") + "', set -D" +
        LIBRARY_PROPERTY + "=<path to libquiche>");
    }

    String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    String arch = switch (osArch) {
      case "aarch64", "arm64" -> "aarch64";
      case "amd64", "x86_64", "x86-64" -> "x86-64";
      default -> throw new UnsupportedOperationException(
        "no quiche binary for architecture '" + System.getProperty("os.arch") + "', set -D" +
        LIBRARY_PROPERTY + "=<path to libquiche>");
    };

    return os + "-" + arch;
  }

  /**
   * Gets the file name of the shared library for this platform.
   *
   * @return the file name
   */
  public static String getLibraryName() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (osName.startsWith("mac") || osName.startsWith("darwin")) {
      return "libquiche.dylib";
    }
    if (osName.startsWith("windows")) {
      return "quiche.dll";
    }
    return "libquiche.so";
  }

  /**
   * Extracts the shared library for this platform to a temporary file.
   *
   * @return the path of the extracted library
   */
  private static Path extract() {
    String resource = getPlatform() + "/" + getLibraryName();
    try (InputStream is = open(resource)) {
      if (is == null) {
        throw new UnsupportedOperationException(
          "the quiche binary '" + resource + "' is not on the module- or classpath: add a dependency on " +
          "org.mortbay.jetty.quiche:jetty-quiche-native, or set -D" + LIBRARY_PROPERTY + "=<path to libquiche>");
      }
      String suffix = getLibraryName().substring(getLibraryName().lastIndexOf('.'));
      Path file = Files.createTempFile(TEMPFILE_PREFIX, suffix);
      file.toFile().deleteOnExit();
      restrictToOwner(file);
      Files.copy(is, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return file;
    }
    catch (IOException e) {
      throw new UncheckedIOException("cannot extract the quiche binary '" + resource + "'", e);
    }
  }

  /**
   * Opens a resource of the quiche binary artifact.
   * <p>
   * The artifact is a JPMS module, so it is looked up in the boot layer first. The class loader is the
   * fallback for a non-modular (classpath) environment, and for a module layer this module was not
   * resolved in.
   *
   * @param resource the resource name
   * @return the stream, null if there is no such resource
   * @throws IOException if opening the resource failed
   */
  private static InputStream open(String resource) throws IOException {
    java.util.Optional<Module> module = ModuleLayer.boot().findModule("org.mortbay.jetty.quiche");
    if (module.isPresent()) {
      InputStream is = module.get().getResourceAsStream(resource);
      if (is != null) {
        return is;
      }
    }
    return NativeLibrary.class.getClassLoader().getResourceAsStream(resource);
  }

  /**
   * Takes all permissions but the owner's away from the extracted library.
   * <p>
   * The file is executable code loaded into this process: on a system with a shared temp directory,
   * a world-writable copy would let any local user run code in this JVM. Silently ignored on file
   * systems without POSIX permissions, where {@link Files#createTempFile} is restrictive anyway.
   *
   * @param file the extracted library
   */
  private static void restrictToOwner(Path file) {
    try {
      Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
      Files.setPosixFilePermissions(file, permissions);
    }
    catch (IOException | UnsupportedOperationException e) {
      // no POSIX permissions (Windows): createTempFile already restricts the file to the owner
    }
  }

  private NativeLibrary() {
  }

}
