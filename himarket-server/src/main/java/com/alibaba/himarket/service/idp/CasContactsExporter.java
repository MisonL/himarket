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

package com.alibaba.himarket.service.idp;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.support.portal.cas.CasServiceContactConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CasContactsExporter {

    List<Object> export(List<CasServiceContactConfig> contacts) {
        if (CollUtil.isEmpty(contacts)) {
            return List.of();
        }
        List<Object> exportedContacts = new ArrayList<>();
        contacts.forEach(
                contact -> {
                    if (contact == null
                            || StrUtil.isAllBlank(
                                    contact.getName(),
                                    contact.getEmail(),
                                    contact.getPhone(),
                                    contact.getDepartment(),
                                    contact.getType())) {
                        return;
                    }
                    Map<String, Object> exportedContact = new LinkedHashMap<>();
                    exportedContact.put(
                            "@class", "org.apereo.cas.services.DefaultRegisteredServiceContact");
                    if (StrUtil.isNotBlank(contact.getName())) {
                        exportedContact.put("name", contact.getName());
                    }
                    if (StrUtil.isNotBlank(contact.getEmail())) {
                        exportedContact.put("email", contact.getEmail());
                    }
                    if (StrUtil.isNotBlank(contact.getPhone())) {
                        exportedContact.put("phone", contact.getPhone());
                    }
                    if (StrUtil.isNotBlank(contact.getDepartment())) {
                        exportedContact.put("department", contact.getDepartment());
                    }
                    if (StrUtil.isNotBlank(contact.getType())) {
                        exportedContact.put("type", contact.getType());
                    }
                    exportedContacts.add(exportedContact);
                });
        if (exportedContacts.isEmpty()) {
            return List.of();
        }
        return exportedContacts;
    }
}
