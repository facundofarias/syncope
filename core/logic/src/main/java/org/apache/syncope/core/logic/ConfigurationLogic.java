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

import java.io.OutputStream;
import java.lang.reflect.Method;
import org.apache.syncope.common.lib.to.AttrTO;
import org.apache.syncope.common.lib.to.ConfTO;
import org.apache.syncope.common.lib.types.Entitlement;
import org.apache.syncope.core.misc.security.AuthContextUtils;
import org.apache.syncope.core.persistence.api.content.ContentExporter;
import org.apache.syncope.core.persistence.api.dao.ConfDAO;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.PlainSchemaDAO;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;
import org.apache.syncope.core.persistence.api.entity.conf.CPlainAttr;
import org.apache.syncope.core.provisioning.api.data.ConfigurationDataBinder;
import org.apache.syncope.core.workflow.api.GroupWorkflowAdapter;
import org.apache.syncope.core.workflow.api.UserWorkflowAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConfigurationLogic extends AbstractTransactionalLogic<ConfTO> {

    @Autowired
    private ConfDAO confDAO;

    @Autowired
    private PlainSchemaDAO plainSchemaDAO;

    @Autowired
    private ConfigurationDataBinder binder;

    @Autowired
    private ContentExporter exporter;

    @Autowired
    private UserWorkflowAdapter uwfAdapter;

    @Autowired
    private GroupWorkflowAdapter gwfAdapter;

    @PreAuthorize("hasRole('" + Entitlement.CONFIGURATION_DELETE + "')")
    public void delete(final String schema) {
        confDAO.delete(schema);
    }

    @PreAuthorize("hasRole('" + Entitlement.CONFIGURATION_LIST + "')")
    public ConfTO list() {
        return binder.getConfTO(confDAO.get());
    }

    @PreAuthorize("isAuthenticated()")
    public AttrTO get(final String schema) {
        AttrTO result;

        CPlainAttr conf = confDAO.find(schema);
        if (conf == null) {
            PlainSchema plainSchema = plainSchemaDAO.find(schema);
            if (plainSchema == null) {
                throw new NotFoundException("Configuration schema " + schema);
            }

            result = new AttrTO();
            result.setSchema(schema);
        } else {
            result = binder.getAttrTO(conf);
        }

        return result;
    }

    @PreAuthorize("hasRole('" + Entitlement.CONFIGURATION_SET + "')")
    public void set(final AttrTO value) {
        confDAO.save(binder.getAttribute(value));
    }

    @PreAuthorize("hasRole('" + Entitlement.CONFIGURATION_EXPORT + "')")
    @Transactional(readOnly = true)
    public void export(final OutputStream os) {
        try {
            exporter.export(AuthContextUtils.getDomain(), os, uwfAdapter.getPrefix(), gwfAdapter.getPrefix());
            LOG.debug("Database content successfully exported");
        } catch (Exception e) {
            LOG.error("While exporting database content", e);
        }
    }

    @Override
    protected ConfTO resolveReference(final Method method, final Object... args)
            throws UnresolvedReferenceException {

        throw new UnresolvedReferenceException();
    }
}
