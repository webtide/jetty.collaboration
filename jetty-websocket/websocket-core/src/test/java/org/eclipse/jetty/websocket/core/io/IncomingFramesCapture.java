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

package org.eclipse.jetty.websocket.core.io;

import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.websocket.core.IncomingFrames;
import org.eclipse.jetty.websocket.core.frames.Frame;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class IncomingFramesCapture implements IncomingFrames
{
    public BlockingQueue<Frame> frames = new LinkedBlockingDeque<>();
    
    @Override
    public void onReceiveFrame(org.eclipse.jetty.websocket.core.frames.Frame frame, Callback callback)
    {
        Frame copy = Frame.copy(frame);
        frames.offer(copy);
        callback.succeeded();
    }
    
    public void assertHasOpCount(byte opCode, int expectedCount)
    {
        assertThat("Frame Count [op=" + opCode + "]", getFrameCount(opCode), is(expectedCount));
    }
    
    public void assertFrameCount(int expectedCount)
    {
        assertThat("Frame Count", frames.size(), is(expectedCount));
    }
    
    public int getFrameCount(byte op)
    {
        int count = 0;
        for (Frame frame : frames)
        {
            if (frame.getOpCode() == op)
            {
                count++;
            }
        }
        return count;
    }
}
