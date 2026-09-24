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
package org.tentackle.session.auth;

import org.testng.annotations.Test;

import org.tentackle.trip.OpCode;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.EchoTransceiver;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests the TRIP serialization of a {@link DefaultAuthentication}.<br>
 * The final components are passed through its {@code @CanonicalConstructor}, the others are set afterward.
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultAuthenticationTripTest {

  @SuppressWarnings("unchecked")
  private static <T> T tunnel(T object) {
    EchoTransceiver transceiver = new EchoTransceiver();
    TripType<T> type = TripFactory.getInstance().getType((Class<T>) object.getClass());
    type.write(object, transceiver.getStream());
    transceiver.echo();
    OpCode opCode = transceiver.getStream().getDeserializer().deserializeOpCode();
    return type.read(transceiver.getStream(), opCode);
  }


  @Test
  public void testPasswordAuthentication() {
    DefaultAuthentication auth = new DefaultAuthentication("hugo",
                                                           new PasswordCredentials("hugo", "secret".toCharArray()));
    auth.getAttributes().put("email", "hugo@example.com");
    auth.getAttributes().put("groups", List.of("a", "b"));
    auth.getAttributes().put("level", 42);
    auth.getRoles().add("admin");
    auth.getRoles().add("user");
    auth.setProviderName("ldap");
    auth.setExpiresAt(1234567890123L);
    auth.setUserId(4711L);
    auth.setUserClassId(815);

    DefaultAuthentication copy = tunnel(auth);
    assertNotSame(copy, auth);
    assertEquals(copy.getName(), "hugo");
    assertEquals(copy.getAttributes(), Map.of("email", "hugo@example.com",
                                              "groups", List.of("a", "b"),
                                              "level", 42));
    assertEquals(copy.getRoles(), Set.of("admin", "user"));
    assertEquals(List.copyOf(copy.getRoles()), List.of("admin", "user"));   // order preserved
    assertEquals(copy.getProviderName(), "ldap");
    assertEquals(copy.getExpiresAt(), 1234567890123L);
    assertEquals(copy.getUserId(), 4711L);
    assertEquals(copy.getUserClassId(), 815);
    assertEquals(copy.toString(), auth.toString());

    assertTrue(copy.getCredentials() instanceof PasswordCredentials);
    PasswordCredentials credentials = (PasswordCredentials) copy.getCredentials();
    assertNotSame(credentials, auth.getCredentials());
    assertEquals(credentials.getUserName(), "hugo");
    assertEquals(credentials.getPassword(), "secret".toCharArray());

    // the copy is still modifiable
    copy.getAttributes().put("more", "value");
    copy.getRoles().add("guest");
    assertEquals(copy.getRoles().size(), 3);
  }

  @Test
  public void testTokenAuthentication() {
    TokenCredentials token = new TokenCredentials("access".toCharArray());
    token.setIssuer("https://issuer.example.com");
    token.setExpiresAt(987654321L);
    DefaultAuthentication auth = new DefaultAuthentication(null, token);

    DefaultAuthentication copy = tunnel(auth);
    assertNotSame(copy, auth);
    assertNull(copy.getName());
    assertTrue(copy.getAttributes().isEmpty());
    assertTrue(copy.getRoles().isEmpty());
    assertNull(copy.getProviderName());
    assertEquals(copy.getExpiresAt(), 0L);
    assertEquals(copy.getUserId(), 0L);
    assertEquals(copy.getUserClassId(), 0);

    assertTrue(copy.getCredentials() instanceof TokenCredentials);
    TokenCredentials credentials = (TokenCredentials) copy.getCredentials();
    assertEquals(credentials.getScheme(), TokenCredentials.SCHEME);
    assertEquals(credentials.getTokenType(), TokenCredentials.DEFAULT_TOKEN_TYPE);
    assertEquals(credentials.getToken(), "access".toCharArray());
    assertEquals(credentials.getIssuer(), "https://issuer.example.com");
    assertEquals(credentials.getExpiresAt(), 987654321L);
  }

}
