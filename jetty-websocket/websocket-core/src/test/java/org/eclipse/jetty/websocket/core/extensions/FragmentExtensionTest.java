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

package org.eclipse.jetty.websocket.core.extensions;

import org.eclipse.jetty.toolchain.test.ByteBufferAssert;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.*;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.Matchers.is;

public class FragmentExtensionTest extends AbstractExtensionTest
{
    @Rule
    public TestTracker tracker = new TestTracker();

    /**
     * Verify that incoming frames are passed thru without modification
     */
    @Test
    public void testIncomingFrames()
    {
        IncomingFramesCapture capture = new IncomingFramesCapture();

        FragmentExtension ext = new FragmentExtension();
        ExtensionConfig config = ExtensionConfig.parse("fragment;maxLength=4");
        ext.init(config, new WebSocketPolicy(), bufferPool);

        ext.setNextIncomingFrames(capture);

        // Quote
        List<String> quote = new ArrayList<>();
        quote.add("No amount of experimentation can ever prove me right;");
        quote.add("a single experiment can prove me wrong.");
        quote.add("-- Albert Einstein");

        // Manually create frame and pass into extension
        for (String q : quote)
        {
            Frame frame = new Frame(OpCode.TEXT).setPayload(q);
            ext.onReceiveFrame(frame, Callback.NOOP);
        }

        int len = quote.size();
        capture.assertFrameCount(len);

        String prefix;
        int i = 0;
        for (Frame actual : capture.frames)
        {
            prefix = "Frame[" + i + "]";

            Assert.assertThat(prefix + ".opcode", actual.getOpCode(), is(OpCode.TEXT));
            Assert.assertThat(prefix + ".fin", actual.isFin(), is(true));
            Assert.assertThat(prefix + ".rsv1", actual.isRsv1(), is(false));
            Assert.assertThat(prefix + ".rsv2", actual.isRsv2(), is(false));
            Assert.assertThat(prefix + ".rsv3", actual.isRsv3(), is(false));

            ByteBuffer expected = BufferUtil.toBuffer(quote.get(i), StandardCharsets.UTF_8);
            Assert.assertThat(prefix + ".payloadLength", actual.getPayloadLength(), is(expected.remaining()));
            ByteBufferAssert.assertEquals(prefix + ".payload", expected, actual.getPayload().slice());
            i++;
        }
    }

    /**
     * Incoming PING (Control Frame) should pass through extension unmodified
     */
    @Test
    public void testIncomingPing()
    {
        IncomingFramesCapture capture = new IncomingFramesCapture();

        FragmentExtension ext = new FragmentExtension();
        ExtensionConfig config = ExtensionConfig.parse("fragment;maxLength=4");
        ext.init(config, new WebSocketPolicy(), bufferPool);

        ext.setNextIncomingFrames(capture);

        String payload = "Are you there?";
        Frame ping = new Frame(OpCode.PING).setPayload(payload);
        ext.onReceiveFrame(ping, Callback.NOOP);

        capture.assertFrameCount(1);
        capture.assertHasOpCount(OpCode.PING, 1);
        Frame actual = capture.frames.poll();

        Assert.assertThat("Frame.opcode", actual.getOpCode(), is(OpCode.PING));
        Assert.assertThat("Frame.fin", actual.isFin(), is(true));
        Assert.assertThat("Frame.rsv1", actual.isRsv1(), is(false));
        Assert.assertThat("Frame.rsv2", actual.isRsv2(), is(false));
        Assert.assertThat("Frame.rsv3", actual.isRsv3(), is(false));

        ByteBuffer expected = BufferUtil.toBuffer(payload, StandardCharsets.UTF_8);
        Assert.assertThat("Frame.payloadLength", actual.getPayloadLength(), is(expected.remaining()));
        ByteBufferAssert.assertEquals("Frame.payload", expected, actual.getPayload().slice());
    }

    /**
     * Verify that outgoing text frames are fragmented by the maxLength configuration.
     * @throws IOException on test failure
     */
    @Test
    public void testOutgoingFramesByMaxLength() throws IOException
    {
        OutgoingFramesCapture capture = new OutgoingFramesCapture();

        FragmentExtension ext = new FragmentExtension();
        ExtensionConfig config = ExtensionConfig.parse("fragment;maxLength=20");
        ext.init(config, new WebSocketPolicy(), bufferPool);

        ext.setNextOutgoingFrames(capture);

        // Quote
        List<String> quote = new ArrayList<>();
        quote.add("No amount of experimentation can ever prove me right;");
        quote.add("a single experiment can prove me wrong.");
        quote.add("-- Albert Einstein");

        // Write quote as separate frames
        for (String section : quote)
        {
            Frame frame = new Frame(OpCode.TEXT).setPayload(section);
            ext.sendFrame(frame, null, BatchMode.OFF);
        }

        // Expected Frames
        List<Frame> expectedFrames = new ArrayList<>();
        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("No amount of experim").setFin(false));
        expectedFrames.add(new Frame(OpCode.CONTINUATION).setPayload("entation can ever pr").setFin(false));
        expectedFrames.add(new Frame(OpCode.CONTINUATION).setPayload("ove me right;").setFin(true));

        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("a single experiment ").setFin(false));
        expectedFrames.add(new Frame(OpCode.CONTINUATION).setPayload("can prove me wrong.").setFin(true));

        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("-- Albert Einstein").setFin(true));

        // capture.dump();

        int len = expectedFrames.size();
        capture.assertFrameCount(len);

        String prefix;
        LinkedList<Frame> frames = new LinkedList<>(capture.frames);
        for (int i = 0; i < len; i++)
        {
            prefix = "Frame[" + i + "]";
            Frame actualFrame = frames.get(i);
            Frame expectedFrame = expectedFrames.get(i);

            // System.out.printf("actual: %s%n",actualFrame);
            // System.out.printf("expect: %s%n",expectedFrame);

            // Validate Frame
            Assert.assertThat(prefix + ".opcode", actualFrame.getOpCode(), is(expectedFrame.getOpCode()));
            Assert.assertThat(prefix + ".fin", actualFrame.isFin(), is(expectedFrame.isFin()));
            Assert.assertThat(prefix + ".rsv1", actualFrame.isRsv1(), is(expectedFrame.isRsv1()));
            Assert.assertThat(prefix + ".rsv2", actualFrame.isRsv2(), is(expectedFrame.isRsv2()));
            Assert.assertThat(prefix + ".rsv3", actualFrame.isRsv3(), is(expectedFrame.isRsv3()));

            // Validate Payload
            ByteBuffer expectedData = expectedFrame.getPayload().slice();
            ByteBuffer actualData = actualFrame.getPayload().slice();

            Assert.assertThat(prefix + ".payloadLength", actualData.remaining(), is(expectedData.remaining()));
            ByteBufferAssert.assertEquals(prefix + ".payload", expectedData, actualData);
        }
    }

    /**
     * Verify that outgoing text frames are fragmented by default configuration
     * @throws IOException on test failure
     */
    @Test
    public void testOutgoingFramesDefaultConfig() throws IOException
    {
        OutgoingFramesCapture capture = new OutgoingFramesCapture();

        FragmentExtension ext = new FragmentExtension();
        ExtensionConfig config = ExtensionConfig.parse("fragment");
        ext.init(config, new WebSocketPolicy(), bufferPool);

        ext.setNextOutgoingFrames(capture);

        // Quote
        List<String> quote = new ArrayList<>();
        quote.add("No amount of experimentation can ever prove me right;");
        quote.add("a single experiment can prove me wrong.");
        quote.add("-- Albert Einstein");

        // Write quote as separate frames
        for (String section : quote)
        {
            Frame frame = new Frame(OpCode.TEXT).setPayload(section);
            ext.sendFrame(frame, null, BatchMode.OFF);
        }

        // Expected Frames
        List<Frame> expectedFrames = new ArrayList<>();
        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("No amount of experimentation can ever prove me right;"));
        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("a single experiment can prove me wrong."));
        expectedFrames.add(new Frame(OpCode.TEXT).setPayload("-- Albert Einstein"));

        // capture.dump();

        int len = expectedFrames.size();
        capture.assertFrameCount(len);

        String prefix;
        LinkedList<Frame> frames = new LinkedList<>(capture.frames);
        for (int i = 0; i < len; i++)
        {
            prefix = "Frame[" + i + "]";
            Frame actualFrame = frames.get(i);
            Frame expectedFrame = expectedFrames.get(i);

            // Validate Frame
            Assert.assertThat(prefix + ".opcode", actualFrame.getOpCode(), is(expectedFrame.getOpCode()));
            Assert.assertThat(prefix + ".fin", actualFrame.isFin(), is(expectedFrame.isFin()));
            Assert.assertThat(prefix + ".rsv1", actualFrame.isRsv1(), is(expectedFrame.isRsv1()));
            Assert.assertThat(prefix + ".rsv2", actualFrame.isRsv2(), is(expectedFrame.isRsv2()));
            Assert.assertThat(prefix + ".rsv3", actualFrame.isRsv3(), is(expectedFrame.isRsv3()));

            // Validate Payload
            ByteBuffer expectedData = expectedFrame.getPayload().slice();
            ByteBuffer actualData = actualFrame.getPayload().slice();

            Assert.assertThat(prefix + ".payloadLength", actualData.remaining(), is(expectedData.remaining()));
            ByteBufferAssert.assertEquals(prefix + ".payload", expectedData, actualData);
        }
    }

    /**
     * Outgoing PING (Control Frame) should pass through extension unmodified
     * @throws IOException on test failure
     */
    @Test
    public void testOutgoingPing() throws IOException
    {
        OutgoingFramesCapture capture = new OutgoingFramesCapture();

        FragmentExtension ext = new FragmentExtension();
        ExtensionConfig config = ExtensionConfig.parse("fragment;maxLength=4");
        ext.init(config, new WebSocketPolicy(), bufferPool);

        ext.setNextOutgoingFrames(capture);

        String payload = "Are you there?";
        Frame ping = new Frame(OpCode.PING).setPayload(payload);

        ext.sendFrame(ping, null, BatchMode.OFF);

        capture.assertFrameCount(1);

        Frame actual = capture.frames.poll();

        Assert.assertThat("Frame.opcode", actual.getOpCode(), is(OpCode.PING));
        Assert.assertThat("Frame.fin", actual.isFin(), is(true));
        Assert.assertThat("Frame.rsv1", actual.isRsv1(), is(false));
        Assert.assertThat("Frame.rsv2", actual.isRsv2(), is(false));
        Assert.assertThat("Frame.rsv3", actual.isRsv3(), is(false));

        ByteBuffer expected = BufferUtil.toBuffer(payload, StandardCharsets.UTF_8);
        Assert.assertThat("Frame.payloadLength", actual.getPayloadLength(), is(expected.remaining()));
        ByteBufferAssert.assertEquals("Frame.payload", expected, actual.getPayload().slice());
    }
}
