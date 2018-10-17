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

package org.eclipse.jetty.websocket.core.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.MultiMap;
import org.eclipse.jetty.util.UrlEncoded;
import org.eclipse.jetty.websocket.core.ExtensionConfig;
import org.eclipse.jetty.websocket.core.WebSocketConstants;

public class Negotiated
{
    private final URI requestURI;
    private final Map<String, List<String>> parameterMap;
    private final String subProtocol;
    private final boolean secure;
    private final ExtensionStack extensions;
    private final String protocolVersion;

    public Negotiated(URI requestURI, String subProtocol, boolean secure,
        ExtensionStack extensions, String protocolVersion)
    {
        this.requestURI = toWebsocket(requestURI);
        this.subProtocol = subProtocol;
        this.secure = secure;
        this.extensions = extensions;
        this.protocolVersion = protocolVersion;

        Map<String,List<String>> map;
        if (requestURI.getQuery()==null)
            map = Collections.emptyMap();
        else
        {
            map = new HashMap<>();
            MultiMap<String> params = new MultiMap<>();
            UrlEncoded.decodeUtf8To(requestURI.getQuery(), params);
            for (String p : params.keySet())
                map.put(p,Collections.unmodifiableList(params.getValues(p)));
        }
        this.parameterMap = Collections.unmodifiableMap(map);
    }

    public URI getRequestURI()
    {
        return requestURI;
    }

    public Map<String, List<String>> getParameterMap()
    {
        return parameterMap;
    }

    public String getSubProtocol()
    {
        return subProtocol;
    }

    public boolean isSecure()
    {
        return secure;
    }

    public ExtensionStack getExtensions()
    {
        return extensions;
    }

    public String getProtocolVersion()
    {
        return protocolVersion;
    }

    @Override
    public String toString()
    {
        return String.format("[%s,%s,%b.%s]",
            requestURI,
            subProtocol,
            secure,
            extensions.getNegotiatedExtensions().stream().map(ExtensionConfig::getName).collect(Collectors.toList()));
    }

    public static Negotiated from(ExtensionStack extensions)
    {
        try
        {
            return new Negotiated(new URI("/"), "", false, extensions, WebSocketConstants.SPEC_VERSION_STRING);
        }
        catch(URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }


    /**
     * Convert to WebSocket {@code ws} or {@code wss} scheme URIs
     *
     * <p>
     * Converting {@code http} and {@code https} URIs to their WebSocket equivalent
     *
     * @param inputUri
     *            the input URI
     * @return the WebSocket scheme URI for the input URI.
     * @throws URISyntaxException
     *             if unable to convert the input URI
     */
    public static URI toWebsocket(final URI inputUri)
    {
        try
        {
            Objects.requireNonNull(inputUri,"Input URI must not be null");
            String httpScheme = inputUri.getScheme();
            if ("ws".equalsIgnoreCase(httpScheme) || "wss".equalsIgnoreCase(httpScheme))
            {
                // keep as-is
                return inputUri;
            }

            if ("http".equalsIgnoreCase(httpScheme))
            {
                // convert to ws
                return new URI("ws" + inputUri.toString().substring(httpScheme.length()));
            }

            if ("https".equalsIgnoreCase(httpScheme))
            {
                // convert to wss
                return new URI("wss" + inputUri.toString().substring(httpScheme.length()));
            }

            throw new URISyntaxException(inputUri.toString(),"Unrecognized HTTP scheme");
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException(e);
        }
    }
}
