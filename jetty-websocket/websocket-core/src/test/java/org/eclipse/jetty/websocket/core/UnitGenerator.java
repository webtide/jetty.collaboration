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

import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.util.BufferUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Extension of the default WebSocket Generator for unit testing purposes
 */
public class UnitGenerator extends Generator
{
    // Client side framing mask
    private static final byte[] MASK = {0x11, 0x22, 0x33, 0x44};
    private final boolean applyMask;
    
    public UnitGenerator(WebSocketCore.Behavior behavior)
    {
        super(new MappedByteBufferPool());
        applyMask = (behavior == WebSocketCore.Behavior.CLIENT);
    }
    
    public ByteBuffer asBuffer(List<Frame> frames)
    {
        int bufferLength = 0;
        for (Frame f : frames)
            bufferLength += f.getPayloadLength() + Generator.MAX_HEADER_LENGTH;
        
        ByteBuffer buffer = ByteBuffer.allocate(bufferLength);
        generate(buffer, frames);
        BufferUtil.flipToFlush(buffer, 0);
        return buffer;
    }
    
    public ByteBuffer asBuffer(Frame... frames)
    {
        return asBuffer(Arrays.asList(frames));
    }
    
    public void generate(ByteBuffer buffer, List<Frame> frames)
    {
        // Generate frames
        for (Frame f : frames)
        {
            if (applyMask)
                f.setMask(MASK);
            
            generateWholeFrame(f, buffer);
        }
    }
    
    public void generate(ByteBuffer buffer, Frame... frames)
    {
        // Generate frames
        for (Frame f : frames)
        {
            if (applyMask)
                f.setMask(MASK);
            
            generateWholeFrame(f, buffer);
        }
    }
    
    public ByteBuffer generate(Frame frame)
    {
        return asBuffer(Collections.singletonList(frame));
    }
    
    public void generate(ByteBuffer buffer, Frame frame)
    {
        if (applyMask)
            frame.setMask(MASK);
        generateWholeFrame(frame, buffer);
    }
}
