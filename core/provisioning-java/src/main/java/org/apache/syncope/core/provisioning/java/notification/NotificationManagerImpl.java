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
package org.apache.syncope.core.provisioning.java.notification;

import org.apache.syncope.core.provisioning.api.notification.NotificationManager;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.syncope.common.lib.SyncopeConstants;
import org.apache.syncope.common.lib.to.GroupTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AuditElements;
import org.apache.syncope.common.lib.types.AuditElements.Result;
import org.apache.syncope.common.lib.types.AuditLoggerName;
import org.apache.syncope.common.lib.types.IntMappingType;
import org.apache.syncope.common.lib.to.AnyObjectTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.core.persistence.api.dao.ConfDAO;
import org.apache.syncope.core.persistence.api.dao.NotificationDAO;
import org.apache.syncope.core.persistence.api.dao.GroupDAO;
import org.apache.syncope.core.persistence.api.dao.TaskDAO;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.dao.search.OrderByClause;
import org.apache.syncope.core.persistence.api.entity.EntityFactory;
import org.apache.syncope.core.persistence.api.entity.Notification;
import org.apache.syncope.core.persistence.api.entity.PlainAttr;
import org.apache.syncope.core.persistence.api.entity.group.Group;
import org.apache.syncope.core.persistence.api.entity.task.NotificationTask;
import org.apache.syncope.core.persistence.api.entity.task.TaskExec;
import org.apache.syncope.core.persistence.api.entity.user.UDerAttr;
import org.apache.syncope.core.persistence.api.entity.user.UPlainAttr;
import org.apache.syncope.core.persistence.api.entity.user.UVirAttr;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.data.GroupDataBinder;
import org.apache.syncope.core.provisioning.api.data.UserDataBinder;
import org.apache.syncope.core.misc.search.SearchCondConverter;
import org.apache.syncope.core.persistence.api.dao.AnyObjectDAO;
import org.apache.syncope.core.persistence.api.dao.AnySearchDAO;
import org.apache.syncope.core.persistence.api.entity.Any;
import org.apache.syncope.core.persistence.api.entity.AnyAbout;
import org.apache.syncope.core.persistence.api.entity.AnyType;
import org.apache.syncope.core.provisioning.api.VirAttrHandler;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.exception.VelocityException;
import org.apache.velocity.tools.ToolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(rollbackFor = { Throwable.class })
public class NotificationManagerImpl implements NotificationManager {

    /**
     * Logger.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NotificationManager.class);

    public static final String MAIL_TEMPLATES = "mailTemplates/";

    public static final String MAIL_TEMPLATE_HTML_SUFFIX = ".html.vm";

    public static final String MAIL_TEMPLATE_TEXT_SUFFIX = ".txt.vm";

    /**
     * Notification DAO.
     */
    @Autowired
    private NotificationDAO notificationDAO;

    /**
     * Configuration DAO.
     */
    @Autowired
    private ConfDAO confDAO;

    /**
     * AnyObject DAO.
     */
    @Autowired
    private AnyObjectDAO anyObjectDAO;

    /**
     * User DAO.
     */
    @Autowired
    private UserDAO userDAO;

    /**
     * Group DAO.
     */
    @Autowired
    private GroupDAO groupDAO;

    /**
     * Search DAO.
     */
    @Autowired
    private AnySearchDAO searchDAO;

    /**
     * Task DAO.
     */
    @Autowired
    private TaskDAO taskDAO;

    /**
     * Velocity template engine.
     */
    @Autowired
    private VelocityEngine velocityEngine;

    /**
     * Velocity tool manager.
     */
    @Autowired
    private ToolManager velocityToolManager;

    @Autowired
    private VirAttrHandler virAttrHander;

    @Autowired
    private UserDataBinder userDataBinder;

    @Autowired
    private GroupDataBinder groupDataBinder;

    @Autowired
    private EntityFactory entityFactory;

    @Transactional(readOnly = true)
    @Override
    public long getMaxRetries() {
        return confDAO.find("notification.maxRetries", "0").getValues().get(0).getLongValue();
    }

    /**
     * Create a notification task.
     *
     * @param notification notification to take as model
     * @param any the any object this task is about
     * @param model Velocity model
     * @return notification task, fully populated
     */
    private NotificationTask getNotificationTask(
            final Notification notification,
            final Any<?, ?, ?> any,
            final Map<String, Object> model) {

        if (any != null) {
            virAttrHander.retrieveVirAttrValues(any);
        }

        List<User> recipients = new ArrayList<>();

        if (notification.getRecipients() != null) {
            recipients.addAll(searchDAO.<User>search(SyncopeConstants.FULL_ADMIN_REALMS,
                    SearchCondConverter.convert(notification.getRecipients()),
                    Collections.<OrderByClause>emptyList(), AnyTypeKind.USER));
        }

        if (notification.isSelfAsRecipient() && any instanceof User) {
            recipients.add((User) any);
        }

        Set<String> recipientEmails = new HashSet<>();
        List<UserTO> recipientTOs = new ArrayList<>(recipients.size());
        for (User recipient : recipients) {
            virAttrHander.retrieveVirAttrValues(recipient);

            String email = getRecipientEmail(notification.getRecipientAttrType(),
                    notification.getRecipientAttrName(), recipient);
            if (email == null) {
                LOG.warn("{} cannot be notified: {} not found", recipient, notification.getRecipientAttrName());
            } else {
                recipientEmails.add(email);
                recipientTOs.add(userDataBinder.getUserTO(recipient, true));
            }
        }

        if (notification.getStaticRecipients() != null) {
            recipientEmails.addAll(notification.getStaticRecipients());
        }

        model.put("recipients", recipientTOs);
        model.put("syncopeConf", this.findAllSyncopeConfs());
        model.put("events", notification.getEvents());

        NotificationTask task = entityFactory.newEntity(NotificationTask.class);
        task.setTraceLevel(notification.getTraceLevel());
        task.getRecipients().addAll(recipientEmails);
        task.setSender(notification.getSender());
        task.setSubject(notification.getSubject());

        String htmlBody = mergeTemplateIntoString(
                MAIL_TEMPLATES + notification.getTemplate() + MAIL_TEMPLATE_HTML_SUFFIX, model);
        String textBody = mergeTemplateIntoString(
                MAIL_TEMPLATES + notification.getTemplate() + MAIL_TEMPLATE_TEXT_SUFFIX, model);

        task.setHtmlBody(htmlBody);
        task.setTextBody(textBody);

        return task;
    }

    private String mergeTemplateIntoString(final String templateLocation, final Map<String, Object> model) {
        StringWriter result = new StringWriter();
        try {
            Context velocityContext = createVelocityContext(model);
            velocityEngine.mergeTemplate(templateLocation, SyncopeConstants.DEFAULT_ENCODING, velocityContext, result);
        } catch (VelocityException e) {
            LOG.error("Could not get mail body", e);
        } catch (RuntimeException e) {
            // ensure same behaviour as by using Spring VelocityEngineUtils.mergeTemplateIntoString()
            throw e;
        } catch (Exception e) {
            LOG.error("Could not get mail body", e);
        }

        return result.toString();
    }

    /**
     * Create a Velocity Context for the given model, to be passed to the template for merging.
     *
     * @param model Velocity model
     * @return Velocity context
     */
    protected Context createVelocityContext(final Map<String, Object> model) {
        Context toolContext = velocityToolManager.createContext();
        return new VelocityContext(model, toolContext);
    }

    @Override
    public List<NotificationTask> createTasks(
            final AuditElements.EventCategoryType type,
            final String category,
            final String subcategory,
            final String event,
            final Result condition,
            final Object before,
            final Object output,
            final Object... input) {

        Any<?, ?, ?> any = null;

        if (before instanceof UserTO) {
            any = userDAO.find(((UserTO) before).getKey());
        } else if (output instanceof UserTO) {
            any = userDAO.find(((UserTO) output).getKey());
        } else if (before instanceof AnyObjectTO) {
            any = anyObjectDAO.find(((AnyObjectTO) before).getKey());
        } else if (output instanceof AnyObjectTO) {
            any = anyObjectDAO.find(((AnyObjectTO) output).getKey());
        } else if (before instanceof GroupTO) {
            any = groupDAO.find(((GroupTO) before).getKey());
        } else if (output instanceof GroupTO) {
            any = groupDAO.find(((GroupTO) output).getKey());
        }

        AnyType anyType = any == null ? null : any.getType();
        LOG.debug("Search notification for [{}]{}", anyType, any);

        List<NotificationTask> notifications = new ArrayList<>();
        for (Notification notification : notificationDAO.findAll()) {
            if (LOG.isDebugEnabled()) {
                for (AnyAbout about : notification.getAbouts()) {
                    LOG.debug("Notification about {} defined: {}", about.getAnyType(), about.get());
                }
            }

            if (notification.isActive()) {
                String currentEvent = AuditLoggerName.buildEvent(type, category, subcategory, event, condition);
                if (!notification.getEvents().contains(currentEvent)) {
                    LOG.debug("No events found about {}", any);
                } else if (anyType == null || any == null
                        || notification.getAbout(anyType) == null
                        || searchDAO.matches(any,
                                SearchCondConverter.convert(notification.getAbout(anyType).get()), anyType.getKind())) {

                    LOG.debug("Creating notification task for event {} about {}", currentEvent, any);

                    final Map<String, Object> model = new HashMap<>();
                    model.put("type", type);
                    model.put("category", category);
                    model.put("subcategory", subcategory);
                    model.put("event", event);
                    model.put("condition", condition);
                    model.put("before", before);
                    model.put("output", output);
                    model.put("input", input);

                    if (any instanceof User) {
                        model.put("user", userDataBinder.getUserTO((User) any, true));
                    } else if (any instanceof Group) {
                        model.put("group", groupDataBinder.getGroupTO((Group) any, true));
                    }

                    NotificationTask notificationTask = getNotificationTask(notification, any, model);
                    notificationTask = taskDAO.save(notificationTask);
                    notifications.add(notificationTask);
                }
            } else {
                LOG.debug("Notification {} is not active, task will not be created", notification.getKey());
            }
        }
        return notifications;
    }

    private String getRecipientEmail(
            final IntMappingType recipientAttrType, final String recipientAttrName, final User user) {

        String email = null;

        switch (recipientAttrType) {
            case Username:
                email = user.getUsername();
                break;

            case UserPlainSchema:
                UPlainAttr attr = user.getPlainAttr(recipientAttrName);
                if (attr != null) {
                    email = attr.getValuesAsStrings().isEmpty() ? null : attr.getValuesAsStrings().get(0);
                }
                break;

            case UserDerivedSchema:
                UDerAttr derAttr = user.getDerAttr(recipientAttrName);
                if (derAttr != null) {
                    email = derAttr.getValue(user.getPlainAttrs());
                }
                break;

            case UserVirtualSchema:
                UVirAttr virAttr = user.getVirAttr(recipientAttrName);
                if (virAttr != null) {
                    email = virAttr.getValues().isEmpty() ? null : virAttr.getValues().get(0);
                }
                break;

            default:
        }

        return email;
    }

    @Override
    public TaskExec storeExec(final TaskExec execution) {
        NotificationTask task = taskDAO.find(execution.getTask().getKey());
        task.addExec(execution);
        task.setExecuted(true);
        taskDAO.save(task);
        // this flush call is needed to generate a value for the execution key
        taskDAO.flush();
        return execution;
    }

    @Override
    public void setTaskExecuted(final Long taskKey, final boolean executed) {
        NotificationTask task = taskDAO.find(taskKey);
        task.setExecuted(executed);
        taskDAO.save(task);
    }

    @Override
    public long countExecutionsWithStatus(final Long taskKey, final String status) {
        NotificationTask task = taskDAO.find(taskKey);
        long count = 0;
        for (TaskExec taskExec : task.getExecs()) {
            if (status == null) {
                if (taskExec.getStatus() == null) {
                    count++;
                }
            } else if (status.equals(taskExec.getStatus())) {
                count++;
            }
        }
        return count;
    }

    protected Map<String, String> findAllSyncopeConfs() {
        Map<String, String> syncopeConfMap = new HashMap<>();
        for (PlainAttr<?> attr : confDAO.get().getPlainAttrs()) {
            syncopeConfMap.put(attr.getSchema().getKey(), attr.getValuesAsStrings().get(0));
        }
        return syncopeConfMap;
    }
}
