/**
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


package org.tentackle.misc;

import java.util.Hashtable;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;

/**
 * EmailAddress address.
 * <p>
 * Verifies an email address (syntax check via regex)
 * and optionally validates the DNS MX records.
 *
 * @author harald
 */
public class EmailAddress {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(EmailAddress.class);

  private boolean valid;          // true if mail address is valid
  private String error;           // error message, null = ok
  private String recipient;       // recipient's name
  private String domain;          // recipient's domain


  /**
   * Creates an empty mail address
   */
  public EmailAddress() {
    clear();
  }


  /**
   * Clears the mail address
   */
  public void clear() {
    valid = false;
    error = null;
    recipient = null;
    domain = null;
  }


  /**
   * Sets the mail address and verifies it.
   *
   * @param address the mail address
   * @param withDomainCheck true if check domainname for DNS MX-records
   * @return true if mail address is ok, false if not valid
   */
  public boolean setAddress(String address, boolean withDomainCheck) {
    clear();
    if (address == null || address.isEmpty()) {
      error = "email address is empty";
    }
    else if (Pattern.compile(
              "^[_A-Za-z0-9-]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*$",
              Pattern.CASE_INSENSITIVE).matcher(address).matches()) {
      // syntactically okay
      int ndx = address.indexOf('@');   // must work!
      recipient = address.substring(0, ndx);
      domain = address.substring(ndx + 1);
      valid = true;
      if (withDomainCheck) {
        try {
          // check that domain exists
          Hashtable<String,String> env = new Hashtable<>();
          env.put(Context.INITIAL_CONTEXT_FACTORY,
                  "com.sun.jndi.dns.DnsContextFactory");
          DirContext dnsCtx = new InitialDirContext(env);
          Attributes attribs = dnsCtx.getAttributes(domain, new String[]{"MX"});
          LOGGER.fine("{0} verified: {1}", address, attribs);
        }
        catch (NamingException ex) {
          error = ex.getMessage();
          valid = false;
          LOGGER.fine("{0} invalid: {1}", address, error);
        }
      }
    }
    else  {
      error = "malformed email address";
    }

    return valid;
  }


  /**
   * Sets the mail address and verifies it.
   * No MX lookup.
   *
   * @param address the mail address
   * @return true if mail address is ok, false if not valid
   */
  public boolean setAddress(String address) {
    return setAddress(address, false);
  }


  /**
   * Gets the mail address as a string.
   *
   * @return the mail address as a string
   */
  public String getAddress() {
    return recipient + "@" + domain;
  }

  @Override
  public String toString() {
    return getAddress();
  }


  /**
   * Returns whether mail address is valid
   * @return true if valid, else invalid or empty
   */
  public boolean isValid() {
    return valid;
  }

  /**
   * Gets the error message.
   *
   * @return the error message, null if none
   */
  public String getError() {
    return error;
  }

  /**
   * Sets the error message.
   *
   * @param error  the error message, null if none
   */
  public void setError(String error) {
    this.error = error;
  }
}
