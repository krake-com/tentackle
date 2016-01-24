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


package org.tentackle.pdo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import org.tentackle.app.AbstractApplication;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.misc.StringHelper;
import org.tentackle.misc.UserHomeJavaDir;


/**
 * A file-based backup store for {@link PdoCache}.<br>
 * Serializes the cache to the filesystem to improve client startup times,
 * especially for preloading caches.<br>
 * Makes only sense for remote client applications.
 *
 * @param <T> the PDO type
 * @author harald
 */
public class PdoCacheFileStore<T extends PersistentDomainObject<T>> implements Serializable {

  private static final long serialVersionUID = 1029177241051535961L;

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(PdoCacheFileStore.class);

  private static final String CACHE_FILE_EXTENSION = ".ser";  // extension for cache files


  private transient PdoCache<T> cache;    // the cache to backup

  // persisted data
  private String applicationName;         // the application name (null if file-backup disabled, i.e. not a client application)
  private String serverName;              // the name of the backend
  private final String fileName;          // the filename
  private long tableSerial;               // the serial version of the table
  private List<T> objects;                // the objects in the cache



  /**
   * Creates a file store for a cache.
   *
   * @param cache the cache
   */
  @SuppressWarnings("unchecked")
  public PdoCacheFileStore(PdoCache<T> cache) {
    this.cache = cache;
    AbstractApplication application = AbstractApplication.getRunningApplication();
    if (application instanceof SessionProvider) {
      Session session = ((SessionProvider) application).getSession();
      applicationName = StringHelper.toAsciiLetterOrDigit(application.getName());
      if (session != null && session.isRemote()) {
        serverName = StringHelper.toAsciiLetterOrDigit(session.getRemoteSession().getServerName());
      }
    }
    fileName = StringHelper.toAsciiLetterOrDigit(cache.getObjectClass().getName()) + CACHE_FILE_EXTENSION;
  }


  /**
   * Writes the cache to persistent storage.<br>
   *
   * @return true if cache has been persited, false if cache isn't persistable (application is not a client)
   * @throws PdoRuntimeException if writing failed
   */
  public boolean writeToFile() throws PdoRuntimeException {

    if (isPersistable()) {

      initializeForWrite();
      File file = getFile();
      ObjectOutputStream os = null;

      try {
        os = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        os.writeObject(this);
        if (LOGGER.isInfoLoggable()) {
          LOGGER.info(objects.size() + " objects written to " + file.getAbsolutePath());
        }
        return true;
      }
      catch (IOException ioe) {
        throw new PdoRuntimeException("cannot write to file " + file.getAbsolutePath(), ioe);
      }
      finally {
        try {
          if (os != null) {
            os.close();
          }
        }
        catch (IOException ioe) {
          throw new PdoRuntimeException("cannot close file " + file.getAbsolutePath(), ioe);
        }
      }
    }
    else  {
      return false;
    }
  }


  /**
   * Loads a cache from persistent storage.<br>
   *
   * @param session the session
   * @return true if loaded from persistent storage or data was missing,
   *         false if cache isn't persistable (application is not a client)
   * @throws PdoRuntimeException if reading failed
   */
  public boolean readFromFile(Session session) throws PdoRuntimeException {

    if (isPersistable()) {

      File file = getFile();
      ObjectInputStream is = null;

      try {
        is = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)));
        Object object = is.readObject();
        if (object instanceof PdoCacheFileStore) {
          @SuppressWarnings("unchecked")
          PdoCacheFileStore<T> cacheFileStore = (PdoCacheFileStore<T>) object;
          cacheFileStore.cache = cache;
          cacheFileStore.verifyAfterRead();
          cacheFileStore.applyToCache(session);
          if (LOGGER.isInfoLoggable()) {
            LOGGER.info(cache.getObjects().size() + " objects loaded from " + file.getAbsolutePath());
          }
          return true;
        }
        else  {
          throw new PdoRuntimeException("file " + file.getAbsolutePath()  + " does not contain cache data! Found: " + object.getClass().getName());
        }
      }
      catch (ClassNotFoundException cnfe) {
        throw new PdoRuntimeException("cannot load java class from file " + file.getAbsolutePath(), cnfe);
      }
      catch (FileNotFoundException fnfe) {
        // this is okay: no such file -> nothing loaded
        return true;
      }
      catch (IOException ioe) {
        throw new PdoRuntimeException("cannot read from file " + file.getAbsolutePath(), ioe);
      }
      finally {
         try {
          if (is != null) {
            is.close();
          }
        }
        catch (IOException ioe) {
          throw new PdoRuntimeException("cannot close file " + file.getAbsolutePath(), ioe);
        }
      }
    }
    else  {
      return false;
    }
  }



  /**
   * Determines whether cache is persistable.
   *
   * @return true if persistable
   */
  private boolean isPersistable() {
    return applicationName != null && fileName != null;
  }

  /**
   * Initializes the cache data for being written to storage.
   */
  private synchronized void initializeForWrite() {
    // build cache data (no check for key change)
    objects = cache.getObjects(false);
    // get current table serial
    tableSerial = getTableSerial();
  }

  /**
   * Verifies the cache data after being read from storage.
   *
   * @throws PdoRuntimeException
   */
  private void verifyAfterRead() throws PdoRuntimeException {
    String expectedApplicationName = null;
    String expectedServerName = null;
    AbstractApplication application = AbstractApplication.getRunningApplication();
    if (application != null) {
      expectedApplicationName = StringHelper.toAsciiLetterOrDigit(application.getName());
      if (application instanceof SessionProvider) {
        Session session = ((SessionProvider) application).getSession();
        if (session.isRemote()) {
          expectedServerName = StringHelper.toAsciiLetterOrDigit(session.getRemoteSession().getServerName());
        }
      }
    }

    String expectedFileName = StringHelper.toAsciiLetterOrDigit(cache.getObjectClass().getName()) + CACHE_FILE_EXTENSION;

    if (!Objects.equals(expectedApplicationName, applicationName)) {
      throw new PdoRuntimeException("cache data belongs to application '" + applicationName +
              "', expected (running) application is '" + expectedApplicationName + "'");
    }

    if (!Objects.equals(expectedServerName, serverName)) {
      throw new PdoRuntimeException("cache data belongs to backend '" + serverName +
              "', expected (running) backend is '" + expectedServerName + "'");
    }

    if (!Objects.equals(expectedFileName, fileName)) {
      throw new PdoRuntimeException("cache data belongs to file '" + fileName +
              "', expected filename is '" + expectedFileName + "'");
    }
  }

  /**
   * Applies the cache data to the cache
   *
   * @param db the database connection assumed for loaded objects
   */
  private synchronized void applyToCache(Session session) {
    cache.invalidate();   // remove all objects from cache (if any)
    for (T object : objects) {
      object.setSession(session);   // this is transient
      cache.add(object);
    }
    // setup the context db list and set the selectAll-lists if cache is preloading
    cache.updateContextInfo();
    // check if cache is expired by asking the server
    long currentTableSerial = getTableSerial();
    if (currentTableSerial != tableSerial) {
      cache.expire(currentTableSerial);
    }
  }


  private long getTableSerial() {
    if (objects != null && objects.size() > 0) {
      return PdoTracker.getInstance().getSerial(cache.getObjectClass());
    }
    else  {
      return 0;
    }
  }

  /**
   * Gets the file for this cache.
   *
   * @return the file
   */
  private File getFile() {
    File file = new File(UserHomeJavaDir.getDirectory("cache", "pdo"), applicationName);
    if (!file.exists()) {
      // make application directory if it doesnt exist
      file.mkdir();
      LOGGER.info("Created cache application directory " + file.getAbsolutePath());
    }
    if (serverName != null) {
      file = new File(file, serverName);
      if (!file.exists()) {
        file.mkdir();
        LOGGER.info("Created cache backend application directory " + file.getAbsolutePath());
      }
    }
    return new File(file, fileName);
  }


}
