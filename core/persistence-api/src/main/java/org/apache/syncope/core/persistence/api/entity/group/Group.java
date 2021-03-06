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
package org.apache.syncope.core.persistence.api.entity.group;

import java.util.List;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.persistence.api.entity.anyobject.ADynGroupMembership;
import org.apache.syncope.core.persistence.api.entity.user.UDynGroupMembership;
import org.apache.syncope.core.persistence.api.entity.user.User;

public interface Group extends Any<GPlainAttr, GDerAttr, GVirAttr> {

    String getName();

    void setName(String name);

    Group getGroupOwner();

    User getUserOwner();

    void setGroupOwner(Group groupOwner);

    void setUserOwner(User userOwner);

    @Override
    boolean add(GPlainAttr attr);

    @Override
    boolean remove(GPlainAttr attr);

    @Override
    GPlainAttr getPlainAttr(String plainSchemaName);

    @Override
    List<? extends GPlainAttr> getPlainAttrs();

    @Override
    boolean add(GDerAttr attr);

    @Override
    boolean remove(GDerAttr derAttr);

    @Override
    GDerAttr getDerAttr(String derSchemaName);

    @Override
    List<? extends GDerAttr> getDerAttrs();

    @Override
    boolean add(GVirAttr attr);

    @Override
    boolean remove(GVirAttr virAttr);

    @Override
    GVirAttr getVirAttr(String virSchemaName);

    @Override
    List<? extends GVirAttr> getVirAttrs();

    ADynGroupMembership getADynMembership();

    void setADynMembership(ADynGroupMembership aDynMembership);

    UDynGroupMembership getUDynMembership();

    void setUDynMembership(UDynGroupMembership uDynMembership);

    boolean add(TypeExtension typeExtension);

    boolean remove(TypeExtension typeExtension);

    TypeExtension getTypeExtension(AnyType anyType);

    List<? extends TypeExtension> getTypeExtensions();
}
