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
package org.apache.syncope.common.lib.patch;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.Set;
import javax.ws.rs.PathParam;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlType;
import org.apache.syncope.common.lib.AbstractBaseBean;

@XmlType
public abstract class AnyPatch extends AbstractBaseBean {

    private static final long serialVersionUID = -7445489774552440544L;

    private long key;

    private StringReplacePatchItem realm;

    private final Set<StringPatchItem> auxClasses = new HashSet<>();

    private final Set<AttrPatch> plainAttrs = new HashSet<>();

    private final Set<AttrPatch> derAttrs = new HashSet<>();

    private final Set<AttrPatch> virAttrs = new HashSet<>();

    private final Set<StringPatchItem> resources = new HashSet<>();

    public long getKey() {
        return key;
    }

    @PathParam("key")
    public void setKey(final long key) {
        this.key = key;
    }

    public StringReplacePatchItem getRealm() {
        return realm;
    }

    public void setRealm(final StringReplacePatchItem realm) {
        this.realm = realm;
    }

    @XmlElementWrapper(name = "auxClasses")
    @XmlElement(name = "auxClass")
    @JsonProperty("auxClasses")
    public Set<StringPatchItem> getAuxClasses() {
        return auxClasses;
    }

    @XmlElementWrapper(name = "plainAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("plainAttrs")
    public Set<AttrPatch> getPlainAttrs() {
        return plainAttrs;
    }

    @XmlElementWrapper(name = "derAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("derAttrs")
    public Set<AttrPatch> getDerAttrs() {
        return derAttrs;
    }

    @XmlElementWrapper(name = "virAttrs")
    @XmlElement(name = "attribute")
    @JsonProperty("virAttrs")
    public Set<AttrPatch> getVirAttrs() {
        return virAttrs;
    }

    @XmlElementWrapper(name = "resources")
    @XmlElement(name = "resource")
    @JsonProperty("resources")
    public Set<StringPatchItem> getResources() {
        return resources;
    }

    /**
     * @return true if no actual changes are defined
     */
    @JsonIgnore
    public boolean isEmpty() {
        return realm == null && auxClasses.isEmpty() && plainAttrs.isEmpty() && derAttrs.isEmpty()
                && virAttrs.isEmpty() && resources.isEmpty();
    }
}
