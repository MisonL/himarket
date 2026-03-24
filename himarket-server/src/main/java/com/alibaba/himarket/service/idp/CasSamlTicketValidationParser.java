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
public class CasSamlTicketValidationParser {

    public Map<String, Object> parse(String responseBody) {
        if (StrUtil.isBlank(responseBody)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS SAML validation returned empty response");
        }
        Document document = parseXml(responseBody);
        assertSuccess(document);
        Element nameIdentifier = findFirstElement(document, "NameIdentifier");
        if (nameIdentifier == null || StrUtil.isBlank(nameIdentifier.getTextContent())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST, "CAS SAML validation missing NameIdentifier");
        }

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("user", StrUtil.trim(nameIdentifier.getTextContent()));
        NodeList attributeNodes = document.getElementsByTagNameNS("*", "Attribute");
        for (int i = 0; i < attributeNodes.getLength(); i++) {
            Node node = attributeNodes.item(i);
            if (node instanceof Element attribute) {
                String name = StrUtil.blankToDefault(attribute.getAttribute("AttributeName"), null);
                String value = extractAttributeValue(attribute);
                if (StrUtil.isNotBlank(name) && StrUtil.isNotBlank(value)) {
                    attributes.put(name, value);
                }
            }
        }
        return attributes;
    }

    private void assertSuccess(Document document) {
        Element statusCode = findFirstElement(document, "StatusCode");
        String statusValue = statusCode == null ? null : statusCode.getAttribute("Value");
        if (StrUtil.endWithIgnoreCase(statusValue, ":Success") || "Success".equals(statusValue)) {
            return;
        }
        Element statusMessage = findFirstElement(document, "StatusMessage");
        throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                StrUtil.blankToDefault(
                        statusMessage == null ? null : StrUtil.trim(statusMessage.getTextContent()),
                        "CAS SAML ticket validation failed"));
    }

    private String extractAttributeValue(Element attribute) {
        Element attributeValue = findFirstElement(attribute, "AttributeValue");
        if (attributeValue == null) {
            return null;
        }
        return StrUtil.trim(attributeValue.getTextContent());
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
        } catch (ParserConfigurationException | SAXException | java.io.IOException e) {
            log.error("Failed to parse CAS SAML validation response", e);
            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR, "Failed to parse CAS SAML validation response");
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
