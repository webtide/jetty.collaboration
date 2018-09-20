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

package org.eclipse.jetty.websocket.common;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.common.message.MessageOutputStream;
import org.eclipse.jetty.websocket.core.TestableLeakTrackingBufferPool;
import org.eclipse.jetty.websocket.core.WebSocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;

public class MessageOutputStreamTest
{
    private static final Logger LOG = Log.getLogger(MessageOutputStreamTest.class);

    public TestableLeakTrackingBufferPool bufferPool = new TestableLeakTrackingBufferPool("Test");

    @AfterEach
    public void afterEach()
    {
        bufferPool.assertNoLeaks();
    }

    private WebSocketPolicy policy;
    private OutgoingMessageCapture channelCapture;

    @BeforeEach
    public void setupTest() throws Exception
    {
        policy = new WebSocketPolicy();
        policy.setInputBufferSize(1024);

        channelCapture = new OutgoingMessageCapture(policy);
    }

    @Test
    public void testMultipleWrites() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(channelCapture, policy.getOutputBufferSize(), bufferPool))
        {
            stream.write("Hello".getBytes("UTF-8"));
            stream.write(" ".getBytes("UTF-8"));
            stream.write("World".getBytes("UTF-8"));
        }

        assertThat("Socket.binaryMessages.size", channelCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = channelCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, is("Hello World"));
    }

    @Test
    public void testSingleWrite() throws Exception
    {
        try (MessageOutputStream stream = new MessageOutputStream(channelCapture, policy.getOutputBufferSize(), bufferPool))
        {
            stream.write("Hello World".getBytes("UTF-8"));
        }

        assertThat("Socket.binaryMessages.size", channelCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = channelCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, is("Hello World"));
    }

    @Test
    public void testWriteLarge_RequiringMultipleBuffers() throws Exception
    {
        int bufsize = (int) (policy.getOutputBufferSize() * 2.5);
        byte buf[] = new byte[bufsize];
        LOG.debug("Buffer sizes: max:{}, test:{}", policy.getOutputBufferSize(), bufsize);
        Arrays.fill(buf, (byte) 'x');
        buf[bufsize - 1] = (byte) 'o'; // mark last entry for debugging

        try (MessageOutputStream stream = new MessageOutputStream(channelCapture, policy.getOutputBufferSize(), bufferPool))
        {
            stream.write(buf);
        }

        assertThat("Socket.binaryMessages.size", channelCapture.binaryMessages.size(), is(1));
        ByteBuffer buffer = channelCapture.binaryMessages.poll(1, TimeUnit.SECONDS);
        String message = BufferUtil.toUTF8String(buffer);
        assertThat("Message", message, endsWith("xxxxxo"));
    }
}
