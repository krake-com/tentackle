/*
 * Tentackle - https://tentackle.org.
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

package org.tentackle.model.parse;

import org.tentackle.model.ModelException;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * The attribute definition line.
 * <p>
 * An attribute line is defined as follows:
 * <pre>
 * javaType[&lt;innerType&gt;][(size[,scale])] javaName columnName [comment] [\[options\]]
 * </pre>
 *
 * Where:
 * <ul>
 * <li>javaType - is the Java class name or primitive. Allowed types are:
 *   <ul>
 *     <li>any type known to the {@link org.tentackle.sql.DataTypeFactory}, such as the primitives,
 *     their wrappers, String, BigDecimal, BMoney, DMoney, Binary, the java.sql and java.time types, UUID or I18NText</li>
 *     <li>an application-specific type with an inner type (see {@code org.tentackle.misc.Convertible})</li>
 *   </ul>
 * </li>
 * <li>innerType: optional inner/generic type</li>
 * <li>size: optional number of columns or width or non-fractional digits if numeric.
 * May be separated from the type by whitespace.</li>
 * <li>scale: optional scale (numeric types only)</li>
 * <li>javaName: the name used in Java sources (must start lowercase)</li>
 * <li>columnName: the database column name</li>
 * <li>comment: the optional comment</li>
 * <li>[options]: the options enclosed in square brackets. Options beginning with an '@' are treated as annotations,
 * options beginning with a '#' as stereotypes. Brackets and parentheses may be escaped by a backslash.</li>
 * </ul>
 * <p>
 * Examples:
 * <pre>
 * long             subsidiaryId    subsidiary_id     subsidiary ID [context]
 * String(10)       invoiceNo       invoice_no        the unique invoice number [uc]
 * Date             invoiceDate     invoice_date      the invoice's date
 * long             customerId      customer_id       object ID of the customer [uc]
 * String(200)      address         address           the address (multiline) [default 'blah']
 * DMoney           total           total             sum (without tax)
 * DMoney           tax             tax               tax (vat)
 * Timestamp        printed         printed           first printed
 * Double(10,2)     someDouble      some_double       digits with scale
 * MyType&lt;int&gt;      extra           extra             some application-specific type
 * </pre>
 *
 * @author harald
 */
public class AttributeLine extends SingleLine {

  /** Java model type. */
  private String javaType;

  /** additional generic/application-specific type. */
  private String innerType;

  /** limited size (e.g. for varchars). */
  private Integer size;

  /** scale (only for numerics). */
  private Integer scale;

  /** Java attribute name. */
  private String javaName;

  /** column name in database table. */
  private String columnName;

  /** description. */
  private String comment;

  /** options. */
  private List<String> options;



  /**
   * Creates an attribute line.<br>
   * This line always spans only one line.
   *
   * @param document the whole document
   * @param offset offset to first character within document
   * @param lineType the line type
   */
  public AttributeLine(Document document, int offset, LineType lineType) {
    super(document, offset, lineType);
  }

  @Override
  public void parse() throws ModelException {
    super.parse();

    String text = getText();

    StringTokenizer stok = new StringTokenizer(text, " \t");
    StringBuilder obuf = new StringBuilder(); // comment and option buffer
    StringBuilder ssBuf = null;               // size and/or scale buffer (in case (size,scale) contains blanks or is separated by blanks)

    // parse tokens
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (javaType == null) {
        extractJavaType(token);
      }
      else if (javaName == null) {
        if (ssBuf == null && token.charAt(0) == '(') {
          // size and/or scale separated from the type by whitespaces
          ssBuf = new StringBuilder();
        }
        if (ssBuf != null) {
          // collect until the closing parenthesis, which may be within this or one of the following tokens
          ssBuf.append(token);
          if (ssBuf.charAt(ssBuf.length() - 1) == ')') {
            extractSizeAndOrScale(ssBuf.substring(1, ssBuf.length() - 1));
            ssBuf = null;
          }
        }
        else {
          javaName = token;
        }
      }
      else if (columnName == null) {
        columnName = token;
      }
      else {
        // comment and options
        obuf.append(' ').append(token);
      }
    }

    if (javaType == null || javaName == null || columnName == null) {
      throw createModelException("syntax error");
    }

    parseCommentAndOptions(obuf.toString());
  }




  /**
   * Gets the Java type name.
   *
   * @return the Java type name, never null
   * @throws ModelException if not parsed
   */
  public String getJavaType() throws ModelException {
    assertParsed();
    return javaType;
  }

  /**
   * Gets the inner type name.
   *
   * @return the inner type, null if regular type
   * @throws ModelException if not parsed
   */
  public String getInnerType() throws ModelException {
    assertParsed();
    return innerType;
  }

  /**
   * Gets the size.
   *
   * @return the size, null if not set
   * @throws ModelException if not parsed
   */
  public Integer getSize() throws ModelException {
    assertParsed();
    return size;
  }

  /**
   * Gets the numeric scale.
   *
   * @return the scale, null if not set
   * @throws ModelException if not parsed
   */
  public Integer getScale() throws ModelException {
    assertParsed();
    return scale;
  }

  /**
   * Gets the Java attribute name.
   *
   * @return the Java name
   * @throws ModelException if not parsed
   */
  public String getJavaName() throws ModelException {
    assertParsed();
    return javaName;
  }

  /**
   * Gets the column name in database table.
   *
   * @return the column name
   * @throws ModelException if not parsed
   */
  public String getColumnName() throws ModelException {
    assertParsed();
    return columnName;
  }

  /**
   * Gets the comment.
   *
   * @return the comment, never null
   * @throws ModelException if not parsed
   */
  public String getComment() throws ModelException {
    assertParsed();
    return comment;
  }

  /**
   * Gets the options.
   *
   * @return the options (never null)
   * @throws ModelException if not parsed
   */
  public List<String> getOptions() throws ModelException {
    assertParsed();
    return options;
  }



  /**
   * Parses the comment and options.
   *
   * @param text the comment and options text
   */
  private void parseCommentAndOptions(String text) {
    StringBuilder commentBuf = new StringBuilder();
    OptionParser parser = new OptionParser(text, commentBuf);
    options = new ArrayList<>();
    String option;
    while ((option = parser.nextOption()) != null) {
      options.add(option);
    }
    comment = commentBuf.toString().trim();
  }


  /**
   * Extracts the Java type, optional inner type and optional size/scale.
   *
   * @param token the token
   * @throws ModelException if parsing failed
   */
  private void extractJavaType(String token) throws ModelException {
    if (token != null && !token.isEmpty()) {
      int genBegin = token.indexOf('<');
      if (genBegin > 0) {
        // generic type
        int genEnd = token.lastIndexOf('>');    // may be nested types e.g. for Binary
        if (genEnd > genBegin) {
          javaType = token.substring(0, genBegin);
          innerType = token.substring(genBegin + 1, genEnd);
          int sizeBegin = token.indexOf('(');
          if (sizeBegin > 0) {
            // size and/or scale for inner type
            int sizeEnd = token.indexOf(')');
            if (sizeEnd > sizeBegin) {
              extractSizeAndOrScale(token.substring(sizeBegin + 1, sizeEnd));
            }
            else {
              throw createModelException("mangled inner type with size and/or scale '" + token + "'");
            }
          }
        }
        else {
          throw createModelException("mangled generic Java type '" + token + "'");
        }
      }
      else {
        int sizeBegin = token.indexOf('(');
        if (sizeBegin > 0) {
          // size and/or scale
          int sizeEnd = token.indexOf(')');
          if (sizeEnd > sizeBegin) {
            javaType = token.substring(0, sizeBegin);
            extractSizeAndOrScale(token.substring(sizeBegin + 1, sizeEnd));
          }
          else {
            throw createModelException("mangled type with size and/or scale '" + token + "'");
          }
        }
        else  {
          // neither generic type nor size or scale
          javaType = token;
        }
      }
    }
    else {
      // cannot happen: StringTokenizer never returns empty tokens
      throw createModelException("Java type is empty");
    }
  }


  /**
   * Extracts the size and/or scale from a string.
   *
   * @param str the string
   * @throws ModelException if parsing failed
   */
  private void extractSizeAndOrScale(String str) throws ModelException {
    int scaleBegin = str.indexOf(',');
    if (scaleBegin > 0) {
      // size and scale
      try {
        String sizeStr = str.substring(0, scaleBegin).trim();
        size = sizeStr.isEmpty() ? 0 : Integer.parseInt(sizeStr);
      }
      catch (NumberFormatException rex) {
        throw createModelException("cannot determine size from '" + str + "'", rex);
      }
      try {
        String scaleStr = str.substring(scaleBegin+1).trim();
        scale = scaleStr.isEmpty() ? 0 : Integer.parseInt(scaleStr);
      }
      catch (NumberFormatException rex) {
        throw createModelException("cannot determine scale from '" + str + "'", rex);
      }
    }
    else  {
      try {
        size = Integer.valueOf(str.trim());
      }
      catch (NumberFormatException rex) {
        throw createModelException("cannot determine size from '" + str + "'", rex);
      }
    }
  }

}
