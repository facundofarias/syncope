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
package org.apache.syncope.common.rest.api.beans;

import javax.validation.constraints.Min;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import org.apache.syncope.common.lib.AbstractBaseBean;
import org.apache.syncope.common.rest.api.service.JAXRSService;

public abstract class AbstractQuery extends AbstractBaseBean {

    private static final long serialVersionUID = -371488230250055359L;

    private Integer page;

    private Integer size;

    private String orderBy;

    public Integer getPage() {
        return page;
    }

    @Min(1)
    @QueryParam(JAXRSService.PARAM_PAGE)
    @DefaultValue("1")
    public void setPage(final Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    @Min(1)
    @QueryParam(JAXRSService.PARAM_SIZE)
    @DefaultValue("25")
    public void setSize(final Integer size) {
        this.size = size;
    }

    @QueryParam(JAXRSService.PARAM_ORDERBY)
    public String getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(final String orderBy) {
        this.orderBy = orderBy;
    }
}
