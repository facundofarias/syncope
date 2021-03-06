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
package org.apache.syncope.core.persistence.jpa.entity;

import java.util.ArrayList;
import java.util.List;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.Transformer;
import org.apache.syncope.common.lib.report.ReportletConf;
import org.apache.syncope.core.persistence.api.entity.Report;
import org.apache.syncope.core.persistence.api.entity.ReportExec;
import org.apache.syncope.core.persistence.jpa.validation.entity.ReportCheck;

@Entity
@Table(name = JPAReport.TABLE)
@ReportCheck
public class JPAReport extends AbstractEntity<Long> implements Report {

    private static final long serialVersionUID = -587652654964285834L;

    public static final String TABLE = "Report";

    @Id
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
    private List<JPAReportletConfInstance> reportletConfs = new ArrayList<>();

    private String cronExpression;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
    private List<JPAReportExec> executions = new ArrayList<>();

    @Override
    public Long getKey() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    @Override
    public boolean add(final ReportExec exec) {
        checkType(exec, JPAReportExec.class);
        return exec != null && !executions.contains((JPAReportExec) exec) && executions.add((JPAReportExec) exec);
    }

    @Override
    public boolean remove(final ReportExec exec) {
        checkType(exec, JPAReportExec.class);
        return exec != null && executions.remove((JPAReportExec) exec);
    }

    @Override
    public List<? extends ReportExec> getExecs() {
        return executions;
    }

    @Override
    public boolean add(final ReportletConf reportletConf) {
        if (reportletConf == null) {
            return false;
        }

        JPAReportletConfInstance instance = new JPAReportletConfInstance();
        instance.setReport(this);
        instance.setInstance(reportletConf);

        return reportletConfs.add(instance);
    }

    @Override
    public void removeAllReportletConfs() {
        reportletConfs.clear();
    }

    @Override
    public List<ReportletConf> getReportletConfs() {
        return CollectionUtils.collect(reportletConfs, new Transformer<JPAReportletConfInstance, ReportletConf>() {

            @Override
            public ReportletConf transform(final JPAReportletConfInstance input) {
                return input.getInstance();
            }
        }, new ArrayList<ReportletConf>());
    }

    @Override
    public String getCronExpression() {
        return cronExpression;
    }

    @Override
    public void setCronExpression(final String cronExpression) {
        this.cronExpression = cronExpression;
    }
}
