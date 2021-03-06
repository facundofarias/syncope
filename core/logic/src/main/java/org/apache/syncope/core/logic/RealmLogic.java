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
package org.apache.syncope.core.logic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.syncope.common.lib.to.RealmTO;
import org.apache.syncope.common.lib.types.Entitlement;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.entity.Realm;
import org.apache.syncope.core.provisioning.api.data.RealmDataBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class RealmLogic extends AbstractTransactionalLogic<RealmTO> {

    @Autowired
    private RealmDAO realmDAO;

    @Autowired
    private RealmDataBinder binder;

    @PreAuthorize("hasRole('" + Entitlement.REALM_LIST + "')")
    public List<RealmTO> list(final String fullPath) {
        Realm realm = realmDAO.find(fullPath);
        if (realm == null) {
            LOG.error("Could not find realm '" + fullPath + "'");

            throw new NotFoundException(fullPath);
        }

        return CollectionUtils.collect(realmDAO.findDescendants(realm), new Transformer<Realm, RealmTO>() {

            @Override
            public RealmTO transform(final Realm input) {
                return binder.getRealmTO(input);
            }
        }, new ArrayList<RealmTO>());
    }

    @PreAuthorize("hasRole('" + Entitlement.REALM_CREATE + "')")
    public RealmTO create(final String parentPath, final RealmTO realmTO) {
        return binder.getRealmTO(realmDAO.save(binder.create(parentPath, realmTO)));
    }

    @PreAuthorize("hasRole('" + Entitlement.REALM_UPDATE + "')")
    public RealmTO update(final RealmTO realmTO) {
        Realm realm = realmDAO.find(realmTO.getFullPath());
        if (realm == null) {
            LOG.error("Could not find realm '" + realmTO.getFullPath() + "'");

            throw new NotFoundException(realmTO.getFullPath());
        }

        binder.update(realm, realmTO);
        realm = realmDAO.save(realm);

        return binder.getRealmTO(realm);
    }

    @PreAuthorize("hasRole('" + Entitlement.REALM_DELETE + "')")
    public RealmTO delete(final String fullPath) {
        Realm realm = realmDAO.find(fullPath);
        if (realm == null) {
            LOG.error("Could not find realm '" + fullPath + "'");

            throw new NotFoundException(fullPath);
        }

        RealmTO deleted = binder.getRealmTO(realm);
        realmDAO.delete(realm);
        return deleted;
    }

    @Override
    protected RealmTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        String fullPath = null;

        if (ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; fullPath == null && i < args.length; i++) {
                if (args[i] instanceof String) {
                    fullPath = (String) args[i];
                } else if (args[i] instanceof RealmTO) {
                    fullPath = ((RealmTO) args[i]).getFullPath();
                }
            }
        }

        if (fullPath != null) {
            try {
                return binder.getRealmTO(realmDAO.find(fullPath));
            } catch (Throwable e) {
                LOG.debug("Unresolved reference", e);
                throw new UnresolvedReferenceException(e);
            }
        }

        throw new UnresolvedReferenceException();
    }

}
