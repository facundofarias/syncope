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
package org.apache.syncope.core.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.patch.AnyPatch;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.core.misc.RealmUtils;
import org.apache.syncope.core.misc.TemplateUtils;
import org.apache.syncope.core.misc.security.DelegatedAdministrationException;
import org.apache.syncope.core.misc.spring.ApplicationContextProvider;
import org.apache.syncope.core.persistence.api.dao.AnyTypeDAO;
import org.apache.syncope.core.persistence.api.dao.RealmDAO;
import org.apache.syncope.core.persistence.api.dao.search.OrderByClause;
import org.apache.syncope.core.persistence.api.dao.search.SearchCond;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.persistence.api.entity.Realm;
import org.apache.syncope.core.provisioning.api.LogicActions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.AbstractBeanDefinition;

public abstract class AbstractAnyLogic<TO extends AnyTO, P extends AnyPatch>
        extends AbstractResourceAssociator<TO> {

    @Autowired
    private RealmDAO realmDAO;

    @Autowired
    private AnyTypeDAO anyTypeDAO;

    @Autowired
    private TemplateUtils templateUtils;

    private List<LogicActions> getActions(final Realm realm) {
        List<LogicActions> actions = new ArrayList<>();

        for (String className : realm.getActionsClassNames()) {
            try {
                Class<?> actionsClass = Class.forName(className);
                LogicActions logicActions = (LogicActions) ApplicationContextProvider.getBeanFactory().
                        createBean(actionsClass, AbstractBeanDefinition.AUTOWIRE_BY_TYPE, true);

                actions.add(logicActions);
            } catch (Exception e) {
                LOG.warn("Class '{}' not found", className, e);
            }
        }

        return actions;
    }

    protected Pair<TO, List<LogicActions>> beforeCreate(final TO input) {
        Realm realm = realmDAO.find(input.getRealm());
        if (realm == null) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRealm);
            sce.getElements().add(input.getRealm());
            throw sce;
        }

        AnyType anyType = input instanceof UserTO
                ? anyTypeDAO.findUser()
                : input instanceof GroupTO
                        ? anyTypeDAO.findGroup()
                        : anyTypeDAO.find(input.getType());
        if (anyType == null) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidAnyType);
            sce.getElements().add(input.getType());
            throw sce;
        }

        TO any = input;

        templateUtils.apply(any, realm.getTemplate(anyType));

        List<LogicActions> actions = getActions(realm);
        for (LogicActions action : actions) {
            any = action.beforeCreate(any);
        }

        LOG.debug("Input: {}\nOutput: {}\n", input, any);

        return ImmutablePair.of(any, actions);
    }

    protected TO afterCreate(final TO input, final List<LogicActions> actions) {
        TO any = input;

        for (LogicActions action : actions) {
            any = action.afterCreate(any);
        }

        return any;
    }

    protected Pair<P, List<LogicActions>> beforeUpdate(final P input, final String realmPath) {
        Realm realm = realmDAO.find(realmPath);
        if (realm == null) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRealm);
            sce.getElements().add(realmPath);
            throw sce;
        }

        P mod = input;

        List<LogicActions> actions = getActions(realm);
        for (LogicActions action : actions) {
            mod = action.beforeUpdate(mod);
        }

        LOG.debug("Input: {}\nOutput: {}\n", input, mod);

        return ImmutablePair.of(mod, actions);
    }

    protected TO afterUpdate(final TO input, final List<LogicActions> actions) {
        TO any = input;

        for (LogicActions action : actions) {
            any = action.afterUpdate(any);
        }

        return any;
    }

    protected Pair<TO, List<LogicActions>> beforeDelete(final TO input) {
        Realm realm = realmDAO.find(input.getRealm());
        if (realm == null) {
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.InvalidRealm);
            sce.getElements().add(input.getRealm());
            throw sce;
        }

        TO any = input;

        List<LogicActions> actions = getActions(realm);
        for (LogicActions action : actions) {
            any = action.beforeDelete(any);
        }

        LOG.debug("Input: {}\nOutput: {}\n", input, any);

        return ImmutablePair.of(any, actions);
    }

    protected TO afterDelete(final TO input, final List<LogicActions> actions) {
        TO any = input;

        for (LogicActions action : actions) {
            any = action.afterDelete(any);
        }

        return any;
    }

    private static class StartsWithPredicate implements Predicate<String> {

        private final Collection<String> targets;

        public StartsWithPredicate(final Collection<String> targets) {
            this.targets = targets;
        }

        @Override
        public boolean evaluate(final String realm) {
            return CollectionUtils.exists(targets, new Predicate<String>() {

                @Override
                public boolean evaluate(final String target) {
                    return realm.startsWith(target);
                }
            });
        }

    }

    protected Set<String> getEffectiveRealms(
            final Set<String> allowedRealms, final Collection<String> requestedRealms) {

        Set<String> allowed = RealmUtils.normalize(allowedRealms);
        Set<String> requested = RealmUtils.normalize(requestedRealms);

        Set<String> effective = new HashSet<>();
        CollectionUtils.select(requested, new StartsWithPredicate(allowed), effective);
        CollectionUtils.select(allowed, new StartsWithPredicate(requested), effective);

        return effective;
    }

    protected void securityChecks(final Set<String> effectiveRealms, final String realm, final Long key) {
        if (!CollectionUtils.exists(effectiveRealms, new Predicate<String>() {

            @Override
            public boolean evaluate(final String ownedRealm) {
                return realm.startsWith(ownedRealm);
            }
        })) {

            throw new DelegatedAdministrationException(
                    this instanceof UserLogic
                            ? AnyTypeKind.USER
                            : this instanceof GroupLogic
                                    ? AnyTypeKind.GROUP
                                    : AnyTypeKind.ANY_OBJECT,
                    key);
        }
    }

    public abstract TO read(Long key);

    public abstract int count(List<String> realms);

    public abstract TO create(TO anyTO);

    public abstract TO update(P anyPatch);

    public abstract TO delete(Long key);

    public abstract List<TO> list(
            int page, int size, List<OrderByClause> orderBy,
            List<String> realms,
            boolean details);

    public abstract List<TO> search(
            SearchCond searchCondition,
            int page, int size, List<OrderByClause> orderBy,
            List<String> realms,
            boolean details);

    public abstract int searchCount(SearchCond searchCondition, List<String> realms);
}
