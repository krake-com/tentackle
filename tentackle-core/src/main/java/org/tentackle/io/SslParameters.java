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

package org.tentackle.io;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedKeyManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;

/**
 * Resolves the SSL/TLS configuration for the TCP socket factories.<br>
 * <p>
 * Unlike the default {@link javax.net.ssl.SSLServerSocketFactory}, which always presents the
 * <em>first</em> suitable certificate found in the keystore, this configuration allows selecting
 * the server (or client) certificate by its <b>alias</b> and tuning the SSL options
 * (enabled protocols, cipher suites, client authentication, etc.).
 * <p>
 * Each configuration value is resolved from up to three sources, in order of precedence:
 * <ol>
 *   <li>the URI query parameter (short key, e.g. {@code ksfile}),</li>
 *   <li>the Tentackle system property {@code tentackle.ssl.<name>} (e.g. {@code tentackle.ssl.keyStore}),</li>
 *   <li>the standard JSSE system property {@code javax.net.ssl.<name>} (e.g. {@code javax.net.ssl.keyStore})
 *       as a fallback, so that legacy configurations keep working.</li>
 * </ol>
 * The following parameters are understood:
 * <table>
 *   <caption>SSL configuration parameters</caption>
 *   <tr><th>Query key</th><th>{@code tentackle.ssl.} suffix</th><th>{@code javax.net.ssl.} fallback</th><th>Description</th></tr>
 *   <tr><td>{@code ksfile}</td><td>{@code keyStore}</td><td>{@code keyStore}</td><td>path to the keystore holding the own certificate(s)</td></tr>
 *   <tr><td>{@code kspass}</td><td>{@code keyStorePassword}</td><td>{@code keyStorePassword}</td><td>keystore password</td></tr>
 *   <tr><td>{@code kstype}</td><td>{@code keyStoreType}</td><td>{@code keyStoreType}</td><td>keystore type (default: JDK default, usually {@code PKCS12})</td></tr>
 *   <tr><td>{@code ksalias}</td><td>{@code keyStoreAlias}</td><td>&mdash;</td><td>alias of the certificate to present (optional if the keystore holds a single key entry)</td></tr>
 *   <tr><td>{@code kmpass}</td><td>{@code keyPassword}</td><td>{@code keyPassword}</td><td>private key password (default: keystore password)</td></tr>
 *   <tr><td>{@code tsfile}</td><td>{@code trustStore}</td><td>{@code trustStore}</td><td>path to the truststore verifying the peer (default: JSSE default trust store)</td></tr>
 *   <tr><td>{@code tspass}</td><td>{@code trustStorePassword}</td><td>{@code trustStorePassword}</td><td>truststore password</td></tr>
 *   <tr><td>{@code tstype}</td><td>{@code trustStoreType}</td><td>{@code trustStoreType}</td><td>truststore type</td></tr>
 *   <tr><td>{@code protocol}</td><td>{@code protocol}</td><td>&mdash;</td><td>{@link SSLContext} protocol (default {@code TLS})</td></tr>
 *   <tr><td>{@code protocols}</td><td>{@code protocols}</td><td>&mdash;</td><td>comma-separated list of enabled protocols, e.g. {@code TLSv1.3,TLSv1.2}</td></tr>
 *   <tr><td>{@code ciphers}</td><td>{@code cipherSuites}</td><td>&mdash;</td><td>comma-separated list of enabled cipher suites</td></tr>
 *   <tr><td>{@code clientauth}</td><td>{@code clientAuth}</td><td>&mdash;</td><td>server-side client-certificate policy: {@code none} (default), {@code want} or {@code need}</td></tr>
 *   <tr><td>{@code endpointid}</td><td>{@code endpointIdentification}</td><td>&mdash;</td><td>client-side endpoint identification algorithm, e.g. {@code HTTPS} (default: none)</td></tr>
 * </table>
 * <p>
 * Client certificates are supported by simply providing a keystore (and, if the certificate should be
 * chosen explicitly, an alias) on the client side.
 *
 * @see TcpServerSocketFactory
 * @see TcpSocketFactory
 */
public class SslParameters {

  private static final Logger LOGGER = Logger.get(SslParameters.class);

  /** The prefix for Tentackle SSL system properties. */
  public static final String SYSTEM_PROPERTY_PREFIX = "tentackle.ssl.";

  /** Client-certificate policy on the server side. */
  private enum ClientAuth { NONE, WANT, NEED }

  private final URI uri;                        // the transport URI (for diagnostics)
  private final Map<String, String> query;      // the URI query parameters

  private final String keyStoreFile;            // own keystore path, null if none
  private final String keyStorePassword;        // keystore password
  private final String keyStoreType;            // keystore type, null for JDK default
  private final String keyStoreAlias;           // alias to present, null to let JSSE decide
  private final String keyPassword;             // private key password, null falls back to keyStorePassword

  private final String trustStoreFile;          // truststore path, null for JSSE default
  private final String trustStorePassword;      // truststore password
  private final String trustStoreType;          // truststore type, null for JDK default

  private final String protocol;                // SSLContext protocol, null for "TLS"
  private final String[] enabledProtocols;      // enabled protocols, null for JDK default
  private final String[] enabledCipherSuites;   // enabled cipher suites, null for JDK default
  private final ClientAuth clientAuth;          // server-side client-certificate policy
  private final String endpointIdentification;  // client-side endpoint identification, null for none

  private SSLContext sslContext;                // lazily built, shared context

  /**
   * Creates the SSL parameters from a transport URI.
   *
   * @param uri the transport URI providing the query parameters, never null
   */
  public SslParameters(URI uri) {
    this.uri = uri;
    this.query = UriHelper.getQueryParameters(uri.getQuery(), String::valueOf);

    keyStoreFile     = resolve("ksfile",  "keyStore",         "javax.net.ssl.keyStore");
    keyStorePassword = resolve("kspass",  "keyStorePassword", "javax.net.ssl.keyStorePassword");
    keyStoreType     = resolve("kstype",  "keyStoreType",     "javax.net.ssl.keyStoreType");
    keyStoreAlias    = resolve("ksalias", "keyStoreAlias",    null);
    keyPassword      = resolve("kmpass",  "keyPassword",      "javax.net.ssl.keyPassword");

    trustStoreFile     = resolve("tsfile", "trustStore",         "javax.net.ssl.trustStore");
    trustStorePassword = resolve("tspass", "trustStorePassword", "javax.net.ssl.trustStorePassword");
    trustStoreType     = resolve("tstype", "trustStoreType",     "javax.net.ssl.trustStoreType");

    protocol               = resolve("protocol",   "protocol",               null);
    enabledProtocols       = split(resolve("protocols",  "protocols",         null));
    enabledCipherSuites    = split(resolve("ciphers",    "cipherSuites",      null));
    clientAuth             = parseClientAuth(resolve("clientauth", "clientAuth", null));
    endpointIdentification = resolve("endpointid", "endpointIdentification", null);
  }


  /**
   * Gets the SSL context built from these parameters.<br>
   * The context is created once and cached; the same instance is shared between the
   * server- and client socket factories.
   *
   * @return the SSL context
   * @throws TentackleRuntimeException if the context cannot be created
   */
  public synchronized SSLContext getSSLContext() {
    if (sslContext == null) {
      try {
        SSLContext context = SSLContext.getInstance(protocol == null ? "TLS" : protocol);
        context.init(createKeyManagers(), createTrustManagers(), null);
        sslContext = context;
        LOGGER.fine("created SSL context {0} for {1}", context.getProtocol(), uri);
      }
      catch (GeneralSecurityException | IOException e) {
        throw new TentackleRuntimeException("cannot create SSL context for " + uri, e);
      }
    }
    return sslContext;
  }

  /**
   * Configures a freshly created server socket with the resolved SSL options.<br>
   * Applies the enabled protocols and cipher suites and the client-certificate policy.
   *
   * @param socket the SSL server socket to configure
   */
  public void configureServer(SSLServerSocket socket) {
    if (enabledProtocols != null) {
      socket.setEnabledProtocols(enabledProtocols);
    }
    if (enabledCipherSuites != null) {
      socket.setEnabledCipherSuites(enabledCipherSuites);
    }
    switch (clientAuth) {
      case NEED -> socket.setNeedClientAuth(true);
      case WANT -> socket.setWantClientAuth(true);
      case NONE -> { }
    }
  }

  /**
   * Configures a freshly created client socket with the resolved SSL options.<br>
   * Applies the enabled protocols and cipher suites and, if requested, the endpoint
   * identification algorithm used to verify the server's certificate against the host name.
   *
   * @param socket the SSL socket to configure
   */
  public void configureClient(SSLSocket socket) {
    if (enabledProtocols != null) {
      socket.setEnabledProtocols(enabledProtocols);
    }
    if (enabledCipherSuites != null) {
      socket.setEnabledCipherSuites(enabledCipherSuites);
    }
    if (endpointIdentification != null) {
      SSLParameters params = socket.getSSLParameters();
      params.setEndpointIdentificationAlgorithm(endpointIdentification);
      socket.setSSLParameters(params);
    }
  }


  /**
   * Resolves a configuration value from the query parameters or the system properties.
   *
   * @param queryKey the URI query parameter key
   * @param tentackleSuffix the suffix appended to {@value #SYSTEM_PROPERTY_PREFIX}
   * @param javaxProperty the fallback {@code javax.net.ssl.*} property name, or null if none
   * @return the resolved value, or null if not configured
   */
  private String resolve(String queryKey, String tentackleSuffix, String javaxProperty) {
    String value = query.get(queryKey);
    if (value == null) {
      value = System.getProperty(SYSTEM_PROPERTY_PREFIX + tentackleSuffix);
    }
    if (value == null && javaxProperty != null) {
      value = System.getProperty(javaxProperty);
    }
    return value;
  }

  /**
   * Creates the key managers presenting the own certificate.<br>
   * The certificate is selected by the configured alias. If no alias is configured but the keystore
   * contains exactly one key entry, that entry is used automatically &mdash; mimicking the classic
   * behavior of single-certificate keystores. If several key entries exist and no alias is given,
   * the selection is left to the JSSE default. When an alias is determined, each X.509 key manager
   * is wrapped so that it always presents the certificate stored under that alias.
   *
   * @return the key managers, or null if no keystore is configured
   */
  private KeyManager[] createKeyManagers() throws GeneralSecurityException, IOException {
    if (keyStoreFile == null) {
      return null;    // no own certificate (typical for a plain SSL client)
    }
    KeyStore keyStore = loadKeyStore(keyStoreFile, keyStoreType, keyStorePassword);
    KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    String password = keyPassword != null ? keyPassword : keyStorePassword;
    factory.init(keyStore, password == null ? null : password.toCharArray());
    KeyManager[] keyManagers = factory.getKeyManagers();
    String alias = keyStoreAlias != null ? keyStoreAlias : soleKeyEntryAlias(keyStore);
    if (alias != null) {
      for (int i = 0; i < keyManagers.length; i++) {
        if (keyManagers[i] instanceof X509ExtendedKeyManager manager) {
          keyManagers[i] = new AliasKeyManager(manager, alias);
        }
      }
    }
    return keyManagers;
  }

  /**
   * Determines the alias of the keystore's only key entry.<br>
   * This lets a single-certificate keystore be used without specifying an alias, as was always
   * the case before multiple certificates per keystore were supported.
   *
   * @param keyStore the keystore to inspect
   * @return the sole key entry's alias, or null if the keystore holds zero or more than one key entry
   */
  private static String soleKeyEntryAlias(KeyStore keyStore) throws KeyStoreException {
    String found = null;
    for (Enumeration<String> aliases = keyStore.aliases(); aliases.hasMoreElements(); ) {
      String alias = aliases.nextElement();
      if (keyStore.isKeyEntry(alias)) {
        if (found != null) {
          return null;    // more than one key entry -> an explicit alias is required
        }
        found = alias;
      }
    }
    return found;
  }

  /**
   * Creates the trust managers verifying the peer's certificate.
   *
   * @return the trust managers, or null to use the JSSE default trust store
   */
  private TrustManager[] createTrustManagers() throws GeneralSecurityException, IOException {
    if (trustStoreFile == null) {
      return null;    // JSSE default trust store (cacerts / javax.net.ssl.trustStore)
    }
    KeyStore trustStore = loadKeyStore(trustStoreFile, trustStoreType, trustStorePassword);
    TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    factory.init(trustStore);
    return factory.getTrustManagers();
  }

  /**
   * Loads a keystore or truststore from a file.
   *
   * @param file the keystore file path
   * @param type the keystore type, or null for the JDK default
   * @param password the keystore password, or null
   * @return the loaded keystore
   */
  private static KeyStore loadKeyStore(String file, String type, String password)
         throws GeneralSecurityException, IOException {
    KeyStore keyStore = KeyStore.getInstance(type == null ? KeyStore.getDefaultType() : type);
    try (InputStream in = new FileInputStream(file)) {
      keyStore.load(in, password == null ? null : password.toCharArray());
    }
    return keyStore;
  }

  /**
   * Splits a comma-separated list into its trimmed elements.
   *
   * @param value the comma-separated value, or null
   * @return the trimmed elements, or null if the value is null
   */
  private static String[] split(String value) {
    if (value == null) {
      return null;
    }
    String[] parts = value.split(",");
    for (int i = 0; i < parts.length; i++) {
      parts[i] = parts[i].trim();
    }
    return parts;
  }

  /**
   * Parses the client-authentication policy.
   *
   * @param value the configured value, or null
   * @return the policy, {@link ClientAuth#NONE} if not configured
   */
  private ClientAuth parseClientAuth(String value) {
    if (value == null) {
      return ClientAuth.NONE;
    }
    try {
      return ClientAuth.valueOf(value.trim().toUpperCase());
    }
    catch (IllegalArgumentException ix) {
      throw new TentackleRuntimeException(
        "invalid client-authentication policy '" + value + "' in " + uri + " (expected none, want or need)", ix);
    }
  }


  /**
   * An X.509 key manager that always presents the certificate stored under a fixed alias.<br>
   * This overrides the JSSE default behavior of picking the first suitable certificate,
   * allowing the certificate to be selected explicitly by its alias.
   */
  private static final class AliasKeyManager extends X509ExtendedKeyManager {

    private final X509ExtendedKeyManager delegate;
    private final String alias;

    AliasKeyManager(X509ExtendedKeyManager delegate, String alias) {
      this.delegate = delegate;
      this.alias = alias;
    }

    @Override
    public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
      return alias;
    }

    @Override
    public String chooseEngineServerAlias(String keyType, Principal[] issuers, SSLEngine engine) {
      return alias;
    }

    @Override
    public String chooseClientAlias(String[] keyType, Principal[] issuers, Socket socket) {
      return alias;
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
      return alias;
    }

    @Override
    public String[] getClientAliases(String keyType, Principal[] issuers) {
      return delegate.getClientAliases(keyType, issuers);
    }

    @Override
    public String[] getServerAliases(String keyType, Principal[] issuers) {
      return delegate.getServerAliases(keyType, issuers);
    }

    @Override
    public X509Certificate[] getCertificateChain(String alias) {
      return delegate.getCertificateChain(alias);
    }

    @Override
    public PrivateKey getPrivateKey(String alias) {
      return delegate.getPrivateKey(alias);
    }
  }

}
