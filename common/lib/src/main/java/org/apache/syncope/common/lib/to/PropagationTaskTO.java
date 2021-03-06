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
package org.apache.syncope.common.lib.to;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ResourceOperation;

@XmlRootElement(name = "propagationTask")
@XmlType
public class PropagationTaskTO extends AbstractTaskTO {

    private static final long serialVersionUID = 386450127003321197L;

    private ResourceOperation operation;

    private String connObjectKey;

    private String oldConnObjectKey;

    private String xmlAttributes;

    private String resource;

    private String objectClassName;

    private AnyTypeKind anyTypeKind;

    private Long anyKey;

    public String getConnObjectKey() {
        return connObjectKey;
    }

    public void setConnObjectKey(final String connObjectKey) {
        this.connObjectKey = connObjectKey;
    }

    public String getOldConnObjectKey() {
        return oldConnObjectKey;
    }

    public void setOldConnObjectKey(final String oldConnObjectKey) {
        this.oldConnObjectKey = oldConnObjectKey;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(final String resource) {
        this.resource = resource;
    }

    public ResourceOperation getOperation() {
        return operation;
    }

    public void setOperation(final ResourceOperation operation) {
        this.operation = operation;
    }

    public String getXmlAttributes() {
        return xmlAttributes;
    }

    public void setXmlAttributes(final String xmlAttributes) {
        this.xmlAttributes = xmlAttributes;
    }

    public String getObjectClassName() {
        return objectClassName;
    }

    public void setObjectClassName(final String objectClassName) {
        this.objectClassName = objectClassName;
    }

    public AnyTypeKind getAnyTypeKind() {
        return anyTypeKind;
    }

    public void setAnyTypeKind(final AnyTypeKind anyTypeKind) {
        this.anyTypeKind = anyTypeKind;
    }

    public Long getAnyKey() {
        return anyKey;
    }

    public void setAnyKey(final Long anyKey) {
        this.anyKey = anyKey;
    }
}
