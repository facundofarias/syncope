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
package org.apache.syncope.core.util;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.keyvalue.DefaultMapEntry;
import org.apache.syncope.core.persistence.beans.AbstractAttr;
import org.apache.syncope.core.persistence.beans.AbstractAttrValue;
import org.apache.syncope.core.persistence.beans.AbstractAttributable;
import org.apache.syncope.core.persistence.beans.AbstractDerAttr;
import org.apache.syncope.core.persistence.beans.AbstractSchema;
import org.apache.syncope.core.persistence.beans.AbstractVirAttr;
import org.apache.syncope.core.persistence.beans.Attributable;
import org.apache.syncope.core.persistence.beans.SchemaMapping;
import org.apache.syncope.core.persistence.beans.membership.MDerSchema;
import org.apache.syncope.core.persistence.beans.membership.MSchema;
import org.apache.syncope.core.persistence.beans.membership.MVirSchema;
import org.apache.syncope.core.persistence.beans.role.RDerSchema;
import org.apache.syncope.core.persistence.beans.role.RSchema;
import org.apache.syncope.core.persistence.beans.role.RVirSchema;
import org.apache.syncope.core.persistence.beans.user.SyncopeUser;
import org.apache.syncope.core.persistence.beans.user.UAttrValue;
import org.apache.syncope.core.persistence.beans.user.UDerSchema;
import org.apache.syncope.core.persistence.beans.user.USchema;
import org.apache.syncope.core.persistence.beans.user.UVirSchema;
import org.apache.syncope.core.persistence.dao.SchemaDAO;
import org.apache.syncope.core.propagation.PropagationManager.AttributableHandler;
import org.apache.syncope.types.IntMappingType;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.util.Password;

public class SchemaMappingUtil {

    /**
     * Logger.
     */
    protected static final Logger LOG = LoggerFactory.getLogger(SchemaMappingUtil.class);

    public static String getExtAttrName(final SchemaMapping mapping) {
        final String name;

        if (mapping.isAccountid()) {
            name = Uid.NAME;
        } else if (mapping.isPassword()) {
            name = OperationalAttributes.PASSWORD_NAME;
        } else {
            name = mapping.getExtAttrName();
        }

        return name;
    }

    public static String getIntAttrName(final SchemaMapping mapping) {
        final String name;

        switch (mapping.getIntMappingType()) {
            case SyncopeUserId:
                name = "id";
                break;
            case Username:
                name = "username";
                break;
            case Password:
                name = "password";
                break;
            default:
                name = mapping.getIntAttrName();
        }

        return name;
    }

    public static Set<SchemaMapping> getMappings(final Collection<SchemaMapping> mappings, final String intAttrName,
            final IntMappingType type) {

        final Set<SchemaMapping> result = new HashSet<SchemaMapping>();

        for (SchemaMapping schemaMapping : mappings) {
            if (schemaMapping.getIntMappingType() == type && intAttrName.equals(getIntAttrName(schemaMapping))) {
                result.add(schemaMapping);
            }
        }

        return result;
    }

    public static Set<SchemaMapping> getMappings(final Collection<SchemaMapping> mappings, final String intAttrName) {

        final Set<SchemaMapping> result = new HashSet<SchemaMapping>();

        for (SchemaMapping schemaMapping : mappings) {
            if (intAttrName.equals(getIntAttrName(schemaMapping))) {
                result.add(schemaMapping);
            }
        }

        return result;
    }

    public static String getIntAttrName(final SchemaMapping mapping, final IntMappingType type) {
        return type == mapping.getIntMappingType()
                ? getIntAttrName(mapping)
                : null;
    }

    /**
     * Get attribute values.
     *
     * @param mapping mapping.
     * @param attributables list of attributables.
     * @param password password.
     * @return schema and attribute values.
     */
    public static Map.Entry<AbstractSchema, List<AbstractAttrValue>> getIntValues(final SchemaMapping mapping,
            final List<Attributable> attributables, final String password, final SchemaDAO schemaDAO) {

        LOG.debug("Get attributes for '{}' and mapping type '{}'", attributables, mapping.getIntMappingType());

        AbstractSchema schema = null;

        List<AbstractAttrValue> values = new ArrayList<AbstractAttrValue>();

        switch (mapping.getIntMappingType()) {
            case UserSchema:
            case RoleSchema:
            case MembershipSchema:
                schema = schemaDAO.find(mapping.getIntAttrName(), SchemaMappingUtil.getIntMappingTypeClass(mapping.
                        getIntMappingType()));

                for (Attributable attributable : attributables) {
                    final AbstractAttr attr = attributable.getAttribute(mapping.getIntAttrName());

                    if (attr != null && attr.getValues() != null) {
                        values.addAll(schema.isUniqueConstraint()
                                ? Collections.singletonList(attr.getUniqueValue()) : attr.getValues());
                    }

                    LOG.debug("Retrieved attribute {}" + "\n* IntAttrName {}" + "\n* IntMappingType {}"
                            + "\n* Attribute values {}", new Object[]{attr, mapping.getIntAttrName(),
                                mapping.getIntMappingType(), values});
                }

                break;

            case UserVirtualSchema:
            case RoleVirtualSchema:
            case MembershipVirtualSchema:

                for (Attributable attributable : attributables) {
                    AbstractVirAttr virAttr = attributable.getVirtualAttribute(mapping.getIntAttrName());

                    if (virAttr != null && virAttr.getValues() != null) {
                        for (String value : virAttr.getValues()) {
                            AbstractAttrValue attrValue = new UAttrValue();
                            attrValue.setStringValue(value);
                            values.add(attrValue);
                        }
                    }

                    LOG.debug("Retrieved virtual attribute {}" + "\n* IntAttrName {}" + "\n* IntMappingType {}"
                            + "\n* Attribute values {}", new Object[]{virAttr, mapping.getIntAttrName(),
                                mapping.getIntMappingType(), values});
                }
                break;

            case UserDerivedSchema:
            case RoleDerivedSchema:
            case MembershipDerivedSchema:
                for (Attributable attributable : attributables) {
                    AbstractDerAttr derAttr = attributable.getDerivedAttribute(mapping.getIntAttrName());

                    if (derAttr != null) {
                        AbstractAttrValue attrValue = new UAttrValue();
                        attrValue.setStringValue(derAttr.getValue(attributable.getAttributes()));
                        values.add(attrValue);
                    }

                    LOG.debug("Retrieved attribute {}" + "\n* IntAttrName {}" + "\n* IntMappingType {}"
                            + "\n* Attribute values {}", new Object[]{derAttr, mapping.getIntAttrName(),
                                mapping.getIntMappingType(), values});
                }
                break;

            case Username:
                for (Attributable attributable : attributables) {
                    AbstractAttrValue attrValue = new UAttrValue();
                    SyncopeUser user = attributable instanceof Proxy?
                            (SyncopeUser)((AttributableHandler)Proxy.getInvocationHandler(attributable)).getObject():
                            (SyncopeUser)attributable;
                     
                    attrValue.setStringValue(user.getUsername());
                    values.add(attrValue);
                }
                break;

            case SyncopeUserId:
                for (Attributable attributable : attributables) {
                    AbstractAttrValue attrValue = new UAttrValue();
                    attrValue.setStringValue(attributable.getId().toString());
                    values.add(attrValue);
                }
                break;

            case Password:
                AbstractAttrValue attrValue = new UAttrValue();

                if (password != null) {
                    attrValue.setStringValue(password);
                }

                values.add(attrValue);
                break;

            default:
        }

        LOG.debug("Retrived values '{}'", values);

        return new DefaultMapEntry(schema, values);
    }

    public static List<String> getIntValueAsStrings(
            final AbstractAttributable attributable, final SchemaMapping mapping) {
        return getIntValueAsStrings(attributable, mapping, null);
    }

    public static List<String> getIntValueAsStrings(
            final AbstractAttributable attributable, final SchemaMapping mapping, String clearPassword) {

        List<String> value = new ArrayList<String>();

        if (mapping != null) {
            switch (mapping.getIntMappingType()) {
                case Username:
                    if (!(attributable instanceof SyncopeUser)) {
                        throw new ClassCastException("mappingtype is Username, but attributable is not SyncopeUser: "
                                + attributable.getClass().getName());
                    }
                    value.add(((SyncopeUser) attributable).getUsername());
                    break;
                case Password:
                    if (clearPassword != null) {
                        value.add(clearPassword);
                    }
                    break;
                case UserSchema:
                case RoleSchema:
                case MembershipSchema:
                    AbstractAttr abstractAttr = attributable.getAttribute(mapping.getIntAttrName());
                    if (abstractAttr != null && abstractAttr.getValues() != null) {
                        value.addAll(abstractAttr.getValuesAsStrings());
                    }
                    break;
                case UserVirtualSchema:
                case RoleVirtualSchema:
                case MembershipVirtualSchema:
                    AbstractVirAttr abstractVirAttr = attributable.getVirtualAttribute(mapping.getIntAttrName());
                    if (abstractVirAttr != null && abstractVirAttr.getValues() != null) {
                        value.addAll(abstractVirAttr.getValues());
                    }
                    break;
                case UserDerivedSchema:
                case RoleDerivedSchema:
                case MembershipDerivedSchema:
                    AbstractDerAttr abstractDerAttr = attributable.getDerivedAttribute(mapping.getIntAttrName());
                    if (abstractDerAttr != null) {
                        String abstractDerAttrValue = abstractDerAttr.getValue(attributable.getAttributes());
                        if (abstractDerAttrValue != null) {
                            value.add(abstractDerAttrValue);
                        }
                    }
                    break;
                default:
            }
        }

        return value;
    }

    /**
     * For given source mapping type, return the corresponding Class object.
     *
     * @param intMappingType source mapping type
     * @return corresponding Class object, if any (can be null)
     */
    public static Class getIntMappingTypeClass(final IntMappingType intMappingType) {

        Class result;

        switch (intMappingType) {
            case UserSchema:
                result = USchema.class;
                break;
            case RoleSchema:
                result = RSchema.class;
                break;
            case MembershipSchema:
                result = MSchema.class;
                break;

            case UserDerivedSchema:
                result = UDerSchema.class;
                break;
            case RoleDerivedSchema:
                result = RDerSchema.class;
                break;
            case MembershipDerivedSchema:
                result = MDerSchema.class;
                break;

            case UserVirtualSchema:
                result = UVirSchema.class;
                break;
            case RoleVirtualSchema:
                result = RVirSchema.class;
                break;
            case MembershipVirtualSchema:
                result = MVirSchema.class;
                break;

            default:
                result = null;
        }

        return result;
    }

    /**
     * Get first occurance of accountId mapping from a collection of mappings.
     *
     * @param mappings collection of SchemaMapping.
     * @return AccountId mapping or null if no occurences found.
     */
    public static SchemaMapping getAccountIdMapping(final Collection<SchemaMapping> mappings) {
        for (SchemaMapping mapping : mappings) {
            if (mapping.isAccountid()) {
                return mapping;
            }
        }

        return null;
    }

    /**
     * Get accountId internal value.
     *
     * @param attributable attributable.
     * @param mappings collection of SchemaMapping.
     * @return accountId internal value.
     */
    public static String getAccountIdValue(final AbstractAttributable attributable,
            final Collection<SchemaMapping> mappings) {

        final List<String> values = getIntValueAsStrings(attributable, getAccountIdMapping(mappings));
        return values == null || values.isEmpty()
                ? null
                : values.get(0);
    }

    /**
     * Get accountId internal value.
     *
     * @param attributable attributable.
     * @param mappings accountId mapping.
     * @return accountId internal value.
     */
    public static String getAccountIdValue(final AbstractAttributable attributable, final SchemaMapping mapping) {
        final List<String> values = getIntValueAsStrings(attributable, mapping);
        return values == null || values.isEmpty()
                ? null
                : values.get(0);
    }

    public static class SchemaMappingsWrapper {

        SchemaMapping accountIdMapping = null;

        SchemaMapping passwordMapping = null;

        final Map<String, Collection<SchemaMapping>> uMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> uVirMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> uDerMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> rMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> rVirMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> rDerMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> mMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> mVirMappings = new HashMap<String, Collection<SchemaMapping>>();

        final Map<String, Collection<SchemaMapping>> mDerMappings = new HashMap<String, Collection<SchemaMapping>>();

        public SchemaMappingsWrapper(final Collection<SchemaMapping> mappings) {
            if (mappings == null) {
                return;
            }

            for (SchemaMapping mapping : mappings) {
                if (mapping.isAccountid() && accountIdMapping == null) {

                    accountIdMapping = mapping;

                } else if (mapping.isPassword() && passwordMapping == null) {

                    passwordMapping = mapping;

                } else {
                    final String intAttrName = getIntAttrName(mapping);

                    switch (mapping.getIntMappingType()) {
                        case Password:
                            if (passwordMapping == null) {
                                passwordMapping = mapping;
                            }
                            break;
                        case Username:
                        case SyncopeUserId:
                        case UserSchema:
                            if (uMappings.get(intAttrName) == null) {
                                uMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            uMappings.get(intAttrName).add(mapping);
                            break;
                        case RoleSchema:
                            if (rMappings.get(intAttrName) == null) {
                                rMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            rMappings.get(intAttrName).add(mapping);
                            break;
                        case MembershipSchema:
                            if (mMappings.get(intAttrName) == null) {
                                mMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            mMappings.get(intAttrName).add(mapping);
                            break;

                        case UserDerivedSchema:
                            if (uDerMappings.get(intAttrName) == null) {
                                uDerMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            uDerMappings.get(intAttrName).add(mapping);
                            break;
                        case RoleDerivedSchema:
                            if (rDerMappings.get(intAttrName) == null) {
                                rDerMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            rDerMappings.get(intAttrName).add(mapping);
                            break;
                        case MembershipDerivedSchema:
                            if (mDerMappings.get(intAttrName) == null) {
                                mDerMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            mDerMappings.get(intAttrName).add(mapping);
                            break;

                        case UserVirtualSchema:
                            if (uVirMappings.get(intAttrName) == null) {
                                uVirMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            uVirMappings.get(intAttrName).add(mapping);
                            break;
                        case RoleVirtualSchema:
                            if (rVirMappings.get(intAttrName) == null) {
                                rVirMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            rVirMappings.get(intAttrName).add(mapping);
                            break;
                        case MembershipVirtualSchema:
                            if (mVirMappings.get(intAttrName) == null) {
                                mVirMappings.put(intAttrName, new HashSet<SchemaMapping>());
                            }
                            mVirMappings.get(intAttrName).add(mapping);
                            break;
                        default:
                    }
                }
            }
        }

        public SchemaMapping getAccountIdMapping() {
            return accountIdMapping;
        }

        public Map<String, Collection<SchemaMapping>> getmDerMappings() {
            return mDerMappings;
        }

        public Map<String, Collection<SchemaMapping>> getmMappings() {
            return mMappings;
        }

        public Map<String, Collection<SchemaMapping>> getmVirMappings() {
            return mVirMappings;
        }

        public SchemaMapping getPasswordMapping() {
            return passwordMapping;
        }

        public Map<String, Collection<SchemaMapping>> getrDerMappings() {
            return rDerMappings;
        }

        public Map<String, Collection<SchemaMapping>> getrMappings() {
            return rMappings;
        }

        public Map<String, Collection<SchemaMapping>> getrVirMappings() {
            return rVirMappings;
        }

        public Map<String, Collection<SchemaMapping>> getuDerMappings() {
            return uDerMappings;
        }

        public Map<String, Collection<SchemaMapping>> getuMappings() {
            return uMappings;
        }

        public Map<String, Collection<SchemaMapping>> getuVirMappings() {
            return uVirMappings;
        }
    }
}