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

import cn.hutool.core.util.StrUtil;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Component
public class CasLogoutRequestParser {

    private static final Logger log = LoggerFactory.getLogger(CasLogoutRequestParser.class);

    public String parseSessionIndex(String logoutRequest) {
        if (StrUtil.isBlank(logoutRequest)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing logoutRequest");
        }
        Document document = parseXml(resolveLogoutRequestXml(logoutRequest));
        Element rootElement = document.getDocumentElement();
        if (rootElement == null || !"LogoutRequest".equals(resolveElementName(rootElement))) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "Invalid CAS logoutRequest root element");
        }
        Element sessionIndex = findFirstElement(rootElement, "SessionIndex");
        if (sessionIndex == null || StrUtil.isBlank(sessionIndex.getTextContent())) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Missing CAS SessionIndex");
        }
        return StrUtil.trim(sessionIndex.getTextContent());
    }

    private String resolveLogoutRequestXml(String logoutRequest) {
        String payload = StrUtil.trim(logoutRequest);
        if (StrUtil.startWith(payload, "<")) {
            return payload;
        }
        try {
            return new String(Base64.getDecoder().decode(payload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.error("Failed to decode CAS front-channel logoutRequest", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid CAS logoutRequest");
        }
    }

    private Document parseXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            log.error("Failed to parse CAS logoutRequest", e);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid CAS logoutRequest");
        }
    }

    private Element findFirstElement(Node node, String expectedName) {
        if (node instanceof Element element && expectedName.equals(resolveElementName(element))) {
            return element;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Element found = findFirstElement(children.item(i), expectedName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String resolveElementName(Element element) {
        return StrUtil.blankToDefault(element.getLocalName(), element.getNodeName())
                .replaceFirst("^.*:", "");
    }
}
