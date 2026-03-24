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

package com.alibaba.himarket.core.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.alibaba.himarket.core.constant.CommonConstants;
import com.alibaba.himarket.core.constant.JwtConstants;
import com.alibaba.himarket.support.common.User;
import com.alibaba.himarket.support.enums.UserType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenUtilTest {

    @BeforeEach
    void setUp() {
        System.setProperty("jwt.secret", "unit-test-secret");
        System.setProperty("jwt.expiration", "60000");
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("jwt.secret");
        System.clearProperty("jwt.expiration");
    }

    @Test
    void shouldGenerateUniqueTokensForSameAdmin() {
        String firstToken = TokenUtil.generateAdminToken("admin-1");
        String secondToken = TokenUtil.generateAdminToken("admin-1");

        assertNotEquals(firstToken, secondToken);

        JWT firstJwt = JWTUtil.parseToken(firstToken);
        JWT secondJwt = JWTUtil.parseToken(secondToken);
        assertNotEquals(
                firstJwt.getPayload(JwtConstants.PAYLOAD_JTI),
                secondJwt.getPayload(JwtConstants.PAYLOAD_JTI));

        User firstUser = TokenUtil.parseUser(firstToken);
        assertEquals(UserType.ADMIN, firstUser.getUserType());
        assertEquals("admin-1", firstUser.getUserId());

        User secondUser = TokenUtil.parseUser(secondToken);
        assertEquals(UserType.ADMIN, secondUser.getUserType());
        assertEquals("admin-1", secondUser.getUserId());
        assertEquals("admin-1", secondJwt.getPayload(CommonConstants.USER_ID).toString());
    }
}
