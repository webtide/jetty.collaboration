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

package org.eclipse.jetty.client;

import static org.eclipse.jetty.http.HttpHeader.CONNECTION;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.CombinableMatcher.both;
import static org.junit.Assert.assertEquals;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.toolchain.test.MavenTestingUtils;
import org.eclipse.jetty.util.HttpCookieStore;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Sweeper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class DuplexConnectionPoolTest
{
    private static Server server;

    @BeforeClass
    public static void startServer() throws Exception
    {
        server = new Server();

        HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendDateHeader(false);
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.addCustomizer(new SecureRequestCustomizer());

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(MavenTestingUtils.getTestResourcePath("pooled/localhost.keystore").toString());
        sslContextFactory.setKeyStorePassword("changeit");
        SslConnectionFactory sslConnectionFactory = new SslConnectionFactory(sslContextFactory, "http/1.1");

        ServerConnector connector = new ServerConnector(server, sslConnectionFactory, new HttpConnectionFactory(httpConfiguration));

        server.addConnector(connector);

        Handler remotePortEchoHandler = new AbstractHandler()
        {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            {
                response.addHeader("remotePort", String.valueOf(baseRequest.getRemotePort()));
                baseRequest.setHandled(true);
            }
        };

        HandlerList handlerList = new HandlerList();
        handlerList.addHandler(remotePortEchoHandler);
        handlerList.addHandler(new DefaultHandler());

        server.setHandler(handlerList);
        server.start();
    }

    @AfterClass
    public static void stopServer() throws Exception
    {
        server.stop();
    }

    private HttpClient createHttpClient()
    {
        SslContextFactory ssl = new SslContextFactory();
        // ssl.setEndpointIdentificationAlgorithm("HTTPS");
        ssl.setKeyStorePath(MavenTestingUtils.getTestResourcePath("pooled/localhost.keystore").toString());
        ssl.setKeyStorePassword("changeit");
        ssl.setTrustStorePath(MavenTestingUtils.getTestResourcePath("pooled/localhost.truststore").toString());
        ssl.setTrustStorePassword("changeit");

        HttpClientTransportOverHTTP transport = new HttpClientTransportOverHTTP(2);

        HttpClient client = new HttpClient(transport, ssl);
        client.setMaxConnectionsPerDestination(20);
        client.setMaxRequestsQueuedPerDestination(1024);

        client.setCookieStore(new HttpCookieStore.Empty());
        client.setUserAgentField(null);

        client.setIdleTimeout(60000);
        client.setConnectTimeout(5000);
        client.setAddressResolutionTimeout(5000);

        client.setConnectBlocking(false);

        client.setByteBufferPool(new MappedByteBufferPool());

        QueuedThreadPool qtp = new QueuedThreadPool(200, 8);
        qtp.setName("http-client-test-shared");
        client.setExecutor(qtp);
        client.addBean(qtp, true);

        ScheduledExecutorScheduler scheduler = new ScheduledExecutorScheduler("http-client-test-shared-scheduler", true);
        client.setScheduler(scheduler);

        client.setSocketAddressResolver(new SocketAddressResolver.Async(
                client.getExecutor(),
                client.getScheduler(),
                5000));

        client.addBean(new Sweeper(client.getScheduler(), 5000), true);

        ConnectionStatistics connectionStats = new ConnectionStatistics();
        client.addBean(connectionStats);

        return client;
    }

    @Test
    public void testKeepAlive() throws Exception
    {
        HttpClient client = createHttpClient();

        try
        {
            client.start();

            ContentResponse response1 = executeRequest(client, "/");
            Thread.sleep(1000);
            ContentResponse response2 = executeRequest(client, "/");
            Thread.sleep(1000);
            ContentResponse response3 = executeRequest(client, "/");

            assertEquals(response1.getStatus(), 200);
            assertEquals(response2.getStatus(), 200);
            assertEquals(response3.getStatus(), 200);

            assertThat(response1.getHeaders().get(CONNECTION), is(nullValue()));
            assertThat(response2.getHeaders().get(CONNECTION), is(nullValue()));
            assertThat(response3.getHeaders().get(CONNECTION), is(nullValue()));

            assertThat(response1.getHeaders().get("remotePort"), is(notNullValue()));
            assertThat(response2.getHeaders().get("remotePort"), is(notNullValue()));
            assertThat(response3.getHeaders().get("remotePort"), is(notNullValue()));

            long port1 = response1.getHeaders().getLongField("remotePort");
            long port2 = response2.getHeaders().getLongField("remotePort");
            long port3 = response3.getHeaders().getLongField("remotePort");

            assertEquals(port2, port1);
            assertEquals(port3, port1);
            assertThat("Port", port1, is(both(greaterThanOrEqualTo(1024L)).and(lessThanOrEqualTo(65535L))));
        }
        finally
        {
            client.stop();
        }
    }

    private ContentResponse executeRequest(HttpClient client, String path) throws InterruptedException, ExecutionException, TimeoutException
    {
        URI uri = server.getURI().resolve(path);
        return client.GET(uri);
    }
}
