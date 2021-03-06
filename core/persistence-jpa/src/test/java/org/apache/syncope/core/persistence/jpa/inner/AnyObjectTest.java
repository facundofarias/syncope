/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.persistence.jpa.inner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.core.persistence.api.dao.AnyObjectDAO;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.persistence.jpa.AbstractTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional("Master")
public class AnyObjectTest extends AbstractTest {

    @Autowired
    private AnyObjectDAO anyObjectDAO;

    @Autowired
    private RealmDAO realmDAO;

    @Test
    public void findAll() {
        List<AnyObject> list = anyObjectDAO.findAll(SyncopeConstants.FULL_ADMIN_REALMS, 1, 100);
        assertFalse(list.isEmpty());
    }

    @Test
    public void findAllByType() {
        List<AnyObject> list = anyObjectDAO.findAll("PRINTER", SyncopeConstants.FULL_ADMIN_REALMS, 1, 100);
        assertFalse(list.isEmpty());

        list = anyObjectDAO.findAll("UNEXISTING", SyncopeConstants.FULL_ADMIN_REALMS, 1, 100);
        assertTrue(list.isEmpty());
    }

    @Test
    public void find() {
        AnyObject anyObject = anyObjectDAO.find(2L);
        assertNotNull(anyObject);
        assertNotNull(anyObject.getType());
        assertFalse(anyObject.getType().getClasses().isEmpty());
    }

    @Test
    public void save() {
        AnyObject anyObject = entityFactory.newEntity(AnyObject.class);
        anyObject.setRealm(realmDAO.find(SyncopeConstants.ROOT_REALM));

        anyObject = anyObjectDAO.save(anyObject);
        assertNotNull(anyObject);
    }

    @Test
    public void delete() {
        AnyObject anyObject = anyObjectDAO.find(2L);
        anyObjectDAO.delete(anyObject.getKey());

        AnyObject actual = anyObjectDAO.find(2L);
        assertNull(actual);
    }
}
