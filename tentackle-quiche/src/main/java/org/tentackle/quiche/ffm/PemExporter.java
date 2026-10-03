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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Base64;
import java.util.Enumeration;

/**
 * Writes the contents of a {@link KeyStore} to the PEM files quiche insists on.
 * <p>
 * The rest of Tentackle configures TLS with a Java keystore &mdash; {@code ksfile}, {@code ksalias},
 * {@code kspass} and {@code tsfile}, see {@code org.tentackle.io.SslParameters} &mdash; because that is
 * what JSSE consumes. Quiche, being BoringSSL underneath, takes file paths to PEM and nothing else. The
 * gap is bridged here rather than by making the QUIC transports configured differently from every other
 * transport.
 * <p>
 * The files are written to the temporary directory with owner-only permissions. Quiche reads them while
 * loading them into its configuration, so the caller {@link #delete}s them right afterward; deletion when
 * the JVM exits is merely the backstop. This is the one place where a private key leaves the keystore, so
 * the permissions are set when the file is <em>created</em>, not afterward: a key that is briefly
 * world-readable is a leaked key.
 */
public final class PemExporter {

  /**
   * The prefix of the temporary files.
   */
  public static final String TEMPFILE_PREFIX = "tentackle-quiche-";

  private static final int LINE_LENGTH = 64;


  /**
   * Deletes a file written by this class, as soon as it is no longer needed.<br>
   * A failure is ignored: the file is deleted when the JVM exits anyway.
   *
   * @param file the file
   */
  public static void delete(Path file) {
    try {
      Files.deleteIfExists(file);
    }
    catch (IOException e) {
      // deleteOnExit is the backstop
    }
  }

  /**
   * Writes the certificate chain of a keystore entry to a PEM file.
   *
   * @param keyStore the keystore
   * @param alias the alias of the entry
   * @return the file, to be {@link #delete}d once loaded
   * @throws QuicheException if the keystore does not provide what is to be written
   * @throws UncheckedIOException if the file cannot be written
   */
  public static Path writeCertificateChain(KeyStore keyStore, String alias) {
    Certificate[] chain;
    try {
      chain = keyStore.getCertificateChain(alias);
    }
    catch (KeyStoreException e) {
      throw new QuicheException("cannot read the certificate chain of '" + alias + "'", e);
    }
    if (chain == null || chain.length == 0) {
      throw new QuicheException("the keystore entry '" + alias + "' has no certificate chain");
    }
    StringBuilder pem = new StringBuilder();
    for (Certificate certificate : chain) {
      append(pem, "CERTIFICATE", encoded(certificate));
    }
    return write("cert-", ".pem", pem.toString());
  }

  /**
   * Writes the private key of a keystore entry to a PEM file, in PKCS#8 format.
   *
   * @param keyStore the keystore
   * @param alias the alias of the entry
   * @param password the password protecting the key; may be null
   * @return the file, to be {@link #delete}d once loaded
   * @throws QuicheException if the keystore does not provide what is to be written
   * @throws UncheckedIOException if the file cannot be written
   */
  public static Path writePrivateKey(KeyStore keyStore, String alias, char[] password) {
    Key key;
    try {
      key = keyStore.getKey(alias, password);
    }
    catch (GeneralSecurityException e) {
      throw new QuicheException("cannot read the private key of '" + alias + "'", e);
    }
    if (!(key instanceof PrivateKey privateKey)) {
      throw new QuicheException("the keystore entry '" + alias + "' holds no private key");
    }
    byte[] encoded = privateKey.getEncoded();
    if (encoded == null) {
      throw new QuicheException("the private key of '" + alias + "' cannot be exported (" +
                                privateKey.getClass().getName() + " is not extractable, a hardware token?)");
    }
    StringBuilder pem = new StringBuilder();
    // getEncoded() is PKCS#8 for every JCE key, which is what "PRIVATE KEY" denotes
    append(pem, "PRIVATE KEY", encoded);
    return write("key-", ".pem", pem.toString());
  }

  /**
   * Writes all certificates of a truststore to a PEM file.
   *
   * @param trustStore the truststore
   * @return the file, to be {@link #delete}d once loaded
   * @throws QuicheException if the keystore does not provide what is to be written
   * @throws UncheckedIOException if the file cannot be written
   */
  public static Path writeTrustedCertificates(KeyStore trustStore) {
    StringBuilder pem = new StringBuilder();
    int count = 0;
    try {
      for (Enumeration<String> aliases = trustStore.aliases(); aliases.hasMoreElements(); ) {
        String alias = aliases.nextElement();
        Certificate certificate = trustStore.getCertificate(alias);
        if (certificate != null) {
          append(pem, "CERTIFICATE", encoded(certificate));
          count++;
        }
      }
    }
    catch (KeyStoreException e) {
      throw new QuicheException("cannot read the truststore", e);
    }
    if (count == 0) {
      throw new QuicheException("the truststore holds no certificates");
    }
    return write("trust-", ".pem", pem.toString());
  }

  /**
   * Gets the DER encoding of a certificate.
   *
   * @param certificate the certificate
   * @return the encoding
   */
  private static byte[] encoded(Certificate certificate) {
    try {
      return certificate.getEncoded();
    }
    catch (CertificateEncodingException e) {
      throw new QuicheException("cannot encode the certificate", e);
    }
  }

  /**
   * Appends one PEM block.
   *
   * @param pem the text to append to
   * @param label the block label, for example {@code CERTIFICATE}
   * @param der the DER encoded content
   */
  private static void append(StringBuilder pem, String label, byte[] der) {
    pem.append("-----BEGIN ").append(label).append("-----\n");
    String base64 = Base64.getEncoder().encodeToString(der);
    for (int i = 0; i < base64.length(); i += LINE_LENGTH) {
      pem.append(base64, i, Math.min(i + LINE_LENGTH, base64.length())).append('\n');
    }
    pem.append("-----END ").append(label).append("-----\n");
  }

  /**
   * Writes a temporary file readable by nobody but this user.
   *
   * @param prefix the file name prefix, appended to {@value #TEMPFILE_PREFIX}
   * @param suffix the file name suffix
   * @param content the content
   * @return the file
   */
  private static Path write(String prefix, String suffix, String content) {
    try {
      Path file = createRestrictedTempFile(prefix, suffix);
      file.toFile().deleteOnExit();
      Files.writeString(file, content, StandardCharsets.US_ASCII);
      return file;
    }
    catch (IOException e) {
      throw new UncheckedIOException("cannot write the PEM file", e);
    }
  }

  /**
   * Creates a temporary file that nobody but its owner can read.
   *
   * @param prefix the file name prefix, appended to {@value #TEMPFILE_PREFIX}
   * @param suffix the file name suffix
   * @return the file
   * @throws IOException if the file cannot be created
   */
  private static Path createRestrictedTempFile(String prefix, String suffix) throws IOException {
    FileAttribute<?> ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    try {
      return Files.createTempFile(TEMPFILE_PREFIX + prefix, suffix, ownerOnly);
    }
    catch (UnsupportedOperationException e) {
      // no POSIX permissions (Windows): createTempFile restricts the file to the owner anyway
      return Files.createTempFile(TEMPFILE_PREFIX + prefix, suffix);
    }
  }

  private PemExporter() {
  }

}
