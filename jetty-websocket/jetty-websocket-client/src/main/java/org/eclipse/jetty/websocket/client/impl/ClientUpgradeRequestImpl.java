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

package org.eclipse.jetty.websocket.client.impl;

import org.eclipse.jetty.client.HttpResponse;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeResponse;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.JettyWebSocketFrameHandler;
import org.eclipse.jetty.websocket.core.FrameHandler;
import org.eclipse.jetty.websocket.core.client.UpgradeRequest;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;

import java.net.HttpCookie;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ClientUpgradeRequestImpl extends UpgradeRequest
{
    private final WebSocketClient containerContext;
    private final Object websocketPojo;
    private final CompletableFuture<Session> onOpenFuture;
    private final CompletableFuture<Session> futureSession;
    private final DelegatedClientUpgradeRequest handshakeRequest;

    public ClientUpgradeRequestImpl(WebSocketClient clientContainer, WebSocketCoreClient coreClient, org.eclipse.jetty.websocket.api.UpgradeRequest request, URI requestURI, Object websocketPojo)
    {
        super(coreClient, requestURI);
        this.containerContext = clientContainer;
        this.websocketPojo = websocketPojo;

        this.onOpenFuture = new CompletableFuture<>();
        this.futureSession = super.futureCoreSession.thenCombine(onOpenFuture, (channel, session) -> session);

        if (request != null)
        {
            // Copy request details into actual request
            HttpFields fields = getHeaders();
            request.getHeaders().forEach((name, values) -> fields.put(name, values));

            // Copy manually created Cookies into place
            List<HttpCookie> cookies = request.getCookies();
            if(cookies != null)
            {
                HttpFields headers = getHeaders();
                // TODO: remove existing Cookie header (if set)?
                for (HttpCookie cookie: cookies)
                {
                    headers.add(HttpHeader.COOKIE, cookie.toString());
                }
            }

            // Copy sub-protocols
            setSubProtocols(request.getSubProtocols());

            // Copy extensions
            /* TODO or not?
            setExtensions(request.getExtensions());
            */

            // Copy method from upgradeRequest object
            if (request.getMethod() != null)
                method(request.getMethod());

            // Copy version from upgradeRequest object
            if (request.getHttpVersion() != null)
                version(HttpVersion.fromString(request.getHttpVersion()));
        }

        handshakeRequest = new DelegatedClientUpgradeRequest(this);
    }

    @Override
    protected void customize(EndPoint endp)
    {
        super.customize(endp);
        handshakeRequest.configure(endp);
    }

    protected void handleException(Throwable failure)
    {
        super.handleException(failure);
        onOpenFuture.completeExceptionally(failure);
    }

    @Override
    public FrameHandler getFrameHandler(WebSocketCoreClient coreClient, HttpResponse response)
    {
        UpgradeResponse upgradeResponse = new DelegatedClientUpgradeResponse(response);

        JettyWebSocketFrameHandler frameHandler = containerContext.newFrameHandler(websocketPojo,
                handshakeRequest, upgradeResponse, onOpenFuture);

        return frameHandler;
    }

    public CompletableFuture<Session> getFutureSession()
    {
        return futureSession;
    }
}
