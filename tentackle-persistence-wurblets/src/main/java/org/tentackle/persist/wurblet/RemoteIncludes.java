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


package org.tentackle.persist.wurblet;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.wurblet.ModelWurblet;
import org.wurbelizer.wurbel.HeapStream;
import org.wurbelizer.wurbel.WurbelException;
import org.wurbelizer.wurbel.Wurbler;


/**
 * Handles generated .remote and .impl-code.
 * <p>
 * Also creates the RMI-java files, if missing.
 *
 * @author harald
 */
public class RemoteIncludes {

  private final Wurbler container;            // the executing container
  private Entity entity;                      // the wurblet's entity
  private String className;                   // classname of persistence object
  private String pdoClassName;                // classname of the pdo interface
  private String packageName;                 // package of the class
  private String pdoPackageName;              // package name of the pdo interface
  private String superClassName;              // name of the superclass
  private String rmiDirName;                  // directory of rmi-classes
  private String remotePrefix;                // prefix of the remote parent class (usually from ant script)
  private final String remotePrefixBasename;  // basename of remotePrefix
  private boolean importSuperDelegate;        // create import for supercass/interface of remote delegate
  private final HeapStream remoteStream;      // stream for remote file
  private final HeapStream implStream;        // stream for impl file



  /**
   * Create remote includes.
   *
   * @param wurblet the wurblet
   * @throws WurbelException if failed
   */
  public RemoteIncludes(ModelWurblet wurblet) throws WurbelException {

    container       = wurblet.getContainer();
    className       = wurblet.getClassName();
    entity          = wurblet.getEntity();
    superClassName  = StringHelper.getPlainClassName(wurblet.getSuperClassName());
    packageName     = wurblet.getPackageName();
    remotePrefix    = container.getProperty(Wurbler.PROPSPACE_EXTRA, "remoteprefix");

    try {
      pdoClassName    = wurblet.getPdoClassName();
    }
    catch (WurbelException ex) {
      // only needed for initial class file generation below
    }

    if (remotePrefix == null) {
      // if not defined, set the default
      pdoPackageName = packageName.replace(".persistence.", ".pdo.").replace(".persist.", ".pdo.");
      switch (superClassName) {
        case "AbstractPersistentObject":
          remotePrefix = "org.tentackle.persist.rmi." + superClassName;
          importSuperDelegate = true;
          break;

        case "AbstractDbObject":
          remotePrefix = "org.tentackle.persist.rmi." + superClassName;
          importSuperDelegate = true;
          break;

        default:
          remotePrefix = superClassName;
          if (pdoClassName != null && className.startsWith(pdoClassName)) {
            // subtract from classname to get the tail, something like "PersistenceImpl" or alike
            String tail = className.substring(pdoClassName.length());
            if (tail.length() > 0 && remotePrefix.endsWith(tail)) {
              remotePrefix = remotePrefix.substring(0, remotePrefix.length() - tail.length());
            }
          }
      }
    }

    int ndx = remotePrefix.lastIndexOf('.');
    remotePrefixBasename = ndx < 0 ? remotePrefix : remotePrefix.substring(ndx + 1);

    // find directory for remote delegates
    rmiDirName = container.getProperty(Wurbler.PROPSPACE_WURBLET, Wurbler.WURBLET_DIRNAME);
    rmiDirName += "/rmi";

    // create remote stream and file
    String remoteName = (pdoClassName != null ? pdoClassName : className) + "RemoteDelegate";
    remoteStream = new HeapStream(container, remoteName + "/methods");
    checkFile(remoteName, false);

    // create impl stream and file
    implStream = new HeapStream(container, remoteName + "Impl/methods");
    checkFile(remoteName, true);
  }


  /**
   * Marks the heapfiles as discarded.<br>
   * Such files should not be used to generate code because their contents are incomplete
   * because of former errors. Wurblets reading this file should throw a {@link org.wurbelizer.wurbel.WurbelDiscardException}.
   */
  public void discard() {
    implStream.discard();
    remoteStream.discard();
  }


  /**
   * Check if remote file exists. If not, create it.
   */
  private void checkFile(String name, boolean impl) throws WurbelException {
    // check directory first
    new File(rmiDirName).mkdirs();
    String fileName = name + (impl ? "Impl" : "") + ".java";
    String pathName = rmiDirName + "/" + fileName;
    File file = new File(pathName);
    if (!file.exists()) {
      if (pdoClassName != null) {
        // create it
        try (PrintStream s = new PrintStream(file)) { // this is ok unbuffered for one time
          s.println(
                "/**\n" +
                " * " + fileName + " (generated " + new Date() + ")\n" +
                " */\n\n" +
                "package " + packageName + ".rmi;\n\n" +
                "import " + pdoPackageName + "." + pdoClassName + ";\n" +
                "import " + packageName + "." + className + ";\n" +
                "import java.rmi.RemoteException;\n" +
                "import java.util.List;\n" +
                "import org.tentackle.common.BMoney;\n" +
                "import org.tentackle.common.Binary;\n" +
                "import org.tentackle.common.DMoney;\n" +
                "import org.tentackle.common.Date;\n" +
                "import org.tentackle.common.Time;\n" +
                "import org.tentackle.common.Timestamp;\n" +
                "import org.tentackle.misc.TrackedList;\n" +
                "import org.tentackle.persist.rmi.RemoteResultSetCursor;"
            );
          s.println("import org.tentackle.pdo.DomainContext;");
          if (impl) {
            if (importSuperDelegate) {
              s.println("import " + remotePrefix + "RemoteDelegateImpl;");
            }
            s.println("import org.tentackle.dbms.rmi.RemoteDbSessionImpl;\n\n");
            s.println("/**");
            s.println(" * Remote delegate implementation for {@link " + className + "}.");
            if (entity.isAbstract()) {
              s.println(" * ");
              s.println(" * @param <T> the pdo interface");
              s.println(" * @param <P> the persistence implementation");
            }
            s.println(" */");
            if (entity.isAbstract()) {
              s.println("public class " + name + "Impl<T extends " + pdoClassName + "<T>, P extends " + className + "<T,P>>");
              s.println("       extends " + remotePrefixBasename + "RemoteDelegateImpl<T,P>");
              s.println("       implements " + name + "<T,P>  {\n\n");
            }
            else  {
              s.println("public class " + name + "Impl");
              s.println("       extends " + remotePrefixBasename + "RemoteDelegateImpl<" + pdoClassName + "," + className + ">");
              s.println("       implements " + name + " {\n\n");
            }
            s.println("  /**");
            s.println("   * Creates the remote delegate for {@link " + className + "}.");
            s.println("   *");
            s.println("   * @param session the RMI session");
            s.println("   * @param persistenceClass the persistence implementation class");
            s.println("   * @param pdoClass the pdo interface class");
            s.println("   * @throws java.rmi.RemoteException");
            s.println("   */");
            if (entity.isAbstract()) {
              s.println("  public " + name + "Impl(RemoteDbSessionImpl session, Class<P> persistenceClass, Class<T> pdoClass) throws RemoteException {");
            }
            else  {
              s.println("  public " + name + "Impl(RemoteDbSessionImpl session, Class<" + className +
                        "> persistenceClass, Class<" + pdoClassName + "> pdoClass) throws RemoteException {");
            }
            s.println("    super(session, persistenceClass, pdoClass);");
            s.println("  }");
          }
          else  {
            if (importSuperDelegate) {
              s.println("import " + remotePrefix + "RemoteDelegate;");
            }
            s.println("\n\n/**");
            s.println(" * Remote delegate for {@link " + className + "}.");
            if (entity.isAbstract()) {
              s.println(" * ");
              s.println(" * @param <T> the pdo interface");
              s.println(" * @param <P> the persistence implementation");
            }
            s.println(" */");
            if (entity.isAbstract()) {
              s.println("public interface " + name + "<T extends " + pdoClassName + "<T>, P extends " + className + "<T,P>>");
              s.println("       extends " + remotePrefixBasename + "RemoteDelegate<T,P> {\n");
            }
            else  {
              s.println("public interface " + name);
              s.println("       extends " + remotePrefixBasename + "RemoteDelegate<" + pdoClassName + "," + className + "> {\n");
            }
          }
          s.println(
                "  /**\n" +
                "   * Include wurblet-generated method declarations.\n" +
                "   *\n" +
                "   * @wurblet inclrmi Include --missingok .$classname/methods\n" +
                "   */\n" +
                "}"
          );
        }
        catch (IOException ex) {
          throw new WurbelException("printing to " + pathName + " failed", ex);
        }
      }
      else if (superClassName != null && superClassName.equals("AbstractDbObject")) {
        // primitive AbstractDbObjects
        try (PrintStream s = new PrintStream(file)) { // this is ok unbuffered for one time
          s.println(
                "/**\n" +
                " * " + fileName + "\n" +
                " *\n" +
                " */\n\n" +
                "package " + packageName + ".rmi;\n\n" +
                "import " + packageName + "." + className + ";\n" +
                "import java.rmi.RemoteException;\n" +
                "import java.util.List;\n" +
                "import org.tentackle.persist.AbstractDbObject;\n" +
                "import org.tentackle.misc.TrackedList;"
            );
          if (impl) {
            if (importSuperDelegate) {
              s.println("import " + remotePrefix + "RemoteDelegateImpl;");
            }
            s.println("import org.tentackle.persist.rmi.RemoteDbSessionImpl;\n\n");
            s.println("/**");
            s.println(" * Remote delegate implementation for {@link " + className + "}.");
            s.println(" * ");
            s.println(" * @param <P> the persistence implementation");
            s.println(" */");
            s.println("public class " + name + "Impl<P extends " + className + ">");
            s.println("             extends " + remotePrefixBasename + "RemoteDelegateImpl<P>");
            s.println("             implements " + name + "<P>  {\n\n");
            s.println("  /**");
            s.println("   * Creates the remote delegate for {@link " + className + "}.");
            s.println("   *");
            s.println("   * @param session the RMI session");
            s.println("   * @param persistenceClass the persistence implementation class");
            s.println("   * @throws java.rmi.RemoteException");
            s.println("   */");
            s.println("  public " + name + "Impl(RemoteDbSessionImpl session, Class<P> persistenceClass) throws RemoteException {");
            s.println("    super(session, persistenceClass);");
            s.println("  }");
          }
          else  {
            if (importSuperDelegate) {
              s.println("import " + remotePrefix + "RemoteDelegate;");
            }
            s.println("\n\n/**");
            s.println(" * Remote delegate for {@link " + className + "}.");
            s.println(" * ");
            s.println(" * @param <P> the persistence implementation");
            s.println(" */");
            s.println("public interface " + name + "<P extends " + className + ">");
            s.println("       extends " + remotePrefixBasename + "RemoteDelegate<P> {\n");
          }
          s.println(
                "  /**\n" +
                "   * Include wurblet-generated method declarations.\n" +
                "   *\n" +
                "   * @wurblet inclrmi Include --missingok .$classname/methods\n" +
                "   */\n" +
                "}"
          );
        }
        catch (IOException ex) {
          throw new WurbelException("printing to " + pathName + " failed", ex);
        }
      }
      else  {
        container.getLogger().warning(pathName + " cannot be generated because pdo class is unknown (missing @PersistenceObjectService?)");
      }
    }
  }



  /**
   * Appends text to remote file.
   *
   * @param text the text to append
   */
  public void appendRemote(String text) {
    remoteStream.append(text);
  }

  /**
   * Appends text to impl file.
   *
   * @param text the text to append
   */
  public void appendImpl(String text) {
    implStream.append(text);
  }

  /**
   * @return get the remote stream
   */
  public PrintStream getRemoteStream()  {
    return remoteStream.getStream();
  }

  /**
   * @return the impl stream
   */
  public PrintStream getImplStream()  {
    return implStream.getStream();
  }

}
