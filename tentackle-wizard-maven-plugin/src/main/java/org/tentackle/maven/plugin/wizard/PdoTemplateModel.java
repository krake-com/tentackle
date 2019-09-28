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

import org.tentackle.maven.TemplateModel;

/**
 * Freemarker model for the PDO templates.
 */
public class PdoTemplateModel extends TemplateModel {

  /**
   * Creates the model from the generator.
   *
   * @param generator the PDO generator
   */
  public PdoTemplateModel(PdoGenerator generator) {
    super(generator.getLogger());

    putValue("profile", generator.getProfile().getName());

    putValue("pdoPackage", generator.getProfile().getPdoPackage());
    putValue("domainPackage", generator.getProfile().getDomainPackage());
    putValue("persistencePackage", generator.getProfile().getPersistencePackage());
    putValue("domainImplPackage", generator.getProfile().getDomainImplPackage());
    putValue("persistenceImplPackage", generator.getProfile().getPersistenceImplPackage());

    putValue("superPdoInterface", generator.getSuperPdoInterface());
    putValue("pdoInterface", generator.getEntityName());
    putValue("domainInterface", generator.getDomainInterface());
    putValue("persistenceInterface", generator.getPersistenceInterface());
    putValue("domainImplementation", generator.getDomainImplementation());
    putValue("persistenceImplementation", generator.getPersistenceImplementation());
    putValue("superDomainInterface", generator.getSuperDomainInterface());
    putValue("superPersistenceInterface", generator.getSuperPersistenceInterface());
    putValue("superDomainImplementation", generator.getSuperDomainImplementation());
    putValue("superPersistenceImplementation", generator.getSuperPersistenceImplementation());

    putValue("pdoTablename", generator.getTableName());
    putValue("pdoClassId", generator.getClassId());
    putValue("shortDescription", generator.getShortDescription());
    putValue("longDescription", generator.getLongDescription());
    putValue("pdoExtends", generator.getSuperEntity());
    putValue("pdoInheritance", generator.getInheritanceType());
    putValue("remoteEnabled", generator.isRemoteEnabled());
  }

}
