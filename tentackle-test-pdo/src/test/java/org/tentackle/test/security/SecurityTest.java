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

package org.tentackle.test.security;

import org.tentackle.security.SecurityFactory;
import org.tentackle.security.permissions.AllPermission;
import org.tentackle.security.permissions.ExecutePermission;
import org.tentackle.security.permissions.ReadPermission;
import org.tentackle.security.permissions.WritePermission;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 *
 * @author harald
 */
public class SecurityTest {

  @Test
  public void testPermissions() {

    // configured permissions
    @SuppressWarnings("unchecked")
    Class<AllPermission> all = (Class<AllPermission>) SecurityFactory.getInstance().getPermissionInterface(AllPermission.NAME);
    @SuppressWarnings("unchecked")
    Class<ReadPermission> read = (Class<ReadPermission>) SecurityFactory.getInstance().getPermissionInterface(ReadPermission.NAME);
    @SuppressWarnings("unchecked")
    Class<WritePermission> write = (Class<WritePermission>) SecurityFactory.getInstance().getPermissionInterface(WritePermission.NAME);
    @SuppressWarnings("unchecked")
    Class<ExecutePermission> execute = (Class<ExecutePermission>) SecurityFactory.getInstance().getPermissionInterface(ExecutePermission.NAME);

    // requested permissions
    ReadPermission readImpl = SecurityFactory.getInstance().getPermission(read);
    WritePermission writeImpl = SecurityFactory.getInstance().getPermission(write);
    ExecutePermission executeImpl = SecurityFactory.getInstance().getPermission(execute);
    AllPermission allImpl = SecurityFactory.getInstance().getPermission(all);

    Assert.assertTrue(readImpl.isAllowedBy(write));
    Assert.assertTrue(readImpl.isAllowedBy(write, execute));
    Assert.assertFalse(writeImpl.isAllowedBy(read, execute));
    Assert.assertFalse(executeImpl.isAllowedBy(read, write));
    Assert.assertTrue(writeImpl.isAllowedBy(read, write, execute));
    Assert.assertTrue(writeImpl.isAllowedBy(all));
    Assert.assertTrue(allImpl.isAllowedBy(all));

    Assert.assertTrue(allImpl.isDeniedBy(all));
    Assert.assertTrue(writeImpl.isDeniedBy(all));
    Assert.assertTrue(writeImpl.isDeniedBy(write));
    Assert.assertTrue(writeImpl.isDeniedBy(read));
    Assert.assertTrue(writeImpl.isDeniedBy(read, execute));
    Assert.assertFalse(readImpl.isDeniedBy(write));
    Assert.assertTrue(readImpl.isDeniedBy(read));
    Assert.assertFalse(executeImpl.isDeniedBy(read, write));
  }


}
