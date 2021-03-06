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
package org.apache.syncope.core.provisioning.java.data;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.StringUtils;
import org.apache.syncope.common.lib.SyncopeClientCompositeException;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.patch.LongPatchItem;
import org.apache.syncope.common.lib.patch.MembershipPatch;
import org.apache.syncope.common.lib.patch.PasswordPatch;
import org.apache.syncope.common.lib.patch.RelationshipPatch;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.to.MembershipTO;
import org.apache.syncope.common.lib.to.RelationshipTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.common.lib.types.PatchOperation;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.core.persistence.api.dao.ConfDAO;
import org.apache.syncope.core.persistence.api.dao.SecurityQuestionDAO;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.user.SecurityQuestion;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.common.lib.types.PropagationByResource;
import org.apache.syncope.core.provisioning.api.data.UserDataBinder;
import org.apache.syncope.core.misc.security.AuthContextUtils;
import org.apache.syncope.core.misc.security.Encryptor;
import org.apache.syncope.core.misc.spring.BeanUtils;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.RoleDAO;
import org.apache.syncope.core.persistence.api.entity.RelationshipType;
import org.apache.syncope.core.persistence.api.entity.Role;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.persistence.api.entity.user.UMembership;
import org.apache.syncope.core.persistence.api.entity.user.URelationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(rollbackFor = { Throwable.class })
public class UserDataBinderImpl extends AbstractAnyDataBinder implements UserDataBinder {

    private static final String[] IGNORE_PROPERTIES = {
        "type", "realm", "auxClasses", "roles", "dynRoles", "relationships", "memberships", "dynGroups",
        "plainAttrs", "derAttrs", "virAttrs", "resources", "securityQuestion", "securityAnswer"
    };

    @Autowired
    private RoleDAO roleDAO;

    @Autowired
    private ConfDAO confDAO;

    @Autowired
    private SecurityQuestionDAO securityQuestionDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Resource(name = "adminUser")
    private String adminUser;

    @Resource(name = "anonymousUser")
    private String anonymousUser;

    private final Encryptor encryptor = Encryptor.getInstance();

    @Transactional(readOnly = true)
    @Override
    public UserTO getAuthenticatedUserTO() {
        final UserTO authUserTO;

        String authUsername = AuthContextUtils.getUsername();
        if (anonymousUser.equals(authUsername)) {
            authUserTO = new UserTO();
            authUserTO.setKey(-2);
            authUserTO.setUsername(anonymousUser);
        } else if (adminUser.equals(authUsername)) {
            authUserTO = new UserTO();
            authUserTO.setKey(-1);
            authUserTO.setUsername(adminUser);
        } else {
            User authUser = userDAO.find(authUsername);
            authUserTO = getUserTO(authUser, true);
        }

        return authUserTO;
    }

    @Transactional(readOnly = true)
    @Override
    public boolean verifyPassword(final String username, final String password) {
        return verifyPassword(userDAO.authFind(username), password);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean verifyPassword(final User user, final String password) {
        return encryptor.verify(password, user.getCipherAlgorithm(), user.getPassword());
    }

    private void setPassword(final User user, final String password, final SyncopeClientCompositeException scce) {
        try {
            String algorithm = confDAO.find(
                    "password.cipher.algorithm", CipherAlgorithm.AES.name()).getValues().get(0).getStringValue();
            CipherAlgorithm predefined = CipherAlgorithm.valueOf(algorithm);
            user.setPassword(password, predefined);
        } catch (IllegalArgumentException e) {
            final SyncopeClientException invalidCiperAlgorithm =
                    SyncopeClientException.build(ClientExceptionType.NotFound);
            invalidCiperAlgorithm.getElements().add(e.getMessage());
            scce.addException(invalidCiperAlgorithm);

            throw scce;
        }
    }

    @Override
    public void create(final User user, final UserTO userTO, final boolean storePassword) {
        SyncopeClientCompositeException scce = SyncopeClientException.buildComposite();

        // roles
        for (Long roleKey : userTO.getRoles()) {
            Role role = roleDAO.find(roleKey);
            if (role == null) {
                LOG.warn("Ignoring unknown role with id {}", roleKey);
            } else {
                user.add(role);
            }
        }

        // relationships
        for (RelationshipTO relationshipTO : userTO.getRelationships()) {
            AnyObject anyObject = anyObjectDAO.find(relationshipTO.getRightKey());
            if (anyObject == null) {
                LOG.debug("Ignoring invalid anyObject " + relationshipTO.getRightKey());
            } else {
                RelationshipType relationshipType = relationshipTypeDAO.find(relationshipTO.getType());
                URelationship relationship = null;
                if (user.getKey() != null) {
                    relationship = user.getRelationship(relationshipType, anyObject.getKey());
                }
                if (relationship == null) {
                    relationship = entityFactory.newEntity(URelationship.class);
                    relationship.setType(relationshipType);
                    relationship.setRightEnd(anyObject);
                    relationship.setLeftEnd(user);

                    user.add(relationship);
                }
            }
        }

        // memberships
        for (MembershipTO membershipTO : userTO.getMemberships()) {
            Group group = groupDAO.find(membershipTO.getRightKey());

            if (group == null) {
                LOG.debug("Ignoring invalid group " + membershipTO.getGroupName());
            } else {
                UMembership membership = null;
                if (user.getKey() != null) {
                    membership = user.getMembership(group.getKey());
                }
                if (membership == null) {
                    membership = entityFactory.newEntity(UMembership.class);
                    membership.setRightEnd(group);
                    membership.setLeftEnd(user);

                    user.add(membership);
                }
            }
        }

        // realm, attributes, derived attributes, virtual attributes and resources
        fill(user, userTO, anyUtilsFactory.getInstance(AnyTypeKind.USER), scce);

        // set password
        if (StringUtils.isBlank(userTO.getPassword()) || !storePassword) {
            LOG.debug("Password was not provided or not required to be stored");
        } else {
            setPassword(user, userTO.getPassword(), scce);
        }

        // set username
        user.setUsername(userTO.getUsername());

        // security question / answer
        if (userTO.getSecurityQuestion() != null) {
            SecurityQuestion securityQuestion = securityQuestionDAO.find(userTO.getSecurityQuestion());
            if (securityQuestion != null) {
                user.setSecurityQuestion(securityQuestion);
            }
        }
        user.setSecurityAnswer(userTO.getSecurityAnswer());

        user.setMustChangePassword(userTO.isMustChangePassword());
    }

    private boolean isPasswordMapped(final ExternalResource resource) {
        boolean result = false;

        Provision provision = resource.getProvision(anyTypeDAO.findUser());
        if (provision != null && provision.getMapping() != null) {
            result = CollectionUtils.exists(provision.getMapping().getItems(), new Predicate<MappingItem>() {

                @Override
                public boolean evaluate(final MappingItem item) {
                    return item.isPassword();
                }
            });
        }

        return result;
    }

    @Override
    public PropagationByResource update(final User toBeUpdated, final UserPatch userPatch) {
        // Re-merge any pending change from workflow tasks
        final User user = userDAO.save(toBeUpdated);

        PropagationByResource propByRes = new PropagationByResource();

        SyncopeClientCompositeException scce = SyncopeClientException.buildComposite();

        Collection<String> currentResources = userDAO.findAllResourceNames(user);

        // fetch connObjectKeys before update
        Map<String, String> oldConnObjectKeys = getConnObjectKeys(user);

        // realm
        setRealm(user, userPatch);

        // password
        if (userPatch.getPassword() != null && StringUtils.isNotBlank(userPatch.getPassword().getValue())) {
            setPassword(user, userPatch.getPassword().getValue(), scce);
            user.setChangePwdDate(new Date());
            propByRes.addAll(ResourceOperation.UPDATE, currentResources);
        }

        // username
        if (userPatch.getUsername() != null && StringUtils.isNotBlank(userPatch.getUsername().getValue())) {
            propByRes.addAll(ResourceOperation.UPDATE, currentResources);

            String oldUsername = user.getUsername();
            user.setUsername(userPatch.getUsername().getValue());

            if (oldUsername.equals(AuthContextUtils.getUsername())) {
                AuthContextUtils.updateUsername(userPatch.getUsername().getValue());
            }
        }

        // security question / answer:
        if (userPatch.getSecurityQuestion() != null) {
            if (userPatch.getSecurityQuestion().getValue() == null) {
                user.setSecurityQuestion(null);
                user.setSecurityAnswer(null);
            } else {
                SecurityQuestion securityQuestion =
                        securityQuestionDAO.find(userPatch.getSecurityQuestion().getValue());
                if (securityQuestion != null) {
                    user.setSecurityQuestion(securityQuestion);
                    user.setSecurityAnswer(userPatch.getSecurityAnswer().getValue());
                }
            }
        }

        if (userPatch.getMustChangePassword() != null) {
            user.setMustChangePassword(userPatch.getMustChangePassword().getValue());
        }

        // roles
        for (LongPatchItem patch : userPatch.getRoles()) {
            Role role = roleDAO.find(patch.getValue());
            if (role == null) {
                LOG.warn("Ignoring unknown role with key {}", patch.getValue());
            } else {
                switch (patch.getOperation()) {
                    case ADD_REPLACE:
                        user.add(role);
                        break;

                    case DELETE:
                    default:
                        user.remove(role);
                }
            }
        }

        // attributes, derived attributes, virtual attributes and resources
        propByRes.merge(fill(user, userPatch, anyUtilsFactory.getInstance(AnyTypeKind.USER), scce));

        Set<String> toBeDeprovisioned = new HashSet<>();
        Set<String> toBeProvisioned = new HashSet<>();

        // relationships
        for (RelationshipPatch patch : userPatch.getRelationships()) {
            if (patch.getRelationshipTO() != null) {
                RelationshipType relationshipType = relationshipTypeDAO.find(patch.getRelationshipTO().getType());
                URelationship relationship =
                        user.getRelationship(relationshipType, patch.getRelationshipTO().getRightKey());
                if (relationship != null) {
                    user.remove(relationship);
                    toBeDeprovisioned.addAll(relationship.getRightEnd().getResourceNames());
                }

                if (patch.getOperation() == PatchOperation.ADD_REPLACE) {
                    AnyObject otherEnd = anyObjectDAO.find(patch.getRelationshipTO().getRightKey());
                    if (otherEnd == null) {
                        LOG.debug("Ignoring invalid any object {}", patch.getRelationshipTO().getRightKey());
                    } else {

                        relationship = entityFactory.newEntity(URelationship.class);
                        relationship.setType(relationshipType);
                        relationship.setRightEnd(otherEnd);
                        relationship.setLeftEnd(user);

                        user.add(relationship);

                        toBeProvisioned.addAll(otherEnd.getResourceNames());
                    }
                }
            }
        }

        // memberships
        for (MembershipPatch patch : userPatch.getMemberships()) {
            if (patch.getMembershipTO() != null) {
                UMembership membership = user.getMembership(patch.getMembershipTO().getRightKey());
                if (membership != null) {
                    user.remove(membership);
                    toBeDeprovisioned.addAll(membership.getRightEnd().getResourceNames());
                }

                if (patch.getOperation() == PatchOperation.ADD_REPLACE) {
                    Group group = groupDAO.find(patch.getMembershipTO().getRightKey());
                    if (group == null) {
                        LOG.debug("Ignoring invalid group {}", patch.getMembershipTO().getRightKey());
                    } else {
                        membership = entityFactory.newEntity(UMembership.class);
                        membership.setRightEnd(group);
                        membership.setLeftEnd(user);

                        user.add(membership);

                        toBeProvisioned.addAll(group.getResourceNames());

                        // SYNCOPE-686: if password is invertible and we are adding resources with password mapping,
                        // ensure that they are counted for password propagation
                        if (toBeUpdated.canDecodePassword()) {
                            if (userPatch.getPassword() == null) {
                                userPatch.setPassword(new PasswordPatch());
                            }
                            for (ExternalResource resource : group.getResources()) {
                                if (isPasswordMapped(resource)) {
                                    userPatch.getPassword().getResources().add(resource.getKey());
                                }
                            }
                        }
                    }
                }
            }
        }

        propByRes.addAll(ResourceOperation.DELETE, toBeDeprovisioned);
        propByRes.addAll(ResourceOperation.UPDATE, toBeProvisioned);

        // In case of new memberships all current resources need to be updated in order to propagate new group
        // attribute values.
        if (!toBeDeprovisioned.isEmpty() || !toBeProvisioned.isEmpty()) {
            currentResources.removeAll(toBeDeprovisioned);
            propByRes.addAll(ResourceOperation.UPDATE, currentResources);
        }

        // check if some connObjectKey was changed by the update above
        Map<String, String> newcCnnObjectKeys = getConnObjectKeys(user);
        for (Map.Entry<String, String> entry : oldConnObjectKeys.entrySet()) {
            if (newcCnnObjectKeys.containsKey(entry.getKey())
                    && !entry.getValue().equals(newcCnnObjectKeys.get(entry.getKey()))) {

                propByRes.addOldConnObjectKey(entry.getKey(), entry.getValue());
                propByRes.add(ResourceOperation.UPDATE, entry.getKey());
            }
        }

        return propByRes;
    }

    @Transactional(readOnly = true)
    @Override
    public UserTO getUserTO(final User user, final boolean details) {
        UserTO userTO = new UserTO();

        BeanUtils.copyProperties(user, userTO, IGNORE_PROPERTIES);

        if (user.getSecurityQuestion() != null) {
            userTO.setSecurityQuestion(user.getSecurityQuestion().getKey());
        }

        if (details) {
            virAttrHander.retrieveVirAttrValues(user);
        }

        fillTO(userTO, user.getRealm().getFullPath(), user.getAuxClasses(),
                user.getPlainAttrs(), user.getDerAttrs(), user.getVirAttrs(), userDAO.findAllResources(user));

        if (details) {
            // roles
            CollectionUtils.collect(user.getRoles(), new Transformer<Role, Long>() {

                @Override
                public Long transform(final Role role) {
                    return role.getKey();
                }
            }, userTO.getRoles());

            // relationships
            CollectionUtils.collect(user.getRelationships(), new Transformer<URelationship, RelationshipTO>() {

                @Override
                public RelationshipTO transform(final URelationship relationship) {
                    return UserDataBinderImpl.this.getRelationshipTO(relationship);
                }

            }, userTO.getRelationships());

            // memberships
            CollectionUtils.collect(user.getMemberships(), new Transformer<UMembership, MembershipTO>() {

                @Override
                public MembershipTO transform(final UMembership membership) {
                    return UserDataBinderImpl.this.getMembershipTO(membership);
                }
            }, userTO.getMemberships());

            // dynamic memberships
            CollectionUtils.collect(userDAO.findDynRoleMemberships(user), new Transformer<Role, Long>() {

                @Override
                public Long transform(final Role role) {
                    return role.getKey();
                }
            }, userTO.getDynRoles());
            CollectionUtils.collect(userDAO.findDynGroupMemberships(user), new Transformer<Group, Long>() {

                @Override
                public Long transform(final Group group) {
                    return group.getKey();
                }
            }, userTO.getDynGroups());
        }

        return userTO;
    }

    @Transactional(readOnly = true)
    @Override
    public UserTO getUserTO(final String username) {
        return getUserTO(userDAO.authFind(username), true);
    }

    @Transactional(readOnly = true)
    @Override
    public UserTO getUserTO(final Long key) {
        return getUserTO(userDAO.authFind(key), true);
    }

}
