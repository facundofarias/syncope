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
package org.apache.syncope.core.persistence.jpa.dao;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.persistence.TypedQuery;
import org.apache.syncope.common.lib.types.IntMappingType;
import org.apache.syncope.common.lib.types.PolicyType;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.core.persistence.api.dao.AnyObjectDAO;
import org.apache.syncope.core.persistence.api.dao.ExternalResourceDAO;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.PolicyDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.TaskDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.policy.AccountPolicy;
import org.apache.syncope.core.persistence.api.entity.resource.ExternalResource;
import org.apache.syncope.core.persistence.api.entity.resource.MappingItem;
import org.apache.syncope.core.persistence.api.entity.Policy;
import org.apache.syncope.core.persistence.api.entity.anyobject.AnyObject;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.resource.Provision;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.persistence.jpa.entity.resource.JPAMappingItem;
import org.apache.syncope.core.persistence.jpa.entity.resource.JPAExternalResource;
import org.apache.syncope.core.persistence.jpa.entity.resource.JPAMapping;
import org.apache.syncope.core.provisioning.api.ConnectorRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JPAExternalResourceDAO extends AbstractDAO<ExternalResource, String> implements ExternalResourceDAO {

    @Autowired
    private TaskDAO taskDAO;

    @Autowired
    private AnyObjectDAO anyObjectDAO;

    @Autowired
    private UserDAO userDAO;

    @Autowired
    private GroupDAO groupDAO;

    @Autowired
    private PolicyDAO policyDAO;

    @Autowired
    private ConnectorRegistry connRegistry;

    @Override
    public ExternalResource find(final String name) {
        return entityManager().find(JPAExternalResource.class, name);
    }

    private StringBuilder getByPolicyQuery(final PolicyType type) {
        StringBuilder query = new StringBuilder("SELECT e FROM ").
                append(JPAExternalResource.class.getSimpleName()).
                append(" e WHERE e.");
        switch (type) {
            case ACCOUNT:
                query.append("accountPolicy");
                break;

            case PASSWORD:
                query.append("passwordPolicy");
                break;

            case SYNC:
                query.append("syncPolicy");
                break;

            default:
                break;
        }
        return query;
    }

    @Override
    public List<ExternalResource> findByPolicy(final Policy policy) {
        TypedQuery<ExternalResource> query = entityManager().createQuery(
                getByPolicyQuery(policy.getType()).append(" = :policy").toString(), ExternalResource.class);
        query.setParameter("policy", policy);
        return query.getResultList();
    }

    @Override
    public List<ExternalResource> findWithoutPolicy(final PolicyType type) {
        TypedQuery<ExternalResource> query = entityManager().createQuery(
                getByPolicyQuery(type).append(" IS NULL").toString(), ExternalResource.class);
        return query.getResultList();
    }

    @Override
    public List<ExternalResource> findAll() {
        TypedQuery<ExternalResource> query = entityManager().createQuery(
                "SELECT e FROM  " + JPAExternalResource.class.getSimpleName() + " e", ExternalResource.class);
        return query.getResultList();
    }

    @Override
    public List<ExternalResource> findAllByPriority() {
        TypedQuery<ExternalResource> query = entityManager().createQuery(
                "SELECT e FROM  " + JPAExternalResource.class.getSimpleName() + " e ORDER BY e.propagationPriority",
                ExternalResource.class);
        return query.getResultList();
    }

    /**
     * This method has an explicit Transactional annotation because it is called by SyncJob.
     *
     * @see org.apache.syncope.core.sync.impl.SyncJob
     *
     * @param resource entity to be merged
     * @return the same entity, updated
     */
    @Override
    @Transactional(rollbackFor = { Throwable.class })
    public ExternalResource save(final ExternalResource resource) {
        ExternalResource merged = entityManager().merge(resource);
        try {
            connRegistry.registerConnector(merged);
        } catch (NotFoundException e) {
            LOG.error("While registering connector for resource", e);
        }
        return merged;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void deleteMapping(final String intAttrName, final IntMappingType intMappingType) {
        if (IntMappingType.getEmbedded().contains(intMappingType)) {
            return;
        }

        TypedQuery<MappingItem> query = entityManager().createQuery(
                "SELECT m FROM " + JPAMappingItem.class.getSimpleName()
                + " m WHERE m.intAttrName=:intAttrName AND m.intMappingType=:intMappingType", MappingItem.class);
        query.setParameter("intAttrName", intAttrName);
        query.setParameter("intMappingType", intMappingType);

        Set<Long> itemKeys = new HashSet<>();
        for (MappingItem item : query.getResultList()) {
            itemKeys.add(item.getKey());
        }
        for (Long itemKey : itemKeys) {
            MappingItem item = entityManager().find(JPAMappingItem.class, itemKey);
            if (item != null) {
                item.getMapping().remove(item);
                item.setMapping(null);

                entityManager().remove(item);
            }
        }

        // Make empty query cache for *MappingItem and related *Mapping
        entityManager().getEntityManagerFactory().getCache().evict(JPAMappingItem.class);
        entityManager().getEntityManagerFactory().getCache().evict(JPAMapping.class);
    }

    @Override
    public void delete(final String name) {
        ExternalResource resource = find(name);
        if (resource == null) {
            return;
        }

        taskDAO.deleteAll(resource, TaskType.PROPAGATION);
        taskDAO.deleteAll(resource, TaskType.SYNCHRONIZATION);
        taskDAO.deleteAll(resource, TaskType.PUSH);

        for (AnyObject anyObject : anyObjectDAO.findByResource(resource)) {
            anyObject.remove(resource);
        }
        for (User user : userDAO.findByResource(resource)) {
            user.remove(resource);
        }
        for (Group group : groupDAO.findByResource(resource)) {
            group.remove(resource);
        }
        for (AccountPolicy policy : policyDAO.findByResource(resource)) {
            policy.remove(resource);
        }

        if (resource.getConnector() != null && resource.getConnector().getResources() != null
                && !resource.getConnector().getResources().isEmpty()) {

            resource.getConnector().getResources().remove(resource);
        }
        resource.setConnector(null);

        for (Provision provision : resource.getProvisions()) {
            for (MappingItem item : provision.getMapping().getItems()) {
                item.setMapping(null);
            }
            provision.getMapping().getItems().clear();
            provision.setMapping(null);
            provision.setResource(null);
        }

        entityManager().remove(resource);
    }
}
