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

import java.net.HttpCookie;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.websocket.common.HandshakeRequest;
import org.eclipse.jetty.websocket.core.WebSocketCore;
import org.eclipse.jetty.websocket.core.extensions.ExtensionConfig;

/**
 * Servlet specific Upgrade Request implementation.
 */
public class ServletUpgradeRequest implements HandshakeRequest
{
    private final URI requestURI;
    private final String queryString;
    private final UpgradeHttpServletRequest request;
    private final boolean secure;
    private List<HttpCookie> cookies;
    private Map<String, List<String>> parameterMap;
    private List<String> subprotocols;

    public ServletUpgradeRequest(HttpServletRequest httpRequest) throws URISyntaxException
    {
        this.queryString = httpRequest.getQueryString();
        this.secure = httpRequest.isSecure();

        StringBuffer uri = httpRequest.getRequestURL();
        if (this.queryString != null)
            uri.append("?").append(this.queryString);
        uri.replace(0, uri.indexOf(":"), secure ? "wss" : "ws");
        this.requestURI = new URI(uri.toString());
        this.request = new UpgradeHttpServletRequest(httpRequest);
    }

    public X509Certificate[] getCertificates()
    {
        return (X509Certificate[]) request.getAttribute("javax.servlet.request.X509Certificate");
    }

    public List<HttpCookie> getCookies()
    {
        if (cookies == null)
        {
            Cookie[] requestCookies = request.getCookies();
            if (requestCookies != null)
            {
                cookies = new ArrayList<>();
                for (Cookie requestCookie : requestCookies)
                {
                    HttpCookie cookie = new HttpCookie(requestCookie.getName(), requestCookie.getValue());
                    // No point handling domain/path/expires/secure/httponly on client request cookies
                    cookies.add(cookie);
                }
            }
        }

        return cookies;
    }

    @Override
    public List<ExtensionConfig> getExtensions()
    {
        Enumeration<String> e = request.getHeaders("Sec-WebSocket-Extensions");
        return ExtensionConfig.parseEnum(e);
    }

    @Override
    public String getHeader(String name)
    {
        return request.getHeader(name);
    }

    @Override
    public int getHeaderInt(String name)
    {
        String val = request.getHeader(name);
        if (val == null)
        {
            return -1;
        }
        return Integer.parseInt(val);
    }

    @Override
    public Map<String, List<String>> getHeadersMap()
    {
        return request.getHeaders();
    }

    @Override
    public List<String> getHeaders(String name)
    {
        return request.getHeaders().get(name);
    }

    @Override
    public String getHost()
    {
        return requestURI.getHost();
    }

    /**
     * Return the underlying HttpServletRequest that existed at Upgrade time.
     * <p>
     * Note: many features of the HttpServletRequest are invalid when upgraded,
     * especially ones that deal with body content, streams, readers, and responses.
     *
     * @return a limited version of the underlying HttpServletRequest
     */
    public HttpServletRequest getHttpServletRequest()
    {
        return request;
    }

    @Override
    public String getHttpVersion()
    {
        return request.getProtocol();
    }

    /**
     * Equivalent to {@link HttpServletRequest#getLocale()}
     *
     * @return the preferred <code>Locale</code> for the client
     */
    @Override
    public Locale getLocale()
    {
        return request.getLocale();
    }

    /**
     * Equivalent to {@link HttpServletRequest#getLocales()}
     *
     * @return an Enumeration of preferred Locale objects
     */
    @Override
    public Enumeration<Locale> getLocales()
    {
        return request.getLocales();
    }

    /**
     * Return a {@link java.net.SocketAddress} for the local socket.
     * <p>
     * Warning: this can cause a DNS lookup
     *
     * @return the local socket address
     */
    @Override
    public SocketAddress getLocalSocketAddress()
    {
        // TODO: fix when HttpServletRequest can use Unix Socket stuff
        return new InetSocketAddress(request.getLocalAddr(), request.getLocalPort());
    }

    @Override
    public String getMethod()
    {
        return request.getMethod();
    }

    @Override
    public String getOrigin()
    {
        return getHeader("Origin");
    }

    @Override
    public Map<String, List<String>> getParameterMap()
    {
        if (parameterMap == null)
        {
            Map<String, String[]> requestParams = request.getParameterMap();
            if (requestParams != null)
            {
                parameterMap = new HashMap<>(requestParams.size());
                for (Map.Entry<String, String[]> entry : requestParams.entrySet())
                    parameterMap.put(entry.getKey(), Arrays.asList(entry.getValue()));
            }
        }
        return parameterMap;
    }

    @Override
    public String getProtocolVersion()
    {
        String version = request.getHeader(HttpHeader.SEC_WEBSOCKET_VERSION.asString());
        if (version == null)
        {
            return Integer.toString(WebSocketCore.SPEC_VERSION);
        }
        return version;
    }

    @Override
    public String getQueryString()
    {
        return this.queryString;
    }

    /**
     * Return a {@link SocketAddress} for the remote socket.
     * <p>
     * Warning: this can cause a DNS lookup
     *
     * @return the remote socket address
     */
    public SocketAddress getRemoteSocketAddress()
    {
        return new InetSocketAddress(request.getRemoteAddr(), request.getRemotePort());
    }

    public String getRequestPath()
    {
        // Since this can be called from a filter, we need to be smart about determining the target request path.
        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI();
        if (requestPath.startsWith(contextPath))
            requestPath = requestPath.substring(contextPath.length());
        return requestPath;
    }

    @Override
    public URI getRequestURI()
    {
        return requestURI;
    }

    public Object getServletAttribute(String name)
    {
        return request.getAttribute(name);
    }

    public Map<String, Object> getServletAttributes()
    {
        return request.getAttributes();
    }

    public Map<String, List<String>> getServletParameters()
    {
        return getParameterMap();
    }

    /**
     * Return the HttpSession if it exists.
     * <p>
     * Note: this is equivalent to {@link HttpServletRequest#getSession(boolean)}
     * and will not create a new HttpSession.
     */
    public HttpSession getSession()
    {
        return request.getSession(false);
    }

    @Override
    public List<String> getSubProtocols()
    {
        if (subprotocols == null)
        {
            Enumeration<String> requestProtocols = request.getHeaders("Sec-WebSocket-Protocol");
            if (requestProtocols != null)
            {
                subprotocols = new ArrayList<>(2);
                while (requestProtocols.hasMoreElements())
                {
                    String candidate = requestProtocols.nextElement();
                    Collections.addAll(subprotocols, parseProtocols(candidate));
                }
            }
        }
        return subprotocols;
    }

    /**
     * Equivalent to {@link HttpServletRequest#getUserPrincipal()}
     */
    public Principal getUserPrincipal()
    {
        return request.getUserPrincipal();
    }

    @Override
    public boolean hasSubProtocol(String test)
    {
        for (String protocol : getSubProtocols())
        {
            if (protocol.equalsIgnoreCase(test))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSecure()
    {
        return this.secure;
    }

    public boolean isUserInRole(String role)
    {
        return request.isUserInRole(role);
    }

    private String[] parseProtocols(String protocol)
    {
        if (protocol == null)
            return new String[0];
        protocol = protocol.trim();
        if (protocol.length() == 0)
            return new String[0];
        return protocol.split("\\s*,\\s*");
    }

    public void setServletAttribute(String name, Object value)
    {
        request.setAttribute(name, value);
    }
}
