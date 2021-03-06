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
package org.apache.syncope.client.console.rest;

import java.util.List;
import javax.ws.rs.core.Response;
import org.apache.syncope.client.lib.SyncopeClient;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.to.AnyObjectTO;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.BulkAction;
import org.apache.syncope.common.lib.to.BulkActionResult;
import org.apache.syncope.common.lib.to.ConnObjectTO;
import org.apache.syncope.common.lib.to.PagedResult;
import org.apache.syncope.common.rest.api.service.AnyObjectService;
import org.apache.syncope.common.rest.api.service.AnyService;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.springframework.stereotype.Component;

/**
 * Console client for invoking Rest any type class services.
 */
@Component
public class AnyObjectRestClient extends AbstractAnyRestClient {

    private static final long serialVersionUID = -8874495991295283249L;

    @Override
    protected Class<? extends AnyService<?, ?>> getAnyServiceClass() {
        return AnyObjectService.class;
    }

    @Override
    public int count(final String realm) {
        return getService(AnyObjectService.class).list(SyncopeClient.getAnyListQueryBuilder().realm(realm).page(1).size(
                1).build()).getTotalCount();
    }

    @Override
    public List<? extends AnyTO> list(final String realm, final int page, final int size, final SortParam<String> sort,
            final String type) {
        return list(type, realm).getResult();
    }

    public PagedResult<AnyObjectTO> list(final String type, final String realm) {
        return getService(AnyObjectService.class).
                list(type, SyncopeClient.getAnyListQueryBuilder().realm(realm).build());
    }

    @Override
    public int searchCount(final String realm, final String fiql, final String type) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<? extends AnyTO> search(final String realm, final String fiql, final int page, final int size,
            final SortParam<String> sort,
            final String type) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ConnObjectTO readConnObject(final String resourceName, final Long key) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public AnyObjectTO read(final Long id) {
        AnyObjectTO anyObjectTO = null;
        try {
            anyObjectTO = getService(AnyObjectService.class).read(id);
        } catch (SyncopeClientException e) {
            LOG.error("While reading any object", e);
        }
        return anyObjectTO;
    }

    public AnyObjectTO create(final AnyObjectTO anyObjectTO) {
        final Response response = getService(AnyObjectService.class).create(anyObjectTO);
        return response.readEntity(AnyObjectTO.class);
    }

    public AnyObjectTO update(final String etag, final AnyObjectTO anyObjectTO) {
        AnyObjectTO result;
        synchronized (this) {
            AnyObjectService service = getService(etag, AnyObjectService.class);
            result = service.update(anyObjectTO).readEntity(AnyObjectTO.class);
            resetClient(AnyObjectService.class);
        }
        return result;
    }

    @Override
    public AnyTO delete(final String etag, final Long key) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BulkActionResult bulkAction(final BulkAction action) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
