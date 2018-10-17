//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.servlet;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.websocket.core.ExtensionConfig;
import org.eclipse.jetty.websocket.core.server.Negotiation;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servlet Specific UpgradeResponse implementation.
 */
public class ServletUpgradeResponse
{
    private final HttpServletResponse response;
    private final Negotiation negotiation;

    public ServletUpgradeResponse(Negotiation negotiation)
    {
        this.negotiation = negotiation;
        this.response = negotiation.getResponse();
        Objects.requireNonNull(response, "HttpServletResponse must not be null");
    }

    public void addHeader(String name, String value)
    {
        if (HttpHeader.SEC_WEBSOCKET_SUBPROTOCOL.is(name))
        {
            setAcceptedSubProtocol(value); // Can only be one, so set
            return;
        }

        if (HttpHeader.SEC_WEBSOCKET_EXTENSIONS.is(name) && getExtensions()!=null)
        {
            // Move any extensions configs to the headers
            response.addHeader(name,ExtensionConfig.toHeaderValue(getExtensions()));
            setExtensions(null);
        }

        response.addHeader(name, value);
    }

    public void setHeader(String name, String value)
    {

        if (HttpHeader.SEC_WEBSOCKET_SUBPROTOCOL.is(name))
        {
            setAcceptedSubProtocol(value);
            return;
        }

        if (HttpHeader.SEC_WEBSOCKET_EXTENSIONS.is(name))
            setExtensions(null);

        response.setHeader(name, value);
    }

    public void setHeader(String name, List<String> values)
    {
        if (HttpHeader.SEC_WEBSOCKET_EXTENSIONS.is(name))
        {
            if (values==null || values.isEmpty())
                setAcceptedSubProtocol(null);
            else if (values.size()==1)
                setAcceptedSubProtocol(values.get(0));
            else
                throw new IllegalArgumentException();
        }

        if (HttpHeader.SEC_WEBSOCKET_SUBPROTOCOL.is(name))
        {
            setExtensions(null);
            response.setHeader(name, null);
            values.forEach(value->addHeader(name, value));
            return;
        }

        response.setHeader(name, null); // clear it out first
        values.forEach(value->response.addHeader(name, value));
    }

    public String getAcceptedSubProtocol()
    {
        return getHeader(HttpHeader.SEC_WEBSOCKET_SUBPROTOCOL.asString());
    }

    public List<ExtensionConfig> getExtensions()
    {
        return negotiation.getNegotiatedExtensions();
    }

    public String getHeader(String name)
    {
        return response.getHeader(name);
    }

    public Set<String> getHeaderNames()
    {
        return Collections.unmodifiableSet(new HashSet<>(response.getHeaderNames()));
    }

    public Map<String, List<String>> getHeadersMap()
    {
        Map<String, List<String>> headers = response.getHeaderNames().stream()
                .collect(Collectors.toMap((name) -> name,
                        (name) -> new ArrayList<>(response.getHeaders(name))));
        return Collections.unmodifiableMap(headers);
    }

    public List<String> getHeaders(String name)
    {
        return Collections.unmodifiableList(new ArrayList<>(response.getHeaders(name)));
    }

    public int getStatusCode()
    {
        return response.getStatus();
    }

    public boolean isCommitted()
    {
        return response.isCommitted();
    }

    public void sendError(int statusCode, String message) throws IOException
    {
        response.sendError(statusCode, message);
        response.flushBuffer();
    }

    public void sendForbidden(String message) throws IOException
    {
        sendError(HttpServletResponse.SC_FORBIDDEN, message);
    }

    public void setAcceptedSubProtocol(String protocol)
    {
        negotiation.setSubprotocol(protocol);
    }

    public void setExtensions(List<ExtensionConfig> configs)
    {
        negotiation.setNegotiatedExtensions(configs);
    }

    public void setStatusCode(int statusCode)
    {
        response.setStatus(statusCode);
    }

    public String toString()
    {
        return String.format("UpgradeResponse=%s", response);
    }
}
