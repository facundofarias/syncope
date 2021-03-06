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
package org.apache.syncope.core.provisioning.java;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.syncope.common.lib.patch.PasswordPatch;
import org.apache.syncope.common.lib.patch.StatusPatch;
import org.apache.syncope.common.lib.patch.StringPatchItem;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.to.PropagationStatus;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.PatchOperation;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.UserProvisioningManager;
import org.apache.syncope.core.provisioning.api.WorkflowResult;
import org.apache.syncope.common.lib.types.PropagationByResource;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.common.lib.types.StatusPatchType;
import org.apache.syncope.core.provisioning.api.propagation.PropagationException;
import org.apache.syncope.core.provisioning.api.propagation.PropagationManager;
import org.apache.syncope.core.provisioning.api.propagation.PropagationReporter;
import org.apache.syncope.core.provisioning.api.propagation.PropagationTaskExecutor;
import org.apache.syncope.core.provisioning.api.sync.ProvisioningResult;
import org.apache.syncope.core.misc.spring.ApplicationContextProvider;
import org.apache.syncope.core.provisioning.api.VirAttrHandler;
import org.apache.syncope.core.workflow.api.UserWorkflowAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DefaultUserProvisioningManager implements UserProvisioningManager {

    private static final Logger LOG = LoggerFactory.getLogger(UserProvisioningManager.class);

    @Autowired
    protected UserWorkflowAdapter uwfAdapter;

    @Autowired
    protected PropagationManager propagationManager;

    @Autowired
    protected PropagationTaskExecutor taskExecutor;

    @Autowired
    protected VirAttrHandler virtAttrHandler;

    @Autowired
    protected UserDAO userDAO;

    @Override
    public Pair<Long, List<PropagationStatus>> create(final UserTO userTO) {
        return create(userTO, true, false, null, Collections.<String>emptySet());
    }

    @Override
    public Pair<Long, List<PropagationStatus>> create(final UserTO userTO, final boolean storePassword) {
        return create(userTO, storePassword, false, null, Collections.<String>emptySet());
    }

    @Override
    public Pair<Long, List<PropagationStatus>> create(final UserTO userTO, final Set<String> excludedResources) {
        return create(userTO, false, false, null, excludedResources);
    }

    @Override
    public Pair<Long, List<PropagationStatus>> create(final UserTO userTO, final boolean storePassword,
            final boolean disablePwdPolicyCheck, final Boolean enabled, final Set<String> excludedResources) {

        WorkflowResult<Pair<Long, Boolean>> created =
                uwfAdapter.create(userTO, disablePwdPolicyCheck, enabled, storePassword);

        List<PropagationTask> tasks = propagationManager.getUserCreateTasks(
                created.getResult().getKey(),
                userTO.getPassword(),
                created.getResult().getValue(),
                created.getPropByRes(),
                userTO.getVirAttrs(),
                excludedResources);
        PropagationReporter propagationReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propagationReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propagationReporter.onPrimaryResourceFailure(tasks);
        }

        return new ImmutablePair<>(created.getResult().getKey(), propagationReporter.getStatuses());
    }

    @Override
    public Pair<Long, List<PropagationStatus>> update(final UserPatch userPatch) {
        WorkflowResult<Pair<UserPatch, Boolean>> updated = uwfAdapter.update(userPatch);

        List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(updated);
        if (tasks.isEmpty()) {
            // SYNCOPE-459: take care of user virtual attributes ...
            PropagationByResource propByResVirAttr = virtAttrHandler.updateVirtual(
                    updated.getResult().getKey().getKey(),
                    AnyTypeKind.USER,
                    userPatch.getVirAttrs());
            if (!propByResVirAttr.isEmpty()) {
                tasks.addAll(propagationManager.getUserUpdateTasks(updated, false, null));
            }
        }
        PropagationReporter propagationReporter = ApplicationContextProvider.getBeanFactory().
                getBean(PropagationReporter.class);
        if (!tasks.isEmpty()) {
            try {
                taskExecutor.execute(tasks, propagationReporter);
            } catch (PropagationException e) {
                LOG.error("Error propagation primary resource", e);
                propagationReporter.onPrimaryResourceFailure(tasks);
            }
        }

        return new ImmutablePair<>(updated.getResult().getKey().getKey(), propagationReporter.getStatuses());
    }

    @Override
    public Pair<Long, List<PropagationStatus>> update(final UserPatch userPatch, final Set<String> excludedResources) {
        return update(userPatch, userPatch.getKey(), new ProvisioningResult(), null, excludedResources);
    }

    @Override
    public Pair<Long, List<PropagationStatus>> update(final UserPatch userPatch, final Long key,
            final ProvisioningResult result, final Boolean enabled, final Set<String> excludedResources) {

        WorkflowResult<Pair<UserPatch, Boolean>> updated;
        try {
            updated = uwfAdapter.update(userPatch);
        } catch (Exception e) {
            LOG.error("Update of user {} failed, trying to sync its status anyway (if configured)", key, e);

            result.setStatus(ProvisioningResult.Status.FAILURE);
            result.setMessage("Update failed, trying to sync status anyway (if configured)\n" + e.getMessage());

            updated = new WorkflowResult<Pair<UserPatch, Boolean>>(
                    new ImmutablePair<>(userPatch, false), new PropagationByResource(),
                    new HashSet<String>());
        }

        if (enabled != null) {
            User user = userDAO.find(key);

            WorkflowResult<Long> enableUpdate = null;
            if (user.isSuspended() == null) {
                enableUpdate = uwfAdapter.activate(key, null);
            } else if (enabled && user.isSuspended()) {
                enableUpdate = uwfAdapter.reactivate(key);
            } else if (!enabled && !user.isSuspended()) {
                enableUpdate = uwfAdapter.suspend(key);
            }

            if (enableUpdate != null) {
                if (enableUpdate.getPropByRes() != null) {
                    updated.getPropByRes().merge(enableUpdate.getPropByRes());
                    updated.getPropByRes().purge();
                }
                updated.getPerformedTasks().addAll(enableUpdate.getPerformedTasks());
            }
        }

        List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(
                updated, updated.getResult().getKey().getPassword() != null, excludedResources);
        PropagationReporter propagationReporter = ApplicationContextProvider.getBeanFactory().
                getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propagationReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propagationReporter.onPrimaryResourceFailure(tasks);
        }

        return new ImmutablePair<>(updated.getResult().getKey().getKey(), propagationReporter.getStatuses());
    }

    @Override
    public List<PropagationStatus> delete(final Long key) {
        return delete(key, Collections.<String>emptySet());
    }

    @Override
    public List<PropagationStatus> delete(final Long key, final Set<String> excludedResources) {
        PropagationByResource propByRes = new PropagationByResource();
        propByRes.set(ResourceOperation.DELETE, userDAO.findAllResourceNames(userDAO.authFind(key)));

        // Note here that we can only notify about "delete", not any other
        // task defined in workflow process definition: this because this
        // information could only be available after uwfAdapter.delete(), which
        // will also effectively remove user from db, thus making virtually
        // impossible by NotificationManager to fetch required user information
        List<PropagationTask> tasks = propagationManager.getDeleteTasks(
                AnyTypeKind.USER,
                key,
                propByRes,
                excludedResources);
        PropagationReporter propagationReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propagationReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propagationReporter.onPrimaryResourceFailure(tasks);
        }

        try {
            uwfAdapter.delete(key);
        } catch (PropagationException e) {
            throw e;
        }

        return propagationReporter.getStatuses();
    }

    @Override
    public Long unlink(final UserPatch userPatch) {
        WorkflowResult<Pair<UserPatch, Boolean>> updated = uwfAdapter.update(userPatch);
        return updated.getResult().getKey().getKey();
    }

    @Override
    public Long link(final UserPatch userPatch) {
        return uwfAdapter.update(userPatch).getResult().getKey().getKey();
    }

    @Override
    public Pair<Long, List<PropagationStatus>> activate(final StatusPatch statusPatch) {
        WorkflowResult<Long> updated = statusPatch.isOnSyncope()
                ? uwfAdapter.activate(statusPatch.getKey(), statusPatch.getToken())
                : new WorkflowResult<>(statusPatch.getKey(), null, statusPatch.getType().name().toLowerCase());

        return new ImmutablePair<>(updated.getResult(), propagateStatus(statusPatch));
    }

    @Override
    public Pair<Long, List<PropagationStatus>> reactivate(final StatusPatch statusPatch) {
        WorkflowResult<Long> updated = statusPatch.isOnSyncope()
                ? uwfAdapter.reactivate(statusPatch.getKey())
                : new WorkflowResult<>(statusPatch.getKey(), null, statusPatch.getType().name().toLowerCase());

        return new ImmutablePair<>(updated.getResult(), propagateStatus(statusPatch));
    }

    @Override
    public Pair<Long, List<PropagationStatus>> suspend(final StatusPatch statusPatch) {
        WorkflowResult<Long> updated = statusPatch.isOnSyncope()
                ? uwfAdapter.suspend(statusPatch.getKey())
                : new WorkflowResult<>(statusPatch.getKey(), null, statusPatch.getType().name().toLowerCase());

        return new ImmutablePair<>(updated.getResult(), propagateStatus(statusPatch));
    }

    protected List<PropagationStatus> propagateStatus(final StatusPatch statusPatch) {
        Collection<String> noPropResourceNames = CollectionUtils.removeAll(
                userDAO.findAllResourceNames(userDAO.find(statusPatch.getKey())), statusPatch.getResources());

        List<PropagationTask> tasks = propagationManager.getUpdateTasks(
                AnyTypeKind.USER,
                statusPatch.getKey(),
                false,
                statusPatch.getType() != StatusPatchType.SUSPEND,
                null,
                null,
                noPropResourceNames);
        PropagationReporter propReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propReporter.onPrimaryResourceFailure(tasks);
        }

        return propReporter.getStatuses();

    }

    @Override
    public void internalSuspend(final Long key) {
        Pair<WorkflowResult<Long>, Boolean> updated = uwfAdapter.internalSuspend(key);

        // propagate suspension if and only if it is required by policy
        if (updated != null && updated.getValue()) {
            UserPatch userPatch = new UserPatch();
            userPatch.setKey(updated.getKey().getResult());

            List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(
                    new WorkflowResult<Pair<UserPatch, Boolean>>(
                            new ImmutablePair<>(userPatch, Boolean.FALSE),
                            updated.getKey().getPropByRes(), updated.getKey().getPerformedTasks()));
            taskExecutor.execute(tasks);
        }
    }

    @Override
    public List<PropagationStatus> provision(
            final Long key, final boolean changePwd, final String password, final Collection<String> resources) {

        UserPatch userPatch = new UserPatch();
        userPatch.setKey(key);
        userPatch.getResources().addAll(CollectionUtils.collect(resources,
                new Transformer<String, StringPatchItem>() {

                    @Override
                    public StringPatchItem transform(final String input) {
                        return new StringPatchItem.Builder().operation(PatchOperation.ADD_REPLACE).value(input).build();
                    }
                }, new HashSet<StringPatchItem>()));

        if (changePwd) {
            PasswordPatch passwordPatch = new PasswordPatch();
            passwordPatch.setOnSyncope(false);
            passwordPatch.getResources().addAll(resources);
            passwordPatch.setValue(password);
            userPatch.setPassword(passwordPatch);
        }

        PropagationByResource propByRes = new PropagationByResource();
        propByRes.addAll(ResourceOperation.UPDATE, resources);

        WorkflowResult<Pair<UserPatch, Boolean>> wfResult = new WorkflowResult<Pair<UserPatch, Boolean>>(
                ImmutablePair.of(userPatch, (Boolean) null), propByRes, "update");

        List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(wfResult, changePwd, null);
        PropagationReporter propagationReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propagationReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propagationReporter.onPrimaryResourceFailure(tasks);
        }

        return propagationReporter.getStatuses();
    }

    @Override
    public List<PropagationStatus> deprovision(final Long key, final Collection<String> resources) {
        PropagationByResource propByRes = new PropagationByResource();
        propByRes.set(ResourceOperation.DELETE, resources);

        List<PropagationTask> tasks = propagationManager.getDeleteTasks(
                AnyTypeKind.USER,
                key,
                propByRes,
                CollectionUtils.removeAll(userDAO.findAllResourceNames(userDAO.authFind(key)), resources));
        PropagationReporter propagationReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propagationReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propagationReporter.onPrimaryResourceFailure(tasks);
        }

        return propagationReporter.getStatuses();
    }

    @Override
    public void requestPasswordReset(final Long key) {
        uwfAdapter.requestPasswordReset(key);
    }

    @Override
    public void confirmPasswordReset(final Long key, final String token, final String password) {
        uwfAdapter.confirmPasswordReset(key, token, password);

        UserPatch userPatch = new UserPatch();
        userPatch.setKey(key);
        userPatch.setPassword(new PasswordPatch.Builder().value(password).build());

        List<PropagationTask> tasks = propagationManager.getUserUpdateTasks(
                new WorkflowResult<Pair<UserPatch, Boolean>>(
                        new ImmutablePair<UserPatch, Boolean>(userPatch, null), null, "confirmPasswordReset"),
                true, null);
        PropagationReporter propReporter =
                ApplicationContextProvider.getBeanFactory().getBean(PropagationReporter.class);
        try {
            taskExecutor.execute(tasks, propReporter);
        } catch (PropagationException e) {
            LOG.error("Error propagation primary resource", e);
            propReporter.onPrimaryResourceFailure(tasks);
        }
    }
}
