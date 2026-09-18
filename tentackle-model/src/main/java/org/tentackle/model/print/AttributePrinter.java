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

package org.tentackle.model.print;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.ModelException;
import org.tentackle.sql.DataType;

/**
 * Pretty printer for attribute lines.
 *
 * @author harald
 */
public class AttributePrinter {

  private final Attribute attribute;
  private final PrintConfiguration configuration;
  private String printedType;
  private String printedComment;

  /**
   * Creates an attribute printer.
   *
   * @param attribute the attribute
   * @param configuration the printing configuration
   */
  public AttributePrinter(Attribute attribute, PrintConfiguration configuration) {
    this.attribute = attribute;
    this.configuration = configuration;
  }

  /**
   * Prints the attribute's type.
   *
   * @return the type
   */
  public String printType() {
    if (printedType == null) {
      StringBuilder buf = new StringBuilder();
      try {
        DataType<?> dataType = attribute.getDataType();
        if (dataType.getModelType().equals(dataType.getJavaType())) {
          buf.append(attribute.getJavaType());
        }
        else {
          buf.append(dataType.getModelType());
        }
        if (attribute.isConvertible()) {
          buf.append('<').append(attribute.getInnerTypeName()).append('>');
        }
        if (attribute.getSize() != null || attribute.getScale() != null) {
          buf.append('(');
          if (attribute.getSize() != null) {
            buf.append(attribute.getSize());
          }
          if (attribute.getScale() != null) {
            buf.append(',').append(attribute.getScale());
          }
          buf.append(')');
        }
      }
      catch (ModelException mx) {
        buf.append('<').append(mx.getMessage()).append('>');
      }
      printedType = buf.toString();
    }
    return printedType;
  }

  /**
   * Prints the attribute's java name.
   *
   * @return the name
   */
  public String printJavaName() {
    return attribute.getName();
  }

  /**
   * Prints the attribute's column name.
   *
   * @return the column
   */
  public String printColumnName() {
    return attribute.getColumnName();
  }

  /**
   * Prints the attribute's comment string.
   *
   * @return the comment
   */
  public String printComment() {
    if (printedComment == null) {
      StringBuilder buf = new StringBuilder();
      if (!StringHelper.isAllWhitespace(attribute.getOptions().getComment())) {
        buf.append(attribute.getOptions().getComment());
      }
      String options = new AttributeOptionsPrinter(attribute, configuration).print();
      if (!options.isEmpty()) {
        buf.append(" [").append(options).append(']');
      }
      printedComment = buf.toString();
    }
    return printedComment;
  }

}
