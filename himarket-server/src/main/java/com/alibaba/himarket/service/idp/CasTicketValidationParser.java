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
import com.alibaba.himarket.core.constant.IdpConstants;
import com.alibaba.himarket.core.exception.BusinessException;
import com.alibaba.himarket.core.exception.ErrorCode;
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Component
@Slf4j
public class CasTicketValidationParser {

    public Map<String, Object> parse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS validation returned empty response");
        }
        Document document = parseXml(responseBody);
        Element success = findFirstElement(document, "authenticationSuccess");
        if (success == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    StrUtil.blankToDefault(
                            extractFailureMessage(document), "CAS ticket validation failed"));
        }
        return extractAttributes(success);
    }

    private Document parseXml(String responseBody) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            return factory.newDocumentBuilder()
                    .parse(new InputSource(new StringReader(responseBody)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Failed to parse CAS validation response", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to parse CAS validation response");
        }
    }

    private Map<String, Object> extractAttributes(Element success) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        Element user = findFirstElement(success, "user");
        if (user != null) {
            attributes.put("user", StrUtil.trim(user.getTextContent()));
        }
        Element proxyGrantingTicket = findFirstElement(success, IdpConstants.PROXY_GRANTING_TICKET);
        if (proxyGrantingTicket != null
                && StrUtil.isNotBlank(proxyGrantingTicket.getTextContent())) {
            attributes.put(
                    IdpConstants.PROXY_GRANTING_TICKET,
                    StrUtil.trim(proxyGrantingTicket.getTextContent()));
        }

        Element attributeRoot = findFirstElement(success, "attributes");
        if (attributeRoot == null) {
            return attributes;
        }

        NodeList children = attributeRoot.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element) {
                String value = StrUtil.trim(element.getTextContent());
                if (StrUtil.isNotBlank(value)) {
                    attributes.compute(
                            resolveElementName(element),
                            (k, v) -> {
                                if (v == null) {
                                    return value;
                                }
                                if (v instanceof java.util.List) {
                                    @SuppressWarnings("unchecked")
                                    java.util.List<Object> list = (java.util.List<Object>) v;
                                    list.add(value);
                                    return list;
                                }
                                java.util.ArrayList<Object> list = new java.util.ArrayList<>();
                                list.add(v);
                                list.add(value);
                                return list;
                            });
                }
            }
        }
        return attributes;
    }

    private String extractFailureMessage(Document document) {
        Element failure = findFirstElement(document, "authenticationFailure");
        return failure == null ? null : StrUtil.blankToDefault(failure.getTextContent(), null);
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
