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


package org.tentackle.wurblet;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.tentackle.common.StringHelper;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelHelper;
import org.wurbelizer.wurblet.AbstractWurblet;


/**
 * Base class for the {@code @DTO}-wurblet.
 * <p>
 * Parses the simple model.
 *
 * @author harald
 */
public class DTOWurblet extends AbstractWurblet {

  /**
   * DTO property.
   */
  protected static class Property {

    private final String type;        // the java type
    private final String name;        // the property name
    private final String comment;     // the comment
    private final boolean inherited;  // true if inherited

    public Property(String type, String name, String comment) {
      if (type.startsWith("^")) {
        inherited = true;
        this.type = type.substring(1);
      }
      else {
        this.type = type;
        inherited = false;
      }
      this.name = name;
      this.comment = comment == null || comment.isEmpty() ? "the " + name + " property" : comment;
    }

    public boolean isInherited() {
      return inherited;
    }

    public String getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public String getComment() {
      return comment;
    }

    public String getGetterName() {
      return "get" + StringHelper.firstToUpper(name);
    }
  }



  protected String filename;            // filename of the here-doc holding the model (usually given as "$> .$filename")
  protected List<Property> properties;  // the DTO properties


  public DTOWurblet() {
    super();
  }

  @Override
  public void run() throws WurbelException {
    super.run();

    for (String arg: getContainer().getArgs())  {
      filename = arg;
      break;
    }

    if (filename == null) {
      throw new WurbelException("usage: @wurblet <guardname> DTO <filename>");
    }

    properties = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(WurbelHelper.openReader(filename))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!StringHelper.isAllWhitespace(line) && !line.startsWith("#")) {
          StringTokenizer stok = new StringTokenizer(line);
          String type = null;
          String name = null;
          StringBuilder comment = new StringBuilder();
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            if (type == null) {
              type = token;
            }
            else if (name == null) {
              name = token;
            }
            else {
              if (comment.length() > 0) {
                comment.append(' ');
              }
              comment.append(token);
            }
          }
          if (type == null || name == null) {
            throw new WurbelException("property line too short: '" + line + "'");
          }
          properties.add(new Property(type, name, comment.toString()));
        }
      }
    }
    catch (IOException ex) {
      throw new WurbelException("including " + filename + " failed", ex);
    }
  }

}
