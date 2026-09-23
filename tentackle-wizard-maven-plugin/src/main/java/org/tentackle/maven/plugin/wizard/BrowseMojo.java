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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.maven.plugin.wizard.fx.BrowseContainer;
import org.tentackle.maven.plugin.wizard.pdodata.JavaCodeFactory;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;
import org.tentackle.sql.datatypes.AbstractDataType;

import javafx.application.Platform;
import javafx.stage.Stage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.CountDownLatch;

/**
 * PDO browser.<br>
 * Brings up an FX UI to analyze PDOs persisted in a database
 * and generate test code.
 */
@Mojo(name = "browse", aggregator = true)
public class BrowseMojo extends AbstractWizardMojo {

  /**
   * The backend URL.
   */
  @Parameter(required = true)
  protected String url;

  /**
   * The backend username.
   */
  @Parameter(required = true)
  public String user;

  /**
   * The backend password.
   */
  @Parameter
  public String password;

  /**
   * Limits the number of lines in generated multi-line string literals.
   */
  @Parameter(property = "maxLines")
  public int maxLinesInStringLiteral;

  /**
   * Map schema names to flat table names in generated SQL SELECT statements.
   */
  @Parameter(property = "tentackle.mapSchemas")
  public boolean mapSchemas;

  /**
   * The PDOs to generate code for.<br>
   * Turns on batch mode in conjunction with the {@code output} parameter.
   * <p>
   * The format is: {@code -Dpdo=<entity>[id[,id,...]]}, where:
   * <ul>
   *   <li>{@code entity} is the entity name, for example {@code Customer}
   *   <li>{@code id} is one or more object IDs, separated by commas.
   * </ul>
   * Example: {@code -Dpdo=Customer[17878,221221,333]}
   */
  @Parameter(property = "pdo")
  private String pdoConfig;

  /**
   * The generated output file.<br>
   * Turns on batch mode in conjunction with the {@code pdo} parameter.
   */
  @Parameter(property = "output")
  private File outputFile;

  /**
   * By default, the variables of the created entities are reused for the
   * 2nd, 3rd, etc... instances. If this flag is true, the variable names
   * are numbered starting at {@link #variableOffset}.
   */
  @Parameter(property = "distinct")
  private boolean distinctVariables;

  /**
   * If {@link #distinctVariables} is true, the variable names are numbered.
   * If {@code variableOffset} is null, the first instance has no number.
   * Otherwise, it starts with the given offset.
   */
  @Parameter(property = "offset")
  private Integer variableOffset;


  private Stage stage;
  private BrowseContainer browseContainer;


  @Override
  @SuppressWarnings("try")
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    try (LayerScope ignored = openLayerScope()) {
      executeLayered();
    }
  }

  private void executeLayered() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();
    AbstractDataType.maxLinesInLiteral = maxLinesInStringLiteral;
    EncryptedProperties props = PropertiesUtilities.getInstance().create();
    props.setProperty(org.tentackle.common.Constants.BACKEND_URL, url);
    props.setProperty(org.tentackle.common.Constants.BACKEND_USER, user);
    props.setProperty(Constants.BACKEND_PASSWORD, password);
    SessionInfo sessionInfo = Pdo.createSessionInfo(props);
    boolean isBatchMode = pdoConfig != null && outputFile != null;
    try {
      Session session = Pdo.createSession(sessionInfo);
      try {
        DomainContext context = Pdo.createDomainContext(session);
        ModificationTracker pdoTracker = ModificationTracker.getInstance();
        pdoTracker.setSession(session.clone("MT"));
        pdoTracker.start();   // caches need a running tracker (also in batch mode!)

        JavaCodeFactory.distinctVariables = distinctVariables;
        JavaCodeFactory.variableOffset = variableOffset;

        if (isBatchMode) {
          int ndx = pdoConfig.indexOf('[');
          if (ndx >= 0 && pdoConfig.endsWith("]")) {
            String entityName = pdoConfig.substring(0, ndx);
            String ids = pdoConfig.substring(ndx + 1, pdoConfig.length() - 1);
            List<Long> idList = new ArrayList<>();
            StringTokenizer stok = new StringTokenizer(ids, ", ");
            while (stok.hasMoreTokens()) {
              String idStr = stok.nextToken();
              try {
                idList.add(Long.valueOf(idStr));
              }
              catch (NumberFormatException nx) {
                throw new MojoExecutionException("invalid ID: " + idStr);
              }
            }
            if (idList.isEmpty()) {
              throw new MojoExecutionException("no IDs specified");
            }

            new BatchPdoGenerator(this, context, entityName, idList, outputFile).generate();
          }
          else {
            throw new MojoExecutionException("invalid pdo parameter: " + pdoConfig);
          }
        }
        else {
          CountDownLatch latch = new CountDownLatch(1);
          Platform.startup(() -> {
            try {
              configureFx();
              registerUncaughtExceptionHandler();
              session.makeCurrent();
              browseContainer = new BrowseContainer(this, context);
              stage = browseContainer.show();
              stage.setOnHidden(_ -> latch.countDown());
            }
            catch (Throwable t) {
              getLogger().error(t.getMessage(), t);
              latch.countDown();
            }
          });

          try {
            latch.await();
          }
          catch (InterruptedException e) {
            throw new InterruptedRuntimeException(e);
          }
        }
      }
      catch (PersistenceException ex) {
        // no database? wrong database? whatever: testing environment incomplete
        throw new MojoExecutionException("no backend found", ex);
      }
      finally {
        if (session != null) {
          ModificationTracker.getInstance().terminate();
          session.close();
        }
      }
    }
    catch (PersistenceException ex) {
      throw new MojoExecutionException("failed to connect to the backend", ex);
    }
  }


  @Override
  protected Stage getStage() {
    return stage;
  }

  @Override
  protected void loadModel() throws MojoFailureException, MojoExecutionException {
    if (mapSchemas) {
      getModel().setSchemaNameMapped(true);
    }
    super.loadModel();
  }

}
