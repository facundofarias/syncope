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
package org.apache.syncope.client.console.wicket.markup.html.form;

import java.io.Serializable;
import org.apache.wicket.ajax.AjaxRequestTarget;

public abstract class ActionLink<T> implements Serializable {

    private static final long serialVersionUID = 7031329706998320639L;

    private boolean reloadFeedbackPanel = true;

    private T modelObject;

    public ActionLink() {
    }

    public ActionLink(final T modelObject) {
        this.modelObject = modelObject;
    }

    public enum ActionType {

        MAPPING("update"),
        ACCOUNT_LINK("update"),
        RESET_TIME("update"),
        CLONE("create"),
        CREATE("create"),
        EDIT("read"),
        USER_TEMPLATE("read"),
        GROUP_TEMPLATE("read"),
        RESET("update"),
        ENABLE("update"),
        SEARCH("read"),
        DELETE("delete"),
        EXECUTE("execute"),
        DRYRUN("execute"),
        CLAIM("claim"),
        SELECT("read"),
        EXPORT("read"),
        SUSPEND("update"),
        REACTIVATE("update"),
        RELOAD("reload"),
        CHANGE_VIEW("changeView"),
        UNLINK("update"),
        LINK("update"),
        UNASSIGN("update"),
        ASSIGN("update"),
        DEPROVISION("update"),
        PROVISION("update"),
        MANAGE_RESOURCES("update"),
        MANAGE_USERS("update"),
        MANAGE_GROUPS("update"),
        ZOOM_IN("zoomin"),
        ZOOM_OUT("zoomout");

        private final String actionId;

        private ActionType(final String actionId) {
            this.actionId = actionId;
        }

        public String getActionId() {
            return actionId;
        }
    }

    public T getModelObject() {
        return modelObject;
    }

    public abstract void onClick(final AjaxRequestTarget target, final T modelObject);

    public void postClick() {
    }

    public boolean feedbackPanelAutomaticReload() {
        return reloadFeedbackPanel;
    }

    public ActionLink<T> feedbackPanelAutomaticReload(final boolean reloadFeedbackPanel) {
        this.reloadFeedbackPanel = reloadFeedbackPanel;
        return this;
    }
}
