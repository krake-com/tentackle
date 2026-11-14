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

/**
 * Pluggable authentication.
 * <p>
 * The client obtains its {@link org.tentackle.session.auth.Credentials} from a
 * {@link org.tentackle.session.auth.CredentialsProvider} — trivially for a username and password,
 * or by running a single sign-on protocol — and attaches them to the
 * {@link org.tentackle.session.SessionInfo}. They travel to the application server, where the
 * {@link org.tentackle.session.auth.AuthenticationManager} drives a chain of
 * {@link org.tentackle.session.auth.AuthenticationProvider}s to verify them and the optional
 * {@link org.tentackle.session.auth.UserResolver} maps the proven identity onto the application's
 * user entity.
 * <p>
 * Along with the credentials, the providers receive the {@link org.tentackle.session.auth.AuthenticationContext}:
 * what the server itself knows about the client, such as the client certificate verified by a mutually
 * authenticated TLS connection. A client can thus log in by its certificate, see
 * {@link org.tentackle.session.auth.CertificateCredentials}.
 * <p>
 * Authentication answers "who is this?"; authorization — Tentackle's {@code SecurityManager},
 * permissions, and rules — answers "what may they do?" and lives in {@code tentackle-pdo}.
 */
package org.tentackle.session.auth;
