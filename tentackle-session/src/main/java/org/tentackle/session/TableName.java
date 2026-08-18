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

package org.tentackle.session;

import org.tentackle.common.Analyze;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for the tablename of a PDO.
 * <p>
 * The tablename can be modified by the options:
 * <ul>
 * <li>mapSchema: if true, dots will be replaced by underscores</li>
 * <li>prefix: prepends the given string to each tablename</li>
 * </ul>
 *
 * Those options can be provided as maven properties such as:
 * <pre>
      &lt;wurbletProperties&gt;
              &lt;tablePrefix&gt;pls&lt;/tablePrefix&gt;
              ...
   </pre>
 *
 * and in the code:
 *
 * <pre>
   &#64;TableName(value=&#47;*&#64;*&#47;"md.ingotcategory"&#47;*&#64;*&#47;,    // &#64;wurblet &lt; Inject --string $tablename
           mapSchema=&#47;*&#64;*&#47;false&#47;*&#64;*&#47;,     // @wurblet &lt; Inject $mapSchema
           prefix=&#47;*&#64;*&#47;""&#47;*&#64;*&#47;)       // @wurblet &lt; Inject --string $tablePrefix
  </pre>
 *
 * Means: all schemas get "pls" prepended (for example, "td.message" becomes "plstd.message").
 * Non-schema tables from the Tentackle framework (technical tables) remain in the default schema.
 * This configuration is particularly useful for databases like Oracle that
 * emulate schemas via extra users. As a result, the main user "pls" remains unchanged;
 * transaction data goes to the "schema-user" plstd while the master data goes to plsmd.
 * The model, however, remains unchanged! It's just a configuration in pom.xml.<p>
 * With mapSchema=true and prefix="" all tables go to the default schema, while
 * master data tables start with md_ and transaction data with td_.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Analyze("org.tentackle.buildsupport.TableNameAnalyzeHandler")
public @interface TableName {

  /**
   * Determines the tablename.
   *
   * @return the tablename
   */
  String value();

  /**
   * Determines whether schemas are allowed or mapped to simple table names.<br>
   * If true, table names like "md.blah" are mapped to "md_blah".
   * This is especially useful for databases that don't support schemas
   * or provide only quirky workarounds (Oracle, MySQL, for example).
   *
   * @return true if map to non-schema name
   */
  boolean mapSchema() default false;

  /**
   * Adds a prefix to the tablename or schema.
   *
   * @return the optional prefix
   */
  String prefix() default "";

}
