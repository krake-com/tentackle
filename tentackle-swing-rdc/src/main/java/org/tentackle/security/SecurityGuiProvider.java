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


package org.tentackle.security;

import org.tentackle.security.pdo.Security;
import org.tentackle.swing.rdc.DefaultGuiProvider;
import org.tentackle.swing.rdc.GuiProviderService;

/**
 * GUI provider for {@link Security} rules.
 *
 * @author harald
 */
@GuiProviderService(Security.class)
public class SecurityGuiProvider extends DefaultGuiProvider<Security> {

  /**
   * Creates a GUI provider for a security rule.
   *
   * @param pdo the security rule
   */
  public SecurityGuiProvider(Security pdo) {
    super(pdo);
  }

  @Override
  public SecurityTableEntry getFormTableEntry() {
    return new SecurityTableEntry(getPdo());
  }

  @Override
  public String getFormTableName()  {
    return "securityTable";
  }

}
