/*
 * Tentackle - http://www.tentackle.org
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

import java.io.Serializable;
import java.text.ParseException;
import java.util.Objects;
import java.util.Properties;
import java.util.StringTokenizer;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.misc.ParameterString;


/**
 * Common implementation for {@link SocketConfigurator} and {@link ServerSocketConfigurator}.
 * <p>
 * The implementation allows to parse the configuration for both client- and server-sockets.
 * However, equals() and hashcode() check only those parameters that really apply to the concrete configurator.
 * Same for getters/setters.
 *
 * @author harald
 */
public abstract class AbstractSocketConfigurator implements Serializable {

  private static final long serialVersionUID = 1L;


  // ------------------- common to all sockets ----------------------

  /** property key for SO_REUSEADDR. */
  public static final String SO_REUSEADDR = "REUSEADDR";

  /** property key for SO_TIMEOUT. */
  public static final String SO_TIMEOUT = "TIMEOUT";

  /** property key for performance preferences. */
  public static final String PERF_PREFS = "PERFPREFS";


  // ------------------- client sockets only ----------------------

  /** property key for SO_KEEPALIVE. */
  public static final String SO_KEEPALIVE = "KEEPALIVE";

  /** property key for SO_OOBINLINE. */
  public static final String SO_OOBINLINE = "OOBINLINE";

  /** property key for TCP_NODELAY. */
  public static final String TCP_NODELAY = "NODELAY";

  /** property key for SO_SNDBUF. */
  public static final String SO_SNDBUF = "SNDBUF";

  /** property key for SO_LINGER. */
  public static final String SO_LINGER = "LINGER";

  /** property key for IP_TOS. */
  public static final String IP_TOS = "TOS";


  // ------------------- server sockets only ----------------------

  /** property key for SO_RCVBUF. */
  public static final String SO_RCVBUF = "RCVBUF";

  /** property key for port. */
  public static final String PORT = "PORT";

  /** property key for port range. */
  public static final String RANGE = "RANGE";



  // socket settings, null if don't configure.
  protected Boolean reuseAddress;
  protected Integer soTimeout;
  protected SocketPerformancePreferences performancePreferences;

  protected Boolean keepAlive;
  protected Boolean oobInline;
  protected Boolean tcpNoDelay;
  protected Integer sendBufferSize;
  protected Integer soLinger;         // -1 to turn linger off
  protected Integer trafficClass;

  protected Integer receiveBufferSize;
  protected int port;                 // fixed port
  protected int portRange;            // automatic port increase if already bound


  /**
   * Gets SO_REUSEADDR.
   *
   * @return null if do not configure
   */
  public Boolean getReuseAddress() {
    return reuseAddress;
  }

  /**
   * Sets SO_REUSEADDR.
   *
   * @param reuseAddress null if do not configure
   */
  public void setReuseAddress(Boolean reuseAddress) {
    this.reuseAddress = reuseAddress;
  }


  /**
   * Gets SO_TIMEOUT.
   *
   * @return null if do not configure
   */
  public Integer getSoTimeout() {
    return soTimeout;
  }

  /**
   * Sets SO_TIMEOUT.
   *
   * @param timeout null if do not configure
   */
  public void setSoTimeout(Integer timeout) {
    this.soTimeout = timeout;
  }


  /**
   * Sets the performance preferences.
   *
   * @return null if do not configure
   */
  public SocketPerformancePreferences getPerformancePreferences() {
    return performancePreferences;
  }

  /**
   * Gets the performance preferences.'
   *
   * @param performancePreferences null if do not configure
   */
  public void setPerformancePreferences(SocketPerformancePreferences performancePreferences) {
    this.performancePreferences = performancePreferences;
  }



  /**
   * Applies the parameters from a property list.
   *
   * @param properties the properties
   */
  public void applyProperties(Properties properties) {
    if (properties != null) {
      for (String key: properties.stringPropertyNames()) {
        switch (key.toUpperCase()) {

          case SO_REUSEADDR:
            reuseAddress = Boolean.valueOf(properties.getProperty(key));
            break;

          case SO_TIMEOUT:
            soTimeout = Integer.decode(properties.getProperty(key));
            break;

          case PERF_PREFS:
            // provided as a triple positive integer of the form nn,nn,nn
            String prefs = properties.getProperty(key);
            StringTokenizer stok = new StringTokenizer(prefs, ", ");
            if (stok.countTokens() != 3) {
              throw new TentackleRuntimeException("expected 'n,n,n', got '" + prefs + "'");
            }
            performancePreferences = new SocketPerformancePreferences(
                    Integer.decode(stok.nextToken()),
                    Integer.decode(stok.nextToken()),
                    Integer.decode(stok.nextToken()));
            break;


          case SO_KEEPALIVE:
            keepAlive = Boolean.valueOf(properties.getProperty(key));
            break;

          case SO_OOBINLINE:
            oobInline = Boolean.valueOf(properties.getProperty(key));
            break;

          case TCP_NODELAY:
            tcpNoDelay = Boolean.valueOf(properties.getProperty(key));
            break;

          case SO_SNDBUF:
            sendBufferSize = Integer.decode(properties.getProperty(key));
            break;

          case SO_LINGER:
            soLinger = Integer.decode(properties.getProperty(key));
            break;

          case IP_TOS:
            trafficClass = Integer.decode(properties.getProperty(key));
            break;


          case SO_RCVBUF:
            receiveBufferSize = Integer.decode(properties.getProperty(key));
            break;

          case RANGE:
            portRange = Integer.decode(properties.getProperty(key));
            break;

          case PORT:
            port = Integer.decode(properties.getProperty(key));
            break;

          default:
            throw new TentackleRuntimeException("illegal socket option: " + key);
        }
      }
    }
  }


  /**
   * Applies the parameters from a single string.
   *
   * @param parameters the parameter string
   * @see ParameterString
   */
  public void applyParameters(String parameters) {
    try {
      applyProperties(new ParameterString(parameters).toProperties());
    }
    catch (ParseException pex) {
      throw new TentackleRuntimeException("applying parameters failed", pex);
    }
  }


  /**
   * Returns whether this configurator contains valid configurations.
   *
   * @return true if options valid
   */
  public boolean isValid() {
    return reuseAddress != null || soTimeout != null || performancePreferences != null;
  }


  @Override
  public int hashCode() {
    if (isValid()) {
      int hash = 5;
      hash = 11 * hash + Objects.hashCode(reuseAddress);
      hash = 11 * hash + Objects.hashCode(soTimeout);
      hash = 11 * hash + Objects.hashCode(performancePreferences);
      return hash;
    }
    // treat invalid socket configurator as if there is no configurator at all
    return 0;   // see Objects.hashCode()
  }


  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      // this is invalid and other is null -> logically equal
      return !isValid();
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AbstractSocketConfigurator other = (AbstractSocketConfigurator) obj;
    if (!Objects.equals(reuseAddress, other.reuseAddress)) {
      return false;
    }
    if (!Objects.equals(soTimeout, other.soTimeout)) {
      return false;
    }
    return Objects.equals(performancePreferences, other.performancePreferences);
  }


  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (reuseAddress != null) {
      buf.append(", ").append(SO_REUSEADDR).append('=').append(reuseAddress);
    }
    if (soTimeout != null) {
      buf.append(", ").append(SO_TIMEOUT).append('=').append(soTimeout);
    }
    if (performancePreferences != null) {
      buf.append(", ").append(PERF_PREFS).append('=').append(performancePreferences);
    }
    return buf.toString();
  }

}
