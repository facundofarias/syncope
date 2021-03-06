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
package org.apache.syncope.core.persistence.jpa.validation.entity;

import java.util.HashSet;
import java.util.Set;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.types.EntityViolationType;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.resource.Mapping;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.provisioning.api.data.MappingItemTransformer;
import org.apache.syncope.core.provisioning.api.propagation.PropagationActions;

public class ExternalResourceValidator extends AbstractValidator<ExternalResourceCheck, ExternalResource> {

    private boolean isValid(final MappingItem item, final ConstraintValidatorContext context) {
        if (StringUtils.isBlank(item.getExtAttrName())) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, item + ".extAttrName is null")).
                    addPropertyNode("extAttrName").addConstraintViolation();

            return false;
        }

        if (StringUtils.isBlank(item.getIntAttrName())) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, item + ".intAttrName is null")).
                    addPropertyNode("intAttrName").addConstraintViolation();

            return false;
        }

        if (item.getPurpose() == null) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, item + ".purpose is null")).
                    addPropertyNode("purpose").addConstraintViolation();

            return false;
        }

        return true;
    }

    private boolean isValid(final AnyType anyType, final Mapping mapping, final ConstraintValidatorContext context) {
        if (mapping == null) {
            return true;
        }

        int connObjectKeys = CollectionUtils.countMatches(mapping.getItems(), new Predicate<MappingItem>() {

            @Override
            public boolean evaluate(final MappingItem item) {
                return item.isConnObjectKey();
            }
        });
        if (connObjectKeys != 1) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, "Single ConnObjectKey mapping is required")).
                    addPropertyNode("connObjectKey.size").addConstraintViolation();
            return false;
        }

        MappingItem connObjectKey = mapping.getConnObjectKeyItem();
        if (connObjectKey.getIntMappingType().getAnyTypeKind() != anyType.getKind()) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, "ConnObjectKey must be from the same AnyTypeKind")).
                    addPropertyNode("anyTypeKind").addConstraintViolation();
            return false;
        }

        boolean isValid = true;

        int passwords = 0;
        for (MappingItem item : mapping.getItems()) {
            isValid &= isValid(item, context);

            if (item.isPassword()) {
                passwords++;
            }
        }
        if (passwords > 1) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidMapping, "One password mapping is allowed at most")).
                    addPropertyNode("password.size").addConstraintViolation();
            isValid = false;
        }

        for (MappingItem item : mapping.getItems()) {
            for (String className : item.getMappingItemTransformerClassNames()) {
                Class<?> actionsClass = null;
                boolean isAssignable = false;
                try {
                    actionsClass = Class.forName(className);
                    isAssignable = MappingItemTransformer.class.isAssignableFrom(actionsClass);
                } catch (Exception e) {
                    LOG.error("Invalid MappingItemTransformer specified: {}", className, e);
                }

                if (actionsClass == null || !isAssignable) {
                    context.buildConstraintViolationWithTemplate(
                            getTemplate(EntityViolationType.InvalidMapping,
                                    "Invalid mapping item trasformer class name")).
                            addPropertyNode("mappingItemTransformerClassName").addConstraintViolation();
                    isValid = false;
                }
            }
        }

        return isValid;
    }

    @Override
    public boolean isValid(final ExternalResource resource, final ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();

        if (!NAME_PATTERN.matcher(resource.getKey()).matches()) {
            context.buildConstraintViolationWithTemplate(
                    getTemplate(EntityViolationType.InvalidName, "Invalid Resource name")).
                    addPropertyNode("name").addConstraintViolation();
            return false;
        }

        if (!resource.getPropagationActionsClassNames().isEmpty()) {
            for (String className : resource.getPropagationActionsClassNames()) {
                Class<?> actionsClass = null;
                boolean isAssignable = false;
                try {
                    actionsClass = Class.forName(className);
                    isAssignable = PropagationActions.class.isAssignableFrom(actionsClass);
                } catch (Exception e) {
                    LOG.error("Invalid PropagationActions specified: {}", className, e);
                }

                if (actionsClass == null || !isAssignable) {
                    context.buildConstraintViolationWithTemplate(
                            getTemplate(EntityViolationType.InvalidResource, "Invalid actions class name")).
                            addPropertyNode("actionsClassName").addConstraintViolation();
                    return false;
                }
            }
        }

        final Set<String> objectClasses = new HashSet<>();
        boolean validMappings = CollectionUtils.matchesAll(resource.getProvisions(),
                new Predicate<Provision>() {

                    @Override
                    public boolean evaluate(final Provision provision) {
                        if (provision.getObjectClass() != null) {
                            objectClasses.add(provision.getObjectClass().getObjectClassValue());
                        }
                        return isValid(provision.getAnyType(), provision.getMapping(), context);
                    }
                });

        if (objectClasses.size() < resource.getProvisions().size()) {
            context.buildConstraintViolationWithTemplate(getTemplate(EntityViolationType.InvalidResource,
                    "Each provision requires a different ObjectClass")).
                    addPropertyNode("provisions").addConstraintViolation();
            return false;
        }

        return validMappings;
    }
}
