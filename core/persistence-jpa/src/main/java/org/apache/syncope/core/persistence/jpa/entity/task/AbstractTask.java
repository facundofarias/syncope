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
package org.apache.syncope.core.persistence.jpa.entity.task;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.core.persistence.api.entity.task.Task;
import org.apache.syncope.core.persistence.api.entity.task.TaskExec;
import org.apache.syncope.core.persistence.jpa.entity.AbstractEntity;

@Entity
@Table(name = AbstractTask.TABLE)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "DTYPE")
public abstract class AbstractTask extends AbstractEntity<Long> implements Task {

    private static final long serialVersionUID = 5837401178128177511L;

    public static final String TABLE = "Task";

    @Id
    private Long id;

    @NotNull
    @Enumerated(EnumType.STRING)
    protected TaskType type;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "task")
    private List<JPATaskExec> executions = new ArrayList<>();

    @Override
    public Long getKey() {
        return id;
    }

    @Override
    public TaskType getType() {
        return type;
    }

    @Override
    public boolean addExec(final TaskExec exec) {
        checkType(exec, JPATaskExec.class);
        return exec != null && !executions.contains((JPATaskExec) exec) && executions.add((JPATaskExec) exec);
    }

    @Override
    public boolean removeExec(final TaskExec exec) {
        checkType(exec, JPATaskExec.class);
        return exec != null && executions.remove((JPATaskExec) exec);
    }

    @Override
    public List<? extends TaskExec> getExecs() {
        return executions;
    }

}
