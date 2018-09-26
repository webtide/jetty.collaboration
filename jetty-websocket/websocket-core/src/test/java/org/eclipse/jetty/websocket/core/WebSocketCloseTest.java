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

package org.eclipse.jetty.websocket.core;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NetworkConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.BlockingArrayQueue;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.core.extensions.WebSocketExtensionRegistry;
import org.eclipse.jetty.websocket.core.server.RFC6455Handshaker;
import org.eclipse.jetty.websocket.core.server.WebSocketNegotiator;
import org.eclipse.jetty.websocket.core.server.WebSocketUpgradeHandler;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests of a core server with a fake client
 *
 */
public class WebSocketCloseTest extends WebSocketTester
{
    @Rule
    public TestTracker tracker = new TestTracker();
    
    private static Logger LOG = Log.getLogger(WebSocketCloseTest.class);

    private WebSocketServer server;
    private Socket client;

    enum State {OPEN, ICLOSED, OCLOSED}

    @After
    public void after() throws Exception
    {
        if (server!=null)
            server.stop();
    }

    public void setup(State state) throws Exception
    {
        switch (state)
        {
            case OPEN:
            {
                TestFrameHandler serverHandler = new TestFrameHandler();
                server = new WebSocketServer(0, serverHandler);
                server.start();
                client = newClient(server.getLocalPort());

                assertTrue(server.handler.opened.await(10,TimeUnit.SECONDS));

                assertThat(server.handler.getCoreSession().toString(), containsString("OPEN"));
                LOG.info("Server: OPEN");

                break;
            }
            case ICLOSED:
            {
                TestFrameHandler serverHandler = new TestFrameHandler();

                server = new WebSocketServer(0, serverHandler);
                server.start();
                client = newClient(server.getLocalPort());

                assertTrue(server.handler.opened.await(10,TimeUnit.SECONDS));

                server.handler.getCoreSession().demand(1);
                client.getOutputStream().write(RawFrameBuilder.buildClose(new CloseStatus(CloseStatus.NORMAL), true));
                Frame frame = serverHandler.receivedFrames.poll(10,TimeUnit.SECONDS);
                assertNotNull(frame);
                assertThat(new CloseStatus(frame.getPayload()).getCode(), is(CloseStatus.NORMAL));

                assertThat(server.handler.getCoreSession().toString(), containsString("ICLOSED"));
                LOG.info("Server: ICLOSED");

                break;
            }
            case OCLOSED:
            {
                TestFrameHandler serverHandler = new TestFrameHandler();

                server = new WebSocketServer(0, serverHandler);
                server.start();
                client = newClient(server.getLocalPort());

                assertTrue(server.handler.opened.await(10,TimeUnit.SECONDS));

                server.sendFrame(CloseStatus.toFrame(CloseStatus.NORMAL));
                Frame frame = receiveFrame(client.getInputStream());
                assertNotNull(frame);
                assertThat(new CloseStatus(frame.getPayload()).getCode(), is(CloseStatus.NORMAL));

                assertThat(server.handler.getCoreSession().toString(), containsString("OCLOSED"));
                LOG.info("Server: OCLOSED");

                break;
            }
        }
    }

    @Test
    public void serverClose_ICLOSED() throws Exception
    {
        setup(State.ICLOSED);

        server.handler.receivedCallback.poll().succeeded();
        Frame frame = receiveFrame(client.getInputStream());
        assertNotNull(frame);
        assertThat(new CloseStatus(frame.getPayload()).getCode(), is(CloseStatus.NORMAL));

        assertTrue(server.handler.closed.await(10,TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(),is(CloseStatus.NORMAL));
    }

    @Test
    public void serverDifferentClose_ICLOSED() throws Exception
    {
        setup(State.ICLOSED);

        server.sendFrame(CloseStatus.toFrame(CloseStatus.SHUTDOWN));
        server.handler.receivedCallback.poll().succeeded();
        Frame frame = receiveFrame(client.getInputStream());
        assertNotNull(frame);
        assertThat(new CloseStatus(frame.getPayload()).getCode(), is(CloseStatus.SHUTDOWN));

        assertTrue(server.handler.closed.await(10,TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(),is(CloseStatus.SHUTDOWN));
    }

    @Test
    public void serverFailClose_ICLOSED() throws Exception
    {
        try(StacklessLogging stackless = new StacklessLogging(WebSocketChannel.class))
        {
            setup(State.ICLOSED);
            server.handler.receivedCallback.poll().failed(new Exception("test failure"));

            Frame frame = receiveFrame(client.getInputStream());
            assertNotNull(frame);
            assertThat(new CloseStatus(frame.getPayload()).getCode(), is(CloseStatus.SERVER_ERROR));

            assertTrue(server.handler.closed.await(10, TimeUnit.SECONDS));
            assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.SERVER_ERROR));
        }
    }

    @Test
    public void clientClose_OCLOSED() throws Exception
    {
        setup(State.OCLOSED);
        server.handler.getCoreSession().demand(1);
        client.getOutputStream().write(RawFrameBuilder.buildClose(new CloseStatus(CloseStatus.NORMAL), true));
        assertNotNull(server.handler.receivedFrames.poll(10,TimeUnit.SECONDS));
        server.handler.receivedCallback.poll().succeeded();

        assertTrue(server.handler.closed.await(10, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NORMAL));

        assertNull(receiveFrame(client.getInputStream()));
    }

    @Test
    public void clientDifferentClose_OCLOSED() throws Exception
    {
        setup(State.OCLOSED);
        server.handler.getCoreSession().demand(1);
        client.getOutputStream().write(RawFrameBuilder.buildClose(new CloseStatus(CloseStatus.BAD_PAYLOAD), true));
        assertNotNull(server.handler.receivedFrames.poll(10,TimeUnit.SECONDS));
        server.handler.receivedCallback.poll().succeeded();

        assertTrue(server.handler.closed.await(10, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.BAD_PAYLOAD));

        assertNull(receiveFrame(client.getInputStream()));
    }

    @Test
    public void clientCloseServerFailClose_OCLOSED() throws Exception
    {
        try(StacklessLogging stackless = new StacklessLogging(WebSocketChannel.class))
        {
            setup(State.OCLOSED);
            server.handler.getCoreSession().demand(1);
            client.getOutputStream().write(RawFrameBuilder.buildClose(new CloseStatus(CloseStatus.NORMAL), true));
            assertNotNull(server.handler.receivedFrames.poll(10, TimeUnit.SECONDS));
            server.handler.receivedCallback.poll().failed(new Exception("Test"));

            assertTrue(server.handler.closed.await(10, TimeUnit.SECONDS));
            assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NORMAL));

            assertNull(receiveFrame(client.getInputStream()));
        }
    }

    @Test
    public void clientSendsBadFrame_OPEN() throws Exception
    {
        setup(State.OPEN);

        client.getOutputStream().write(RawFrameBuilder.buildFrame(OpCode.PONG, "pong frame not masked", false));
        server.handler.getCoreSession().demand(1);
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.PROTOCOL));
        assertThat(server.handler.closeStatus.getReason(), containsString("Client MUST mask all frames"));
    }

    @Test
    public void clientSendsBadFrame_OCLOSED() throws Exception
    {
        setup(State.OCLOSED);

        client.getOutputStream().write(RawFrameBuilder.buildFrame(OpCode.PONG, "pong frame not masked", false));
        server.handler.getCoreSession().demand(1);
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.PROTOCOL));
        assertThat(server.handler.closeStatus.getReason(), containsString("Client MUST mask all frames"));
    }

    @Test
    public void clientSendsBadFrame_ICLOSED() throws Exception
    {
        setup(State.ICLOSED);

        client.getOutputStream().write(RawFrameBuilder.buildFrame(OpCode.PONG, "pong frame not masked", false));
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));
        server.handler.getCoreSession().demand(1);
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));

        server.close();
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NORMAL));
    }

    @Test
    public void clientAborts_OPEN() throws Exception
    {
        setup(State.OPEN);

        client.close();
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));
        server.handler.getCoreSession().demand(1);
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NO_CLOSE));
        assertThat(server.handler.closeStatus.getReason(), containsString("IOException"));
    }

    @Test
    public void clientAborts_OCLOSED() throws Exception
    {
        setup(State.OCLOSED);

        client.close();
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));
        server.handler.getCoreSession().demand(1);
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NO_CLOSE));
        assertThat(server.handler.closeStatus.getReason(), containsString("IOException"));
    }

    @Test
    public void clientAborts_ICLOSED() throws Exception
    {
        setup(State.ICLOSED);

        client.close();
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));
        server.handler.getCoreSession().demand(1);
        assertFalse(server.handler.closed.await(250, TimeUnit.MILLISECONDS));
        server.close();
        assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.NORMAL));
    }

    @Test
    public void onFrameThrows_OPEN() throws Exception
    {
        setup(State.OPEN);

        client.getOutputStream().write(RawFrameBuilder.buildFrame(OpCode.BINARY, "binary", true));

        try (StacklessLogging stacklessLogging = new StacklessLogging(WebSocketChannel.class))
        {
            server.handler.getCoreSession().demand(1);
            assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        }

        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.SERVER_ERROR));
        assertThat(server.handler.closeStatus.getReason(), containsString("onReceiveFrame throws for binary frames"));
    }

    @Test
    public void onFrameThrows_OCLOSED() throws Exception
    {
        setup(State.OCLOSED);

        client.getOutputStream().write(RawFrameBuilder.buildFrame(OpCode.BINARY, "binary", true));

        try (StacklessLogging stacklessLogging = new StacklessLogging(WebSocketChannel.class))
        {
            server.handler.getCoreSession().demand(1);
            assertTrue(server.handler.closed.await(5, TimeUnit.SECONDS));
        }

        assertThat(server.handler.closeStatus.getCode(), is(CloseStatus.SERVER_ERROR));
        assertThat(server.handler.closeStatus.getReason(), containsString("onReceiveFrame throws for binary frames"));
    }

    static class TestFrameHandler implements FrameHandler
    {
        private CoreSession session;

        protected BlockingQueue<Frame> receivedFrames = new BlockingArrayQueue<>();
        protected BlockingQueue<Callback> receivedCallback = new BlockingArrayQueue<>();
        protected CountDownLatch opened = new CountDownLatch(1);
        protected CountDownLatch closed = new CountDownLatch(1);
        protected CloseStatus closeStatus = null;

        public CoreSession getCoreSession()
        {
            return session;
        }

        public BlockingQueue<Frame> getFrames()
        {
            return receivedFrames;
        }

        @Override
        public void onOpen(CoreSession coreSession)
        {
            LOG.info("onOpen {}", coreSession);
            this.session = coreSession;
            opened.countDown();
        }

        @Override
        public void onReceiveFrame(Frame frame, Callback callback)
        {
            LOG.info("onFrame: " + BufferUtil.toDetailString(frame.getPayload()));
            receivedCallback.offer(callback);
            receivedFrames.offer(Frame.copy(frame));

            if(frame.getOpCode() == OpCode.BINARY)
                throw new IllegalArgumentException("onReceiveFrame throws for binary frames");
        }

        @Override
        public void onClosed(CloseStatus closeStatus)
        {
            LOG.info("onClosed {}",closeStatus);
            this.closeStatus = closeStatus;
            closed.countDown();
        }

        @Override
        public void onError(Throwable cause) throws Exception
        {
            LOG.info("onError {} ",cause==null?null:cause.toString());
        }

        @Override
        public boolean isDemanding()
        {
            return true;
        }

        public void sendText(String text)
        {
            Frame frame = new Frame(OpCode.TEXT);
            frame.setFin(true);
            frame.setPayload(text);

            getCoreSession().sendFrame(frame, Callback.NOOP, BatchMode.AUTO);
        }
    }

    static class WebSocketServer extends AbstractLifeCycle
    {
        private static Logger LOG = Log.getLogger(WebSocketServer.class);
        private final Server server;
        private final TestFrameHandler handler;

        public void doStart() throws Exception
        {
            server.start();
        }

        public void doStop() throws Exception
        {
            server.stop();
        }

        public int getLocalPort()
        {
            return server.getBean(NetworkConnector.class).getLocalPort();
        }

        public WebSocketServer(int port, TestFrameHandler frameHandler)
        {
            this.handler = frameHandler;
            server = new Server();
            server.getBean(QueuedThreadPool.class).setName("WSCoreServer");
            ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory());

            connector.addBean(new WebSocketPolicy());
            connector.addBean(new RFC6455Handshaker());
            connector.setPort(port);
            connector.setIdleTimeout(1000000);
            server.addConnector(connector);

            ContextHandler context = new ContextHandler("/");
            server.setHandler(context);
            WebSocketNegotiator negotiator =  new TestWebSocketNegotiator(new DecoratedObjectFactory(), new WebSocketExtensionRegistry(), connector.getByteBufferPool(), frameHandler);

            WebSocketUpgradeHandler upgradeHandler = new TestWebSocketUpgradeHandler(negotiator);
            context.setHandler(upgradeHandler);
        }

        public void sendFrame(Frame frame)
        {
            handler.getCoreSession().sendFrame(frame, Callback.NOOP, BatchMode.OFF);
        }

        public void sendText(String line)
        {
            LOG.info("sending {}...", line);
            handler.sendText(line);
        }

        public BlockingQueue<Frame> getFrames()
        {
            return handler.getFrames();
        }

        public void close()
        {
            handler.getCoreSession().close(CloseStatus.NORMAL, "WebSocketServer Initiated Close", Callback.NOOP);
        }

        public boolean isOpen()
        {
            return handler.getCoreSession().isOpen();
        }
    }
}
