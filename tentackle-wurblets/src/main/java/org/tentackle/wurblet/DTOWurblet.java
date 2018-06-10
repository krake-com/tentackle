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
 * Parses wurblet args and the simple model.
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
    private final boolean mutable;    // true if with setter instead of final

    public Property(String type, String name, String comment) {
      if (type.startsWith("^")) {
        inherited = true;
        mutable = false;
        this.type = type.substring(1);
      }
      else if (type.startsWith("=")) {
        mutable = true;
        inherited = false;
        this.type = type.substring(1);
      }
      else {
        this.type = type;
        inherited = false;
        mutable = false;
      }
      this.name = name;
      this.comment = comment == null || comment.isEmpty() ? "the " + name + " property" : comment;
    }

    public boolean isInherited() {
      return inherited;
    }

    public boolean isMutable() {
      return mutable;
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
      return ("boolean".equals(type) ? "is" : "get") + StringHelper.firstToUpper(name);
    }

    public String getSetterName() {
      return "set" + StringHelper.firstToUpper(name);
    }

  }



  protected String filename;            // filename of the here-doc holding the model (usually given as "$> .$filename")
  protected List<Property> properties;  // the DTO properties
  protected boolean needConstructor;    // true if constructor necessary
  protected boolean withBuilder;        // use the builder pattern
  protected String extendsWithBuilder;  // only if builder pattern: super classname (must be with builder as well)


  public DTOWurblet() {
    super();
  }

  @Override
  public void run() throws WurbelException {
    super.run();

    for (String arg: getContainer().getArgs()) {
      if ("--builder".equals(arg)) {
        withBuilder = true;
      }
      else if (filename == null && !arg.startsWith("--")) {
        filename = arg;
      }
    }

    if (filename == null) {
      throw new WurbelException("usage: @wurblet <guardname> DTO [--builder] <filename>");
    }

    if (withBuilder) {
      try {
        extendsWithBuilder = getSuperClassName();
      }
      catch (WurbelException wx) {
        // ok: no superclass
      }
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
          Property property = new Property(type, name, comment.toString());
          if (!property.isMutable()) {
            needConstructor = true;
          }
          properties.add(property);
        }
      }
    }
    catch (IOException ex) {
      throw new WurbelException("reading model " + filename + " failed", ex);
    }
  }

}
