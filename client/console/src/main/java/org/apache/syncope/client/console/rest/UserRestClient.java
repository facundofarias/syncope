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
import org.apache.syncope.client.console.commons.status.StatusBean;
import org.apache.syncope.client.console.commons.status.StatusUtils;
import org.apache.syncope.client.lib.SyncopeClient;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.patch.StatusPatch;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.to.BulkAction;
import org.apache.syncope.common.lib.to.BulkActionResult;
import org.apache.syncope.common.lib.to.ConnObjectTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.StatusPatchType;
import org.apache.syncope.common.rest.api.service.AnyService;
import org.apache.syncope.common.rest.api.service.ResourceService;
import org.apache.syncope.common.rest.api.service.UserService;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.springframework.stereotype.Component;

/**
 * Console client for invoking rest users services.
 */
@Component
public class UserRestClient extends AbstractAnyRestClient {

    private static final long serialVersionUID = -1575748964398293968L;

    @Override
    protected Class<? extends AnyService<?, ?>> getAnyServiceClass() {
        return UserService.class;
    }

    @Override
    public int count(final String realm) {
        return getService(UserService.class).
                list(SyncopeClient.getAnyListQueryBuilder().realm(realm).page(1).size(1).build()).
                getTotalCount();
    }

    @Override
    public List<UserTO> list(final String realm, final int page, final int size, final SortParam<String> sort,
            final String type) {
        return getService(UserService.class).
                list(SyncopeClient.getAnyListQueryBuilder().realm(realm).page(page).size(size).
                        orderBy(toOrderBy(sort)).details(false).build()).
                getResult();
    }

    public UserTO create(final UserTO userTO, final boolean storePassword) {
        Response response = getService(UserService.class).create(userTO, storePassword);
        return response.readEntity(UserTO.class);
    }

    public UserTO update(final String etag, final UserPatch userPatch) {
        UserTO result;
        synchronized (this) {
            UserService service = getService(etag, UserService.class);
            result = service.update(userPatch).readEntity(UserTO.class);
            resetClient(UserService.class);
        }
        return result;
    }

    @Override
    public UserTO delete(final String etag, final Long id) {
        UserTO result;
        synchronized (this) {
            UserService service = getService(etag, UserService.class);
            result = service.delete(id).readEntity(UserTO.class);
            resetClient(UserService.class);
        }
        return result;
    }

    public UserTO read(final Long id) {
        UserTO userTO = null;
        try {
            userTO = getService(UserService.class).read(id);
        } catch (SyncopeClientException e) {
            LOG.error("While reading a user", e);
        }
        return userTO;
    }

    @Override
    public int searchCount(final String realm, final String fiql, final String type) {
        return getService(UserService.class).
                search(SyncopeClient.getAnySearchQueryBuilder().realm(realm).fiql(fiql).page(1).size(1).build()).
                getTotalCount();
    }

    @Override
    public List<UserTO> search(
            final String realm, final String fiql, final int page, final int size, final SortParam<String> sort,
            final String type) {

        return getService(UserService.class).
                search(SyncopeClient.getAnySearchQueryBuilder().realm(realm).fiql(fiql).page(page).size(size).
                        orderBy(toOrderBy(sort)).details(false).build()).
                getResult();
    }

    @Override
    public ConnObjectTO readConnObject(final String resourceName, final Long id) {
        return getService(ResourceService.class).readConnObject(resourceName, AnyTypeKind.USER.name(), id);
    }

    public void suspend(final String etag, final long userKey, final List<StatusBean> statuses) {
        StatusPatch statusPatch = StatusUtils.buildStatusPatch(statuses, false);
        statusPatch.setKey(userKey);
        statusPatch.setType(StatusPatchType.SUSPEND);
        synchronized (this) {
            UserService service = getService(etag, UserService.class);
            service.status(statusPatch);
            resetClient(UserService.class);
        }
    }

    public void reactivate(final String etag, final long userKey, final List<StatusBean> statuses) {
        StatusPatch statusPatch = StatusUtils.buildStatusPatch(statuses, true);
        statusPatch.setKey(userKey);
        statusPatch.setType(StatusPatchType.REACTIVATE);
        synchronized (this) {
            UserService service = getService(etag, UserService.class);
            service.status(statusPatch);
            resetClient(UserService.class);
        }
    }

    @Override
    public BulkActionResult bulkAction(final BulkAction action) {
        return getService(UserService.class).bulk(action);
    }
}
