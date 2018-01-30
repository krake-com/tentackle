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


package org.tentackle.swing.plaf.tlooks;

import org.tentackle.common.Service;
import org.tentackle.swing.plaf.TentackleLookAndFeel;

/**
 * Cross platform TLooks with a pleasent blue color theme.
 * 
 * @author harald
 */
@SuppressWarnings("serial")
@Service(TentackleLookAndFeel.class)
public class TLooksBlueLookAndFeel extends TLooksLookAndFeel {
  
  /** Creates a new instance of TLooksBlueLookAndFeel */
  public TLooksBlueLookAndFeel() {
    super();
  }
  
  
  @Override
  protected void createDefaultTheme() {
    setCurrentTheme(new TLooksBlueTheme());
  }
  
  
  public static String getTLooksBlueName() {
      return "TLooks-Blue";
  }

  public static String getTLooksBlueDescription() {
      return "Enhanced Blue JGoodies LnF for Tentackle";
  }  
  
  @Override
  public String getName() {
      return getTLooksBlueName();
  }
  

  @Override
  public String getDescription() {
      return getTLooksBlueDescription();
  }
  
}
