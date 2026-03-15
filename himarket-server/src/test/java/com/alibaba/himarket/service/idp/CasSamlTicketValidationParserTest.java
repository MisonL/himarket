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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.alibaba.himarket.core.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CasSamlTicketValidationParserTest {

    private final CasSamlTicketValidationParser parser = new CasSamlTicketValidationParser();

    @Test
    void parseShouldExtractSamlUserAndAttributes() {
        Map<String, Object> attributes = parser.parse(successResponse());

        assertEquals("alice", attributes.get("user"));
        assertEquals("alice@example.com", attributes.get("mail"));
        assertEquals("true", attributes.get("longTermAuthenticationRequestTokenUsed"));
    }

    @Test
    void parseShouldRejectFailureResponse() {
        String xml =
                """
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                  <SOAP-ENV:Body>
                    <saml1p:Response xmlns:saml1p="urn:oasis:names:tc:SAML:1.0:protocol">
                      <saml1p:Status>
                        <saml1p:StatusCode Value="saml1p:Responder"/>
                        <saml1p:StatusMessage>ticket invalid</saml1p:StatusMessage>
                      </saml1p:Status>
                    </saml1p:Response>
                  </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """;

        assertThrows(BusinessException.class, () -> parser.parse(xml));
    }

    static String successResponse() {
        return """
        <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
          <SOAP-ENV:Body>
            <saml1p:Response xmlns:saml1p="urn:oasis:names:tc:SAML:1.0:protocol"
                             xmlns:saml1="urn:oasis:names:tc:SAML:1.0:assertion">
              <saml1p:Status>
                <saml1p:StatusCode Value="saml1p:Success"/>
              </saml1p:Status>
              <saml1:Assertion>
                <saml1:AuthenticationStatement>
                  <saml1:Subject>
                    <saml1:NameIdentifier>alice</saml1:NameIdentifier>
                  </saml1:Subject>
                </saml1:AuthenticationStatement>
                <saml1:AttributeStatement>
                  <saml1:Attribute AttributeName="mail">
                    <saml1:AttributeValue>alice@example.com</saml1:AttributeValue>
                  </saml1:Attribute>
                  <saml1:Attribute AttributeName="longTermAuthenticationRequestTokenUsed">
                    <saml1:AttributeValue>true</saml1:AttributeValue>
                  </saml1:Attribute>
                </saml1:AttributeStatement>
              </saml1:Assertion>
            </saml1p:Response>
          </SOAP-ENV:Body>
        </SOAP-ENV:Envelope>
        """;
    }
}
