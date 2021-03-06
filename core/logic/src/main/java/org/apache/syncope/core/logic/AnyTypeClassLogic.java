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
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.to.AnyTypeClassTO;
import org.apache.syncope.common.lib.types.Entitlement;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.AnyTypeClassDAO;
import org.apache.syncope.core.persistence.api.entity.AnyTypeClass;
import org.apache.syncope.core.provisioning.api.data.AnyTypeClassDataBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class AnyTypeClassLogic extends AbstractTransactionalLogic<AnyTypeClassTO> {

    @Autowired
    private AnyTypeClassDataBinder binder;

    @Autowired
    private AnyTypeClassDAO anyTypeClassDAO;

    @PreAuthorize("hasRole('" + Entitlement.ANYTYPECLASS_READ + "')")
    public AnyTypeClassTO read(final String key) {
        AnyTypeClass anyType = anyTypeClassDAO.find(key);
        if (anyType == null) {
            LOG.error("Could not find anyType '" + key + "'");

            throw new NotFoundException(String.valueOf(key));
        }

        return binder.getAnyTypeClassTO(anyType);
    }

    @PreAuthorize("hasRole('" + Entitlement.ANYTYPECLASS_LIST + "')")
    public List<AnyTypeClassTO> list() {
        return CollectionUtils.collect(anyTypeClassDAO.findAll(), new Transformer<AnyTypeClass, AnyTypeClassTO>() {

            @Override
            public AnyTypeClassTO transform(final AnyTypeClass input) {
                return binder.getAnyTypeClassTO(input);
            }
        }, new ArrayList<AnyTypeClassTO>());
    }

    @PreAuthorize("hasRole('" + Entitlement.ANYTYPECLASS_CREATE + "')")
    public AnyTypeClassTO create(final AnyTypeClassTO anyTypeClassTO) {
        return binder.getAnyTypeClassTO(anyTypeClassDAO.save(binder.create(anyTypeClassTO)));
    }

    @PreAuthorize("hasRole('" + Entitlement.ANYTYPECLASS_UPDATE + "')")
    public AnyTypeClassTO update(final AnyTypeClassTO anyTypeClassTO) {
        AnyTypeClass anyType = anyTypeClassDAO.find(anyTypeClassTO.getKey());
        if (anyType == null) {
            LOG.error("Could not find anyTypeClass '" + anyTypeClassTO.getKey() + "'");
            throw new NotFoundException(String.valueOf(anyTypeClassTO.getKey()));
        }

        binder.update(anyType, anyTypeClassTO);
        anyType = anyTypeClassDAO.save(anyType);

        return binder.getAnyTypeClassTO(anyType);
    }

    @PreAuthorize("hasRole('" + Entitlement.ANYTYPECLASS_DELETE + "')")
    public AnyTypeClassTO delete(final String key) {
        AnyTypeClass anyTypeClass = anyTypeClassDAO.find(key);
        if (anyTypeClass == null) {
            LOG.error("Could not find anyTypeClass '" + key + "'");

            throw new NotFoundException(String.valueOf(key));
        }

        AnyTypeClassTO deleted = binder.getAnyTypeClassTO(anyTypeClass);
        anyTypeClassDAO.delete(key);
        return deleted;
    }

    @Override
    protected AnyTypeClassTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        String key = null;

        if (ArrayUtils.isNotEmpty(args)) {
            for (int i = 0; key == null && i < args.length; i++) {
                if (args[i] instanceof String) {
                    key = (String) args[i];
                } else if (args[i] instanceof AnyTypeClassTO) {
                    key = ((AnyTypeClassTO) args[i]).getKey();
                }
            }
        }

        if (StringUtils.isNotBlank(key)) {
            try {
                return binder.getAnyTypeClassTO(anyTypeClassDAO.find(key));
            } catch (Throwable ignore) {
                LOG.debug("Unresolved reference", ignore);
                throw new UnresolvedReferenceException(ignore);
            }
        }

        throw new UnresolvedReferenceException();
    }

}
