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

/**
 * A QUIC {@code CONNECTION_CLOSE} error, as reported by {@code quiche_conn_local_error}.
 *
 * @param isApp true if {@code errorCode} is an application-defined error code, false if it is a QUIC
 *        transport error code (which is where TLS alerts, including a rejected certificate, show up)
 * @param errorCode the error code; see
 *        <a href="https://www.rfc-editor.org/rfc/rfc9000.html#section-20.1">RFC 9000 &sect;20.1</a> for
 *        the transport error codes and
 *        <a href="https://www.rfc-editor.org/rfc/rfc9001.html#section-4.8">RFC 9001 &sect;4.8</a> for how
 *        a TLS alert is encoded among them ({@code 0x100 + alert-number})
 * @param reason the reason phrase, possibly empty, never null
 */
public record ConnectionError(boolean isApp, long errorCode, String reason) {
}
