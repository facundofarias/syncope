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
package org.apache.syncope.core.misc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.jexl2.JexlContext;
import org.apache.commons.jexl2.MapContext;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.syncope.common.lib.patch.AttrPatch;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.AttrTO;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AttrSchemaType;
import org.apache.syncope.common.lib.types.IntMappingType;
import org.apache.syncope.common.lib.types.MappingPurpose;
import org.apache.syncope.core.misc.policy.InvalidPasswordRuleConf;
import org.apache.syncope.core.persistence.api.dao.PlainSchemaDAO;
import org.apache.syncope.core.persistence.api.dao.VirSchemaDAO;
import org.apache.syncope.core.persistence.api.entity.AnyUtils;
import org.apache.syncope.core.persistence.api.entity.AnyUtilsFactory;
import org.apache.syncope.core.persistence.api.entity.DerAttr;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.PlainAttr;
import org.apache.syncope.core.persistence.api.entity.PlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.VirAttr;
import org.apache.syncope.core.persistence.api.entity.group.GPlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.user.UPlainAttrValue;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.cache.VirAttrCache;
import org.apache.syncope.core.misc.security.Encryptor;
import org.apache.syncope.core.misc.jexl.JexlUtils;
import org.apache.syncope.core.misc.security.PasswordGenerator;
import org.apache.syncope.core.misc.spring.ApplicationContextProvider;
import org.apache.syncope.core.persistence.api.attrvalue.validation.ParsingValidationException;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.PlainAttrUniqueValue;
import org.apache.syncope.core.persistence.api.entity.PlainSchema;
import org.apache.syncope.core.persistence.api.entity.Schema;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.persistence.api.entity.resource.Mapping;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.provisioning.api.VirAttrHandler;
import org.apache.syncope.core.provisioning.api.data.MappingItemTransformer;
import org.identityconnectors.framework.common.FrameworkUtil;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MappingUtils {

    private static final Logger LOG = LoggerFactory.getLogger(MappingUtils.class);

    private static final Encryptor ENCRYPTOR = Encryptor.getInstance();

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private PlainSchemaDAO plainSchemaDAO;

    @Autowired
    private VirSchemaDAO virSchemaDAO;

    @Autowired
    private VirAttrHandler virAttrHandler;

    @Autowired
    private VirAttrCache virAttrCache;

    @Autowired
    private PasswordGenerator passwordGenerator;

    @Autowired
    private EntityFactory entityFactory;

    @Autowired
    private AnyUtilsFactory anyUtilsFactory;

    public static <T extends MappingItem> Collection<T> getMatchingMappingItems(
            final Collection<T> items, final IntMappingType type) {

        return CollectionUtils.select(items, new Predicate<T>() {

            @Override
            public boolean evaluate(final T item) {
                return item.getIntMappingType() == type;
            }
        });
    }

    public static <T extends MappingItem> Collection<T> getMatchingMappingItems(
            final Collection<T> items, final String intAttrName, final IntMappingType type) {

        return CollectionUtils.select(items, new Predicate<T>() {

            @Override
            public boolean evaluate(final T item) {
                return item.getIntMappingType() == type && intAttrName.equals(item.getIntAttrName());
            }
        });
    }

    public static <T extends MappingItem> Collection<T> getMatchingMappingItems(
            final Collection<T> items, final String intAttrName) {

        return CollectionUtils.select(items, new Predicate<T>() {

            @Override
            public boolean evaluate(final T item) {
                return intAttrName.equals(item.getIntAttrName());
            }
        });
    }

    public static MappingItem getConnObjectKeyItem(final Provision provision) {
        Mapping mapping = null;
        if (provision != null) {
            mapping = provision.getMapping();
        }

        return mapping == null
                ? null
                : mapping.getConnObjectKeyItem();
    }

    private static List<MappingItem> getMappingItems(final Provision provision, final MappingPurpose purpose) {
        List<? extends MappingItem> items = Collections.<MappingItem>emptyList();
        if (provision != null) {
            items = provision.getMapping().getItems();
        }

        List<MappingItem> result = new ArrayList<>();

        switch (purpose) {
            case SYNCHRONIZATION:
                for (MappingItem item : items) {
                    if (MappingPurpose.PROPAGATION != item.getPurpose()
                            && MappingPurpose.NONE != item.getPurpose()) {

                        result.add(item);
                    }
                }
                break;

            case PROPAGATION:
                for (MappingItem item : items) {
                    if (MappingPurpose.SYNCHRONIZATION != item.getPurpose()
                            && MappingPurpose.NONE != item.getPurpose()) {

                        result.add(item);
                    }
                }
                break;

            case BOTH:
                for (MappingItem item : items) {
                    if (MappingPurpose.NONE != item.getPurpose()) {
                        result.add(item);
                    }
                }
                break;

            case NONE:
                for (MappingItem item : items) {
                    if (MappingPurpose.NONE == item.getPurpose()) {
                        result.add(item);
                    }
                }
                break;

            default:
        }

        return result;
    }

    public static List<MappingItem> getBothMappingItems(final Provision provision) {
        return getMappingItems(provision, MappingPurpose.BOTH);
    }

    public static List<MappingItem> getPropagationMappingItems(final Provision provision) {
        return getMappingItems(provision, MappingPurpose.PROPAGATION);
    }

    public static List<MappingItem> getSyncMappingItems(final Provision provision) {
        return getMappingItems(provision, MappingPurpose.SYNCHRONIZATION);
    }

    /**
     * Build __NAME__ for propagation. First look if there ia a defined connObjectLink for the given resource (and in
     * this case evaluate as JEXL); otherwise, take given connObjectKey.
     *
     * @param any given any object
     * @param provision external resource
     * @param connObjectKey connector object key
     * @return the value to be propagated as __NAME__
     */
    public static Name evaluateNAME(final Any<?, ?, ?> any, final Provision provision, final String connObjectKey) {
        if (StringUtils.isBlank(connObjectKey)) {
            // LOG error but avoid to throw exception: leave it to the external resource
            LOG.error("Missing ConnObjectKey for '{}': ", provision.getResource());
        }

        // Evaluate connObjectKey expression
        String connObjectLink = provision == null || provision.getMapping() == null
                ? null
                : provision.getMapping().getConnObjectLink();
        String evalConnObjectLink = null;
        if (StringUtils.isNotBlank(connObjectLink)) {
            JexlContext jexlContext = new MapContext();
            JexlUtils.addFieldsToContext(any, jexlContext);
            JexlUtils.addPlainAttrsToContext(any.getPlainAttrs(), jexlContext);
            JexlUtils.addDerAttrsToContext(any.getDerAttrs(), any.getPlainAttrs(), jexlContext);
            evalConnObjectLink = JexlUtils.evaluate(connObjectLink, jexlContext);
        }

        // If connObjectLink evaluates to an empty string, just use the provided connObjectKey as Name(),
        // otherwise evaluated connObjectLink expression is taken as Name().
        Name name;
        if (StringUtils.isBlank(evalConnObjectLink)) {
            // add connObjectKey as __NAME__ attribute ...
            LOG.debug("Add connObjectKey [{}] as __NAME__", connObjectKey);
            name = new Name(connObjectKey);
        } else {
            LOG.debug("Add connObjectLink [{}] as __NAME__", evalConnObjectLink);
            name = new Name(evalConnObjectLink);

            // connObjectKey not propagated: it will be used to set the value for __UID__ attribute
            LOG.debug("connObjectKey will be used just as __UID__ attribute");
        }

        return name;
    }

    public static List<MappingItemTransformer> getMappingItemTransformers(final MappingItem mappingItem) {
        List<MappingItemTransformer> result = new ArrayList<>();

        for (String className : mappingItem.getMappingItemTransformerClassNames()) {
            try {
                Class<?> transformerClass = ClassUtils.getClass(className);

                result.add((MappingItemTransformer) ApplicationContextProvider.
                        getBeanFactory().
                        createBean(transformerClass, AbstractBeanDefinition.AUTOWIRE_BY_NAME, false));
            } catch (Exception e) {
                LOG.error("Could not instantiate {}, ignoring...", className, e);
            }
        }

        return result;
    }

    /**
     * Prepare attributes for sending to a connector instance.
     *
     * @param any given any object
     * @param password clear-text password
     * @param changePwd whether password should be included for propagation attributes or not
     * @param vAttrs virtual attributes to be managed
     * @param enable whether any object must be enabled or not
     * @param provision provision information
     * @return connObjectLink + prepared attributes
     */
    @Transactional(readOnly = true)
    public Pair<String, Set<Attribute>> prepareAttrs(
            final Any<?, ?, ?> any,
            final String password,
            final boolean changePwd,
            final Map<String, AttrPatch> vAttrs,
            final Boolean enable,
            final Provision provision) {

        LOG.debug("Preparing resource attributes for {} with provision {} for attributes {}",
                any, provision, any.getPlainAttrs());

        Set<Attribute> attributes = new HashSet<>();
        String connObjectKey = null;

        for (MappingItem mapping : getMappingItems(provision, MappingPurpose.PROPAGATION)) {
            LOG.debug("Processing schema {}", mapping.getIntAttrName());

            try {
                if (mapping.getIntMappingType() == IntMappingType.UserVirtualSchema
                        || mapping.getIntMappingType() == IntMappingType.GroupVirtualSchema
                        || mapping.getIntMappingType() == IntMappingType.AnyObjectVirtualSchema) {

                    LOG.debug("Expire entry cache {}-{}", any.getKey(), mapping.getIntAttrName());
                    virAttrCache.expire(any.getType().getKey(), any.getKey(), mapping.getIntAttrName());
                }

                Pair<String, Attribute> preparedAttr = prepareAttr(provision, mapping, any, password, vAttrs);

                if (preparedAttr != null && preparedAttr.getKey() != null) {
                    connObjectKey = preparedAttr.getKey();
                }

                if (preparedAttr != null && preparedAttr.getValue() != null) {
                    Attribute alreadyAdded = AttributeUtil.find(preparedAttr.getValue().getName(), attributes);

                    if (alreadyAdded == null) {
                        attributes.add(preparedAttr.getValue());
                    } else {
                        attributes.remove(alreadyAdded);

                        Set<Object> values = new HashSet<>(alreadyAdded.getValue());
                        values.addAll(preparedAttr.getValue().getValue());

                        attributes.add(AttributeBuilder.build(preparedAttr.getValue().getName(), values));
                    }
                }
            } catch (Exception e) {
                LOG.debug("Attribute '{}' processing failed", mapping.getIntAttrName(), e);
            }
        }

        Attribute connObjectKeyExtAttr =
                AttributeUtil.find(getConnObjectKeyItem(provision).getExtAttrName(), attributes);
        if (connObjectKeyExtAttr != null) {
            attributes.remove(connObjectKeyExtAttr);
            attributes.add(AttributeBuilder.build(getConnObjectKeyItem(provision).getExtAttrName(), connObjectKey));
        }
        attributes.add(evaluateNAME(any, provision, connObjectKey));

        if (enable != null) {
            attributes.add(AttributeBuilder.buildEnabled(enable));
        }
        if (!changePwd) {
            Attribute pwdAttr = AttributeUtil.find(OperationalAttributes.PASSWORD_NAME, attributes);
            if (pwdAttr != null) {
                attributes.remove(pwdAttr);
            }
        }

        return new ImmutablePair<>(connObjectKey, attributes);
    }

    /**
     * Prepare an attribute to be sent to a connector instance.
     *
     * @param provision external resource
     * @param mapItem mapping item for the given attribute
     * @param any any object
     * @param password clear-text password
     * @param vAttrs virtual attributes to be managed
     * @return connObjectLink + prepared attribute
     */
    private Pair<String, Attribute> prepareAttr(
            final Provision provision, final MappingItem mapItem,
            final Any<?, ?, ?> any, final String password,
            final Map<String, AttrPatch> vAttrs) {

        List<Any<?, ?, ?>> anys = new ArrayList<>();

        switch (mapItem.getIntMappingType().getAnyTypeKind()) {
            case USER:
                if (any instanceof User) {
                    anys.add(any);
                }
                break;

            case GROUP:
                if (any instanceof User) {
                    for (Group group : userDAO.findAllGroups((User) any)) {
                        virAttrHandler.retrieveVirAttrValues(group);
                        anys.add(group);
                    }
                } else if (any instanceof Group) {
                    anys.add(any);
                }
                break;

            case ANY_OBJECT:
                if (any instanceof AnyObject) {
                    anys.add(any);
                }
                break;

            default:
        }

        List<PlainAttrValue> values = getIntValues(provision, mapItem, anys, vAttrs);

        Schema schema = null;
        boolean readOnlyVirSchema = false;
        AttrSchemaType schemaType;
        Pair<String, Attribute> result;

        switch (mapItem.getIntMappingType()) {
            case UserPlainSchema:
            case GroupPlainSchema:
            case AnyObjectPlainSchema:
                schema = plainSchemaDAO.find(mapItem.getIntAttrName());
                schemaType = schema == null ? AttrSchemaType.String : schema.getType();
                break;

            case UserVirtualSchema:
            case GroupVirtualSchema:
            case AnyObjectVirtualSchema:
                schema = virSchemaDAO.find(mapItem.getIntAttrName());
                readOnlyVirSchema = (schema != null && schema.isReadonly());
                schemaType = AttrSchemaType.String;
                break;

            default:
                schemaType = AttrSchemaType.String;
        }

        String extAttrName = mapItem.getExtAttrName();

        LOG.debug("Define mapping for: "
                + "\n* ExtAttrName " + extAttrName
                + "\n* is connObjectKey " + mapItem.isConnObjectKey()
                + "\n* is password " + (mapItem.isPassword() || mapItem.getIntMappingType() == IntMappingType.Password)
                + "\n* mandatory condition " + mapItem.getMandatoryCondition()
                + "\n* Schema " + mapItem.getIntAttrName()
                + "\n* IntMappingType " + mapItem.getIntMappingType().toString()
                + "\n* ClassType " + schemaType.getType().getName()
                + "\n* Values " + values);

        if (readOnlyVirSchema) {
            result = null;
        } else {
            List<Object> objValues = new ArrayList<>();

            for (PlainAttrValue value : values) {
                if (FrameworkUtil.isSupportedAttributeType(schemaType.getType())) {
                    objValues.add(value.getValue());
                } else {
                    objValues.add(value.getValueAsString());
                }
            }

            if (mapItem.isConnObjectKey()) {
                result = new ImmutablePair<>(objValues.iterator().next().toString(), null);
            } else if (mapItem.isPassword() && any instanceof User) {
                String passwordAttrValue = password;
                if (StringUtils.isBlank(passwordAttrValue)) {
                    User user = (User) any;
                    if (user.canDecodePassword()) {
                        try {
                            passwordAttrValue = ENCRYPTOR.decode(user.getPassword(), user.getCipherAlgorithm());
                        } catch (Exception e) {
                            LOG.error("Could not decode password for {}", user, e);
                        }
                    } else if (provision.getResource().isRandomPwdIfNotProvided()) {
                        try {
                            passwordAttrValue = passwordGenerator.generate(user);
                        } catch (InvalidPasswordRuleConf e) {
                            LOG.error("Could not generate policy-compliant random password for {}", user, e);
                        }
                    }
                }

                if (passwordAttrValue == null) {
                    result = null;
                } else {
                    result = new ImmutablePair<>(
                            null,
                            AttributeBuilder.buildPassword(passwordAttrValue.toCharArray()));
                }
            } else {
                if ((schema != null && schema.isMultivalue())
                        || anyUtilsFactory.getInstance(any).getAnyTypeKind()
                        != mapItem.getIntMappingType().getAnyTypeKind()) {

                    result = new ImmutablePair<>(
                            null,
                            AttributeBuilder.build(extAttrName, objValues));
                } else {
                    result = new ImmutablePair<>(
                            null, objValues.isEmpty()
                                    ? AttributeBuilder.build(extAttrName)
                                    : AttributeBuilder.build(extAttrName, objValues.iterator().next()));
                }
            }
        }

        return result;
    }

    private String getGroupOwnerValue(final Provision provision, final Any<?, ?, ?> any) {
        Pair<String, Attribute> preparedAttr = prepareAttr(
                provision, getConnObjectKeyItem(provision), any, null, Collections.<String, AttrPatch>emptyMap());
        String connObjectKey = preparedAttr.getKey();

        return evaluateNAME(any, provision, connObjectKey).getNameValue();
    }

    /**
     * Get attribute values for the given {@link MappingItem} and any objects.
     *
     * @param provision provision information
     * @param mappingItem mapping item
     * @param anys any objects
     * @param vAttrs virtual attributes to be managed
     * @return attribute values.
     */
    @Transactional(readOnly = true)
    public List<PlainAttrValue> getIntValues(final Provision provision,
            final MappingItem mappingItem, final List<Any<?, ?, ?>> anys, final Map<String, AttrPatch> vAttrs) {

        LOG.debug("Get attributes for '{}' and mapping type '{}'", anys, mappingItem.getIntMappingType());

        boolean transform = true;

        List<PlainAttrValue> values = new ArrayList<>();
        switch (mappingItem.getIntMappingType()) {
            case UserPlainSchema:
            case GroupPlainSchema:
            case AnyObjectPlainSchema:
                for (Any<?, ?, ?> any : anys) {
                    PlainAttr<?> attr = any.getPlainAttr(mappingItem.getIntAttrName());
                    if (attr != null) {
                        if (attr.getUniqueValue() != null) {
                            PlainAttrUniqueValue value = SerializationUtils.clone(attr.getUniqueValue());
                            value.setAttr(null);
                            values.add(value);
                        } else if (attr.getValues() != null) {
                            for (PlainAttrValue value : attr.getValues()) {
                                PlainAttrValue shadow = SerializationUtils.clone(value);
                                shadow.setAttr(null);
                                values.add(shadow);
                            }
                        }
                    }

                    LOG.debug("Retrieved attribute {}"
                            + "\n* IntAttrName {}"
                            + "\n* IntMappingType {}"
                            + "\n* Attribute values {}",
                            attr, mappingItem.getIntAttrName(), mappingItem.getIntMappingType(), values);
                }

                break;

            case UserVirtualSchema:
            case GroupVirtualSchema:
            case AnyObjectVirtualSchema:
                // virtual attributes don't get transformed
                transform = false;

                for (Any<?, ?, ?> any : anys) {
                    AnyUtils anyUtils = anyUtilsFactory.getInstance(any);
                    VirAttr<?> attr = any.getVirAttr(mappingItem.getIntAttrName());
                    if (attr != null) {
                        if (vAttrs != null) {
                            if (vAttrs.containsKey(mappingItem.getIntAttrName())) {
                                attr.getValues().clear();
                                attr.getValues().addAll(
                                        vAttrs.get(mappingItem.getIntAttrName()).getAttrTO().getValues());
                            } else {
                                throw new IllegalArgumentException("Don't need to update virtual attribute '"
                                        + mappingItem.getIntAttrName() + "'");
                            }
                        }
                        for (String value : attr.getValues()) {
                            PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                            attrValue.setStringValue(value);
                            values.add(attrValue);
                        }
                    }

                    LOG.debug("Retrieved {} virtual attribute {}"
                            + "\n* IntAttrName {}"
                            + "\n* IntMappingType {}"
                            + "\n* Attribute values {}",
                            any.getClass().getSimpleName(),
                            attr, mappingItem.getIntAttrName(), mappingItem.getIntMappingType(), values);
                }
                break;

            case UserDerivedSchema:
            case GroupDerivedSchema:
            case AnyObjectDerivedSchema:
                for (Any<?, ?, ?> any : anys) {
                    AnyUtils anyUtils = anyUtilsFactory.getInstance(any);
                    DerAttr<?> attr = any.getDerAttr(mappingItem.getIntAttrName());
                    if (attr != null) {
                        PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                        attrValue.setStringValue(attr.getValue(any.getPlainAttrs()));
                        values.add(attrValue);
                    }

                    LOG.debug("Retrieved attribute {}"
                            + "\n* IntAttrName {}"
                            + "\n* IntMappingType {}"
                            + "\n* Attribute values {}",
                            attr, mappingItem.getIntAttrName(), mappingItem.getIntMappingType(), values);
                }
                break;

            case UserKey:
            case GroupKey:
            case AnyObjectKey:
                for (Any<?, ?, ?> any : anys) {
                    AnyUtils anyUtils = anyUtilsFactory.getInstance(any);
                    PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                    attrValue.setStringValue(any.getKey().toString());
                    values.add(attrValue);
                }
                break;

            case Username:
                for (Any<?, ?, ?> any : anys) {
                    if (any instanceof User) {
                        UPlainAttrValue attrValue = entityFactory.newEntity(UPlainAttrValue.class);
                        attrValue.setStringValue(((User) any).getUsername());
                        values.add(attrValue);
                    }
                }
                break;

            case GroupName:
                for (Any<?, ?, ?> any : anys) {
                    if (any instanceof Group) {
                        GPlainAttrValue attrValue = entityFactory.newEntity(GPlainAttrValue.class);
                        attrValue.setStringValue(((Group) any).getName());
                        values.add(attrValue);
                    }
                }
                break;

            case GroupOwnerSchema:
                Mapping uMapping = provision.getAnyType().equals(anyTypeDAO.findUser())
                        ? null
                        : provision.getMapping();
                Mapping gMapping = provision.getAnyType().equals(anyTypeDAO.findGroup())
                        ? null
                        : provision.getMapping();

                for (Any<?, ?, ?> any : anys) {
                    if (any instanceof Group) {
                        Group group = (Group) any;
                        String groupOwnerValue = null;
                        if (group.getUserOwner() != null && uMapping != null) {
                            groupOwnerValue = getGroupOwnerValue(provision, group.getUserOwner());
                        }
                        if (group.getGroupOwner() != null && gMapping != null) {
                            groupOwnerValue = getGroupOwnerValue(provision, group.getGroupOwner());
                        }

                        if (StringUtils.isNotBlank(groupOwnerValue)) {
                            GPlainAttrValue attrValue = entityFactory.newEntity(GPlainAttrValue.class);
                            attrValue.setStringValue(groupOwnerValue);
                            values.add(attrValue);
                        }
                    }
                }
                break;

            default:
        }

        LOG.debug("Values for propagation: {}", values);

        List<PlainAttrValue> transformed = values;
        if (transform) {
            for (MappingItemTransformer transformer : getMappingItemTransformers(mappingItem)) {
                transformed = transformer.beforePropagation(transformed);
            }
            LOG.debug("Transformed values for propagation: {}", values);
        } else {
            LOG.debug("No transformation occurred");
        }

        return transformed;
    }

    /**
     * Get connObjectKey internal value.
     *
     * @param any any object
     * @param provision provision information
     * @return connObjectKey internal value
     */
    @Transactional(readOnly = true)
    public String getConnObjectKeyValue(final Any<?, ?, ?> any, final Provision provision) {
        List<PlainAttrValue> values = getIntValues(provision, provision.getMapping().getConnObjectKeyItem(),
                Collections.<Any<?, ?, ?>>singletonList(any), null);
        return values == null || values.isEmpty()
                ? null
                : values.get(0).getValueAsString();
    }

    /**
     * Set attribute values, according to the given {@link MappingItem}, to any object from attribute received from
     * connector.
     *
     * @param <T> any object
     * @param mappingItem mapping item
     * @param attr attribute received from connector
     * @param anyTO any object
     * @param anyUtils any utils
     */
    @Transactional(readOnly = true)
    public <T extends AnyTO> void setIntValues(
            final MappingItem mappingItem, final Attribute attr, final T anyTO, final AnyUtils anyUtils) {

        List<Object> values = null;
        if (attr != null) {
            values = attr.getValue();
            for (MappingItemTransformer transformer : getMappingItemTransformers(mappingItem)) {
                values = transformer.beforeSync(values);
            }
        }
        values = ListUtils.emptyIfNull(values);

        switch (mappingItem.getIntMappingType()) {
            case UserKey:
            case GroupKey:
            case AnyObjectKey:
                break;

            case Password:
                if (anyTO instanceof UserTO && !values.isEmpty()) {
                    ((UserTO) anyTO).setPassword(ConnObjectUtils.getPassword(values.get(0)));
                }
                break;

            case Username:
                if (anyTO instanceof UserTO) {
                    ((UserTO) anyTO).setUsername(values.isEmpty() || values.get(0) == null
                            ? null
                            : values.get(0).toString());
                }
                break;

            case GroupName:
                if (anyTO instanceof GroupTO) {
                    ((GroupTO) anyTO).setName(values.isEmpty() || values.get(0) == null
                            ? null
                            : values.get(0).toString());
                }
                break;

            case GroupOwnerSchema:
                if (anyTO instanceof GroupTO && attr != null) {
                    // using a special attribute (with schema "", that will be ignored) for carrying the
                    // GroupOwnerSchema value
                    AttrTO attrTO = new AttrTO();
                    attrTO.setSchema(StringUtils.EMPTY);
                    if (values.isEmpty() || values.get(0) == null) {
                        attrTO.getValues().add(StringUtils.EMPTY);
                    } else {
                        attrTO.getValues().add(values.get(0).toString());
                    }

                    ((GroupTO) anyTO).getPlainAttrs().add(attrTO);
                }
                break;

            case UserPlainSchema:
            case GroupPlainSchema:
            case AnyObjectPlainSchema:
                AttrTO attrTO = new AttrTO();
                attrTO.setSchema(mappingItem.getIntAttrName());

                PlainSchema schema = plainSchemaDAO.find(mappingItem.getIntAttrName());

                for (Object value : values) {
                    AttrSchemaType schemaType = schema == null ? AttrSchemaType.String : schema.getType();
                    if (value != null) {
                        PlainAttrValue attrValue = anyUtils.newPlainAttrValue();
                        switch (schemaType) {
                            case String:
                                attrValue.setStringValue(value.toString());
                                break;

                            case Binary:
                                attrValue.setBinaryValue((byte[]) value);
                                break;

                            default:
                                try {
                                    attrValue.parseValue(schema, value.toString());
                                } catch (ParsingValidationException e) {
                                    LOG.error("While parsing provided value {}", value, e);
                                    attrValue.setStringValue(value.toString());
                                    schemaType = AttrSchemaType.String;
                                }
                                break;
                        }
                        attrTO.getValues().add(attrValue.getValueAsString(schemaType));
                    }
                }

                anyTO.getPlainAttrs().add(attrTO);
                break;

            case UserDerivedSchema:
            case GroupDerivedSchema:
            case AnyObjectDerivedSchema:
                attrTO = new AttrTO();
                attrTO.setSchema(mappingItem.getIntAttrName());
                anyTO.getDerAttrs().add(attrTO);
                break;

            case UserVirtualSchema:
            case GroupVirtualSchema:
            case AnyObjectVirtualSchema:
                attrTO = new AttrTO();
                attrTO.setSchema(mappingItem.getIntAttrName());

                // virtual attributes don't get transformed, iterate over original attr.getValue()
                for (Object value : (attr == null || attr.getValue() == null)
                        ? Collections.emptyList() : attr.getValue()) {

                    if (value != null) {
                        attrTO.getValues().add(value.toString());
                    }
                }

                anyTO.getVirAttrs().add(attrTO);
                break;

            default:
        }
    }

}
