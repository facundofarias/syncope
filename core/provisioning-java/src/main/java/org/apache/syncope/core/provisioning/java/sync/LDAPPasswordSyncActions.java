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
package org.apache.syncope.core.provisioning.java.sync;

import org.apache.syncope.common.lib.patch.AnyPatch;
import org.apache.syncope.common.lib.patch.PasswordPatch;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.to.AnyTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.core.persistence.api.dao.UserDAO;
import org.apache.syncope.core.persistence.api.entity.user.User;
import org.apache.syncope.core.provisioning.api.sync.ProvisioningProfile;
import org.apache.syncope.core.provisioning.api.sync.ProvisioningResult;
import org.identityconnectors.framework.common.objects.SyncDelta;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.transaction.annotation.Transactional;

/**
 * A SyncActions implementation which allows the ability to import passwords from an LDAP backend
 * that are hashed.
 */
public class LDAPPasswordSyncActions extends DefaultSyncActions {

    protected static final Logger LOG = LoggerFactory.getLogger(LDAPPasswordSyncActions.class);

    @Autowired
    private UserDAO userDAO;

    private String encodedPassword;

    private CipherAlgorithm cipher;

    @Transactional(readOnly = true)
    @Override
    public <A extends AnyTO> SyncDelta beforeProvision(
            final ProvisioningProfile<?, ?> profile,
            final SyncDelta delta,
            final A any) throws JobExecutionException {

        if (any instanceof UserTO) {
            String password = ((UserTO) any).getPassword();
            parseEncodedPassword(password);
        }

        return delta;
    }

    @Transactional(readOnly = true)
    @Override
    public <A extends AnyTO, M extends AnyPatch> SyncDelta beforeUpdate(
            final ProvisioningProfile<?, ?> profile,
            final SyncDelta delta,
            final A any,
            final M anyPatch) throws JobExecutionException {

        if (anyPatch instanceof UserPatch) {
            PasswordPatch modPassword = ((UserPatch) anyPatch).getPassword();
            parseEncodedPassword(modPassword == null ? null : modPassword.getValue());
        }

        return delta;
    }

    private void parseEncodedPassword(final String password) {
        if (password != null && password.startsWith("{")) {
            int closingBracketIndex = password.indexOf('}');
            String digest = password.substring(1, password.indexOf('}'));
            if (digest != null) {
                digest = digest.toUpperCase();
            }
            try {
                encodedPassword = password.substring(closingBracketIndex + 1);
                cipher = CipherAlgorithm.valueOf(digest);
            } catch (IllegalArgumentException e) {
                LOG.error("Cipher algorithm not allowed: {}", digest, e);
                encodedPassword = null;
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public <A extends AnyTO> void after(
            final ProvisioningProfile<?, ?> profile,
            final SyncDelta delta,
            final A any,
            final ProvisioningResult result) throws JobExecutionException {

        if (any instanceof UserTO && encodedPassword != null && cipher != null) {
            User syncopeUser = userDAO.find(any.getKey());
            if (syncopeUser != null) {
                byte[] encodedPasswordBytes = Base64.decode(encodedPassword.getBytes());
                char[] encodedHex = Hex.encode(encodedPasswordBytes);
                String encodedHexStr = new String(encodedHex).toUpperCase();

                syncopeUser.setEncodedPassword(encodedHexStr, cipher);
            }
            encodedPassword = null;
            cipher = null;
        }
    }
}
