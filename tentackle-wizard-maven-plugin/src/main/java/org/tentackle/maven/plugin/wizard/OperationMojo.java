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

import freemarker.template.TemplateException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.fx.Fx;
import org.tentackle.maven.PackageInfo;
import org.tentackle.maven.plugin.wizard.fx.OperationWizard;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;

/**
 * Wizard to create operation files.<br>
 * Brings up an FX UI to create the operation related Java files.
 * <p>
 * If {@code operationName} is set, the wizard works in batch mode and generates the files
 * for that single operation without opening the UI. Since operations are not part of the model,
 * all values the UI would ask for must be given as parameters, for example:
 * <pre>
 * mvn tentackle-wizard:operation -DoperationName=SendInvoice -Dcomment="sends an invoice"
 * </pre>
 * <p>
 * Best invoked from within the directory of the maven parent of the project.
 */
@Mojo(name = "operation", aggregator = true)
public class OperationMojo extends AbstractWizardMojo {

  /**
   * The value denoting an explicitly missing class.
   */
  private static final String NONE = "-";

  /**
   * The name of the operation.<br>
   * If set, the wizard works in batch mode and generates the files for that operation
   * instead of opening the UI.
   */
  @Parameter(property = "operationName")
  private String operationName;

  /**
   * The name of the {@link OperationProfile} to use in batch mode.<br>
   * Matched case-insensitively. Can be omitted if there is only one profile configured.
   */
  @Parameter(property = "profile")
  private String profileName;

  /**
   * The short description of the operation.<br>
   * Mandatory in batch mode.
   */
  @Parameter(property = "comment")
  private String shortDescription;

  /**
   * The long description of the operation.
   */
  @Parameter(property = "longComment")
  private String longDescription;

  /**
   * Whether remoting is supported.<br>
   * Defaults to the {@code remote} option of the project's model defaults.
   */
  @Parameter(property = "remote")
  private Boolean remoteEnabled;

  /**
   * The name of the super operation.<br>
   * Determines all super types of the generated classes.
   * If not set, the super types are taken from the profile or the framework defaults.
   */
  @Parameter(property = "superOperation")
  private String superOperation;

  /**
   * Whether an abstract operation should be generated.
   */
  @Parameter(property = "abstract")
  private boolean abstractOperation;

  /**
   * The name of the domain interface.<br>
   * Defaults to the name derived from the operation name.
   * An empty value or {@value #NONE} generates no domain interface and no domain implementation.
   */
  @Parameter(property = "domainInterface")
  private String domainInterface;

  /**
   * The name of the persistence interface.<br>
   * Defaults to the name derived from the operation name.
   * An empty value or {@value #NONE} generates no persistence interface, no persistence
   * implementation and disables remoting.
   */
  @Parameter(property = "persistenceInterface")
  private String persistenceInterface;

  /**
   * The name of the domain implementation.<br>
   * Defaults to the name derived from the operation name.
   * An empty value or {@value #NONE} generates the domain interface only.
   */
  @Parameter(property = "domainImplementation")
  private String domainImplementation;

  /**
   * The name of the persistence implementation.<br>
   * Defaults to the name derived from the operation name.
   * An empty value or {@value #NONE} generates the persistence interface only.
   */
  @Parameter(property = "persistenceImplementation")
  private String persistenceImplementation;

  /**
   * Whether existing files may be overwritten in batch mode.<br>
   * By default, the goal fails if one of the files to generate already exists,
   * because regenerating it would destroy the business logic written into it.
   */
  @Parameter(property = "overwrite")
  private boolean overwrite;


  private List<OperationProfile> operationProfiles;

  private Stage stage;

  @Override
  protected Stage getStage() {
    return stage;
  }

  public List<OperationProfile> getOperationProfiles() {
    return operationProfiles;
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();

    if (operationName != null) {
      generateFromParameters();
    }
    else {
      // interactive mode
      CountDownLatch latch = new CountDownLatch(1);

      Platform.startup(() -> {
        try {
          configureFx();
          registerUncaughtExceptionHandler();
          OperationWizard wizard = Fx.load(OperationWizard.class);
          wizard.applyMojo(this);
          stage = Fx.createStage(Modality.APPLICATION_MODAL, null);
          Scene scene = Fx.createScene(wizard.getView());
          stage.setScene(scene);
          stage.setTitle("Operation Wizard");
          wizard.getContainer().updateView();
          stage.setOnHidden(event -> latch.countDown());
          Fx.show(stage);
        }
        catch (Throwable t) {
          getLog().error(t);
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

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      operationProfiles = getProfiles(OperationProfile.class);
      if (operationProfiles.isEmpty()) {
        throw new MojoExecutionException("at least one OperationProfile must be configured");
      }
      Map<String, PackageInfo> packageInfoMap = createPackageMap(false);
      for (OperationProfile profile : operationProfiles) {
        profile.validate(packageInfoMap);
      }
      return true;
    }
    return false;
  }


  /**
   * Generates the code for the operation described by the parameters.
   *
   * @throws MojoExecutionException if the parameters are invalid or code generation failed
   * @throws MojoFailureException if the operation is not valid or a file exists already
   */
  private void generateFromParameters() throws MojoExecutionException, MojoFailureException {
    String name = blankToNull(operationName);
    if (name == null) {
      throw new MojoExecutionException("operationName must not be empty");
    }
    OperationProfile profile = selectProfile(operationProfiles, profileName);
    getLog().info("generating code for operation " + name + " using profile " + profile);

    OperationGenerator gen = new OperationGenerator(getModel());
    gen.setProfile(profile);
    gen.setTemplateDirectory(new File(getTemplateDir(), Constants.CATEGORY_OPERATION));
    // sets the derived names of the interfaces and implementations and the remote default
    gen.setOperationName(name);
    gen.setSuperOperationName(blankToNull(superOperation));
    gen.setAbstractOperation(abstractOperation);
    gen.setShortDescription(blankToNull(shortDescription));
    gen.setLongDescription(blankToNull(longDescription));
    // the interfaces first: dropping an interface drops its implementation as well
    if (domainInterface != null) {
      gen.setDomainInterface(noneToNull(domainInterface));
    }
    if (persistenceInterface != null) {
      gen.setPersistenceInterface(noneToNull(persistenceInterface));
    }
    if (domainImplementation != null) {
      gen.setDomainImplementation(noneToNull(domainImplementation));
    }
    if (persistenceImplementation != null) {
      gen.setPersistenceImplementation(noneToNull(persistenceImplementation));
    }
    if (remoteEnabled != null) {
      // last, since dropping the persistence interface disables remoting
      gen.setRemoteEnabled(remoteEnabled);
    }

    if (gen.getDomainInterface() != null && gen.getDomainImplementation() == null) {
      getLog().warn("no domain implementation will be generated for " + name);
    }
    if (gen.getPersistenceInterface() != null && gen.getPersistenceImplementation() == null) {
      getLog().warn("no persistence implementation will be generated for " + name);
    }

    if (!overwrite) {
      StringJoiner existingFiles = new StringJoiner("\n");
      for (File file: gen.getTargetFiles()) {
        if (file.exists()) {
          existingFiles.add(file.getAbsolutePath());
        }
      }
      if (existingFiles.length() > 0) {
        throw new MojoFailureException("the following files exist already:\n" + existingFiles +
                                       "\nuse -Doverwrite=true to regenerate them");
      }
    }

    try {
      List<ValidationResult> results = gen.generate();
      if (!results.isEmpty()) {
        throw new MojoFailureException(ValidationUtilities.getInstance().resultsToMessage(results));
      }
    }
    catch (IOException | TemplateException ex) {
      throw new MojoExecutionException("code generation failed for operation " + name, ex);
    }

    for (File file: gen.getTargetFiles()) {
      getLog().info("generated " + file.getAbsolutePath());
    }
  }


  /**
   * Selects the profile by its name.
   *
   * @param profiles the configured profiles, never empty
   * @param name the profile name, null if the only configured profile should be used
   * @return the profile
   * @throws MojoExecutionException if there is no such profile or the name is missing
   */
  static OperationProfile selectProfile(List<OperationProfile> profiles, String name) throws MojoExecutionException {
    if (name == null) {
      if (profiles.size() > 1) {
        throw new MojoExecutionException("more than one OperationProfile configured: " +
                                         profileNames(profiles) + " -> please select one via -Dprofile=...");
      }
      return profiles.getFirst();
    }
    for (OperationProfile profile: profiles) {
      if (profile.getName() != null && profile.getName().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
        return profile;
      }
    }
    throw new MojoExecutionException("no such OperationProfile: " + name +
                                     " -> configured profiles are: " + profileNames(profiles));
  }

  /**
   * Creates a printable list of the profile names.
   *
   * @param profiles the profiles
   * @return the comma separated names
   */
  private static String profileNames(List<OperationProfile> profiles) {
    StringJoiner names = new StringJoiner(", ");
    for (OperationProfile profile: profiles) {
      names.add(String.valueOf(profile.getName()));
    }
    return names.toString();
  }

  /**
   * Converts a blank string to null.
   *
   * @param value the parameter value, may be null
   * @return the trimmed value, null if not given
   */
  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  /**
   * Converts a blank string or {@value #NONE} to null.<br>
   * Used for parameters where an explicitly empty value means that the corresponding
   * file should not be generated at all.
   *
   * @param value the parameter value, may be null
   * @return the trimmed value, null if not given or {@value #NONE}
   */
  static String noneToNull(String value) {
    String trimmedValue = blankToNull(value);
    return NONE.equals(trimmedValue) ? null : trimmedValue;
  }
}
