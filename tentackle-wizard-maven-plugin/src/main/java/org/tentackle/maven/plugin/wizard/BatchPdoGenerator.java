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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.common.Settings;
import org.tentackle.common.Timestamp;
import org.tentackle.maven.plugin.wizard.pdodata.CodeConfigurationFactory;
import org.tentackle.maven.plugin.wizard.pdodata.DataNodeFactory;
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.maven.plugin.wizard.pdodata.JavaCodeFactory;
import org.tentackle.misc.FormatHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PersistentDomainObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Generates PDOs in batch mode.
 */
public class BatchPdoGenerator {

  private final BrowseMojo mojo;
  private final DomainContext context;
  private final String entityName;
  private final List<Long> idList;
  private final File outputFile;
  private final DataNodeFactory nodeFactory;

  /**
   * Creates a new batch generator.
   *
   * @param mojo the browse mojo
   * @param context the domain context
   * @param entityName the entity name
   * @param idList the list of entity IDs
   * @param outputFile the output file
   */
  public BatchPdoGenerator(BrowseMojo mojo, DomainContext context, String entityName, List<Long> idList, File outputFile) {
    this.mojo = mojo;
    this.context = context;
    this.entityName = entityName;
    this.idList = idList;
    this.outputFile = outputFile;

    nodeFactory = new DataNodeFactory(mojo.getModel());
  }

  /**
   * Generates the Java code.
   *
   * @throws MojoExecutionException if failed
   */
  public void generate() throws MojoExecutionException {
    context.getSession().makeCurrent();
    outputFile.getParentFile().mkdirs();
    try {
      try (FileWriter codeWriter = new FileWriter(outputFile, Settings.getEncodingCharset())) {
        codeWriter.append("// batch generated at ").append(FormatHelper.formatShortTimestamp(new Timestamp())).append(" by ")
                  .append(System.getProperty("user.name"))
                  .append('@')
                  .append(mojo.getHostName())
                  .append(" from ")
                  .append(context.getSession().toString()).append('\n');

        for (String pdoName : PdoFactory.getInstance().getPersistenceMapper().getMap().keySet()) {
          String className = pdoName;
          int ndx = pdoName.lastIndexOf('.');
          if (ndx >= 0) {
            pdoName = pdoName.substring(ndx + 1);
          }
          if (pdoName.equals(entityName)) {
            PersistentDomainObject<?> proxy = PdoFactory.getInstance().create(className, context);
            DataObject[] dataObjects = new DataObject[idList.size()];
            int i = 0;
            for (Long id : idList) {
              PersistentDomainObject<?> pdo = proxy.on().select(id);
              if (pdo == null) {
                throw new MojoExecutionException("entity " + entityName + " with ID " + id + " not found in database");
              }
              dataObjects[i++] = nodeFactory.create(pdo);
            }
            Map<String, Boolean> codePaths = CodeConfigurationFactory.createConfig(dataObjects);
            codeWriter.append(JavaCodeFactory.generateCode(codePaths, dataObjects));
            break;
          }
        }
      }
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write to " + outputFile, iox);
    }
  }

}
