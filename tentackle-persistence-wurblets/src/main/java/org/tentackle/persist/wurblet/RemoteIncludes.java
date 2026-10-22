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

package org.tentackle.persist.wurblet;

import freemarker.template.TemplateException;
import org.wurbelizer.misc.Constants;
import org.wurbelizer.wurbel.HeapStream;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.WurbelTerminationException;
import org.wurbelizer.wurbel.Wurbler;

import org.tentackle.buildsupport.codegen.AbstractGenerator;
import org.tentackle.buildsupport.codegen.GeneratedFile;
import org.tentackle.buildsupport.codegen.TemplateModel;
import org.tentackle.common.NamingRules;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.wurblet.ModelWurblet;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.regex.Pattern;

/**
 * Handles generated remote interface and implementation code.
 * <p>
 * Creates the TRIP-java files from freemarker templates, if missing.
 *
 * @author harald
 */
public class RemoteIncludes {

  /** template parameters and variables. */
  private final class TemplatePars {

    private final TemplateModel model;              // the template model (for pdo or operation)
    private final AbstractGenerator generator;      // the template generator

    private TemplatePars() throws WurbelException {

      NamingRules namingRules = NamingRules.getInstance();
      String popPackage = wurblet.determinePackageName(popInterface); // package name of the pdo or operation interface

      String superPackage;              // package name of the remote super interface
      String superImplPackage;          // package name of the remote super implementation
      String superInterface;            // name of the remote super interface
      String superImplementation;       // name of the remote super implementation

      switch (superClassName) {
        case "AbstractPersistentObject":
          superInterface = "AbstractPersistentObjectRemoteDelegate";
          superPackage = "org.tentackle.persist.trip";
          superImplementation = "AbstractPersistentObjectRemoteDelegateImpl";
          superImplPackage = "org.tentackle.persist.trip";
          break;

        case "AbstractPersistentOperation":
          superInterface = "AbstractPersistentOperationRemoteDelegate";
          superPackage = "org.tentackle.persist.trip";
          superImplementation = "AbstractPersistentOperationRemoteDelegateImpl";
          superImplPackage = "org.tentackle.persist.trip";
          break;

        case "AbstractDbObject":
          // just convenience for TT-builds...
          superInterface = "AbstractDbObjectRemoteDelegate";
          superPackage = "org.tentackle.dbms.trip";
          superImplementation = "AbstractDbObjectRemoteDelegateImpl";
          superImplPackage = "org.tentackle.dbms.trip";
          break;

        default:
          String superJavaClassName = StringHelper.getPlainClassName(wurblet.getSuperClassName());
          int dotNdx = superJavaClassName.lastIndexOf('.');
          String superClassPackageName = dotNdx > 0 ?
                                         superJavaClassName.substring(0, dotNdx) :   // fully qualified
                                         wurblet.determinePackageName(superJavaClassName);
          if (wurblet.isOperation()) {
            superInterface = namingRules.getOperationRemoteInterface(superClassName);
            superPackage = namingRules.getOperationRemoteInterfacePackageName(superClassPackageName);
            superImplementation = namingRules.getOperationRemoteImplementation(superClassName);
            superImplPackage = namingRules.getOperationRemoteImplementationPackageName(superClassPackageName);
          }
          else {
            superInterface = namingRules.getPdoRemoteInterface(superClassName);
            superPackage = namingRules.getPdoRemoteInterfacePackageName(superClassPackageName);
            superImplementation = namingRules.getPdoRemoteImplementation(superClassName);
            superImplPackage = namingRules.getPdoRemoteImplementationPackageName(superClassPackageName);
          }
      }

      model = new TemplateModel();
      if (wurblet.isOperation()) {
        model.putValue("operationInterface", popInterface);
        model.putValue("operationPackage", popPackage);
        String sourceText = wurblet.getContainer().getProperty(Wurbler.PROPSPACE_WURBLET, Wurbler.WURBLET_FILECONTENTS);
        model.putValue("abstractOperation",
                       sourceText != null &&
                       Pattern.compile("\\s+extends\\s+" + popInterface + "\\s*<").matcher(sourceText).find());
      }
      else {
        model.putValue("pdoInterface", popInterface);
        model.putValue("pdoPackage", popPackage);
        model.putValue("pdoInheritance", entity != null ? entity.getInheritanceType() : "");
      }
      model.putValue("persistenceImplementation", className);
      model.putValue("persistenceImplPackage", packageName);
      model.putValue("remoteInterface", remoteInterface);
      model.putValue("remotePackage", remotePackage);
      model.putValue("remoteImplementation", remoteImplementation);
      model.putValue("remoteImplPackage", remoteImplPackage);
      model.putValue("superInterface", superInterface);
      model.putValue("superPackage", superPackage);
      model.putValue("superImplementation", superImplementation);
      model.putValue("superImplPackage", superImplPackage);

      String templateDir = wurblet.getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "templateDir");
      if (templateDir == null) {
        String projectRoot = wurblet.getContainer().getProperty(Wurbler.PROPSPACE_EXTRA, "projectRoot");
        if (projectRoot == null) {
          throw new WurbelTerminationException("neither the WurbletProperty \"templateDir\" nor \"projectRoot\" were set.");
        }
        templateDir = projectRoot + File.separator + "templates" +
                      File.separator + (wurblet.isOperation() ? "operation" : "pdo");
      }
      generator = new AbstractGenerator() {};
      generator.setTemplateDirectory(new File(templateDir));
    }
  }

  private final ModelWurblet wurblet;             // the model wurblet
  private final Entity entity;                    // the entity associated to the wurblet
  private final String className;                 // classname of persistence object
  private final String packageName;               // package of the persistence implementation class
  private final String superClassName;            // name of the superclass
  private final HeapStream interfaceStream;       // stream for remote interface wurblet generated code
  private final HeapStream implementationStream;  // stream for remote implementation wurblet generated code

  private final String popInterface;              // classname of the pdo or operation interface
  private final String remotePackage;             // package name of the remote interface
  private final String remoteInterface;           // classname of the remote interface
  private final String remoteImplPackage;         // package name of the remote implementation
  private final String remoteImplementation;      // classname of the remote implementation

  private TemplatePars templatePars;              // template variables (null if not created yet)

  /**
   * Create remote includes.
   *
   * @param wurblet the wurblet
   * @throws WurbelException if failed
   */
  public RemoteIncludes(ModelWurblet wurblet) throws WurbelException {

    this.wurblet = wurblet;

    className = wurblet.getClassName();
    entity = wurblet.getEntity();
    superClassName = entity != null && entity.getSuperEntityName() != null ?
                       entity.getSuperEntityName() :
                       StringHelper.getPlainClassName(wurblet.getSuperClassName());
    packageName = wurblet.getPackageName();
    popInterface = determinePopInterface(wurblet);

    NamingRules namingRules = NamingRules.getInstance();
    Wurbler container = wurblet.getContainer();

    if (wurblet.isOperation()) {
      remotePackage = namingRules.getOperationRemoteInterfacePackageName(packageName);
      remoteInterface = namingRules.getOperationRemoteInterface(popInterface);
      remoteImplPackage = namingRules.getOperationRemoteImplementationPackageName(packageName);
      remoteImplementation = namingRules.getOperationRemoteImplementation(popInterface);
    }
    else {
      remotePackage = namingRules.getPdoRemoteInterfacePackageName(packageName);
      remoteInterface = namingRules.getPdoRemoteInterface(popInterface);
      remoteImplPackage = namingRules.getPdoRemoteImplementationPackageName(packageName);
      remoteImplementation = namingRules.getPdoRemoteImplementation(popInterface);
    }

    // find directory for remote delegates (must be in the same module as the persistence implementation!)
    String dirName = container.getProperty(Wurbler.PROPSPACE_WURBLET, Wurbler.WURBLET_DIRNAME);
    String packagePath = packageName.replace('.', File.separatorChar);
    int ndx = dirName.indexOf(packagePath);
    String pathPrefix = ndx > 0 ? dirName.substring(0, ndx) : "";

    String remoteDirName = pathPrefix + File.separator + remotePackage.replace('.', File.separatorChar);
    String remoteImplDirName = pathPrefix + File.separator + remoteImplPackage.replace('.', File.separatorChar);

    // create remote stream and file
    interfaceStream = new HeapStream(container, remoteInterface + "/methods");
    checkFile(remoteDirName, remoteInterface, "RemoteInterface.ftl");

    // create impl stream and file
    implementationStream = new HeapStream(container, remoteImplementation + "/methods");
    checkFile(remoteImplDirName, remoteImplementation, "RemoteImplementation.ftl");
  }

  /**
   * Marks the heap files as discarded.<br>
   * Such files should not be used to generate code because their contents are incomplete
   * because of former errors. Wurblets reading this file should throw a {@link org.wurbelizer.wurbel.WurbelDiscardException}.
   */
  public void discard() {
    implementationStream.discard();
    interfaceStream.discard();
  }

  /**
   * Gets the print stream of the remote interface.
   *
   * @return get the remote stream
   */
  public PrintStream getInterfaceStream()  {
    return interfaceStream.getStream();
  }

  /**
   * Gets the print stream of the remote implementation.
   *
   * @return the impl stream
   */
  public PrintStream getImplementationStream()  {
    return implementationStream.getStream();
  }


  /**
   * Determines the interface name of the PDO or operation.
   *
   * @param wurblet the model wurblet from within the persistence implementation java file
   * @return the proxy interface
   * @throws WurbelException if fallback classname could not be determined
   */
  private String determinePopInterface(ModelWurblet wurblet) throws WurbelException {
    String popInterface = null;
    try {
      popInterface = wurblet.getPdoClassName();
    }
    catch (WurbelException ex) {
      // run into fallback below
    }
    if (popInterface == null) {
      popInterface = wurblet.getClassName();   // fallback if simple object
    }
    return popInterface;
  }

  /**
   * Check if remote file exists. If not, generate it.
   */
  private void checkFile(String dirName, String remoteName, String templateName) throws WurbelException {
    File dir = new File(dirName);
    File file = new File(dir, remoteName + Constants.JAVA_SOURCE_EXTENSION);
    if (!file.exists()) {
      // generate it with freemarker
      dir.mkdirs();
      if (templatePars == null) {
        templatePars = new TemplatePars();
      }
      try {
        new GeneratedFile(templatePars.generator.createFreemarkerConfiguration(),
                          templatePars.model, templateName, file).generate();
      }
      catch (IOException | TemplateException e) {
        throw new WurbelTerminationException("cannot generate " + file + " from " + templateName, e);
      }
    }
  }

}
