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

import org.eclipse.jetty.toolchain.test.TestTracker;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

import static org.eclipse.jetty.websocket.core.OpCode.BINARY;
import static org.eclipse.jetty.websocket.core.OpCode.CLOSE;
import static org.eclipse.jetty.websocket.core.OpCode.CONTINUATION;
import static org.eclipse.jetty.websocket.core.OpCode.PING;
import static org.eclipse.jetty.websocket.core.OpCode.PONG;
import static org.eclipse.jetty.websocket.core.OpCode.TEXT;
import static org.junit.Assert.assertThat;

public class OpCodeTest
{
    @Rule
    public TestTracker tracker = new TestTracker();
    
    @Test
    public void testIsControl()
    {
        assertThat(OpCode.isControlFrame(OpCode.BINARY),Matchers.is(false));
        assertThat(OpCode.isControlFrame(OpCode.CLOSE),Matchers.is(true));
        assertThat(OpCode.isControlFrame(OpCode.CONTINUATION),Matchers.is(false));
        assertThat(OpCode.isControlFrame(OpCode.PING),Matchers.is(true));
        assertThat(OpCode.isControlFrame(OpCode.PONG),Matchers.is(true));
        assertThat(OpCode.isControlFrame(OpCode.TEXT),Matchers.is(false));
    }

    @Test
    public void testIsData()
    {
        assertThat(OpCode.isDataFrame(BINARY),Matchers.is(true));
        assertThat(OpCode.isDataFrame(CLOSE),Matchers.is(false));
        assertThat(OpCode.isDataFrame(CONTINUATION),Matchers.is(true));
        assertThat(OpCode.isDataFrame(PING),Matchers.is(false));
        assertThat(OpCode.isDataFrame(PONG),Matchers.is(false));
        assertThat(OpCode.isDataFrame(TEXT),Matchers.is(true));
    }
    
    private void testSequence(byte... opcode)
    {
        OpCode.Sequence sequence = new OpCode.Sequence(); 
        for (int i=0; i<opcode.length; i++)
        {
            byte o = (byte)(opcode[i] & 0x0F);
            boolean unfin = (opcode[i] & 0x80) != 0;
            
            sequence.check(o,!unfin);
        }
    }
    
    private byte unfin(byte o)
    {        
        return (byte)(0x80|o);
    }
 
    @Test
    public void testFinSequences()
    {
        testSequence(TEXT,BINARY,PING,PONG,CLOSE);
    }
    
    @Test
    public void testContinuationSequences()
    {
        testSequence(unfin(TEXT),CONTINUATION,CLOSE);
        testSequence(unfin(TEXT),unfin(CONTINUATION),CONTINUATION,CLOSE);
        testSequence(unfin(TEXT),unfin(CONTINUATION),unfin(CONTINUATION),CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),unfin(CONTINUATION),CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),unfin(CONTINUATION),unfin(CONTINUATION),CONTINUATION,CLOSE);
    }
    
    @Test
    public void testInterleavedContinuationSequences()
    {
        testSequence(unfin(TEXT),PING,CONTINUATION,CLOSE);
        testSequence(unfin(TEXT),PING,unfin(CONTINUATION),PONG,CONTINUATION,CLOSE);
        testSequence(unfin(TEXT),PING,unfin(CONTINUATION),PONG,unfin(CONTINUATION),PING,CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),PING,CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),PING,unfin(CONTINUATION),PONG,CONTINUATION,CLOSE);
        testSequence(unfin(BINARY),PING,unfin(CONTINUATION),PONG,unfin(CONTINUATION),PING,CONTINUATION,CLOSE);
    }

    @Test(expected=ProtocolException.class)
    public void testBadContinuationAlreadyFIN()
    {
        testSequence(TEXT,CONTINUATION,CLOSE);
    }

    @Test(expected=ProtocolException.class)
    public void testBadContinuationNoCont()
    {
        testSequence(unfin(TEXT),TEXT);
    }

    @Test(expected=ProtocolException.class)
    public void testFramesAfterClose()
    {
        testSequence(TEXT,CLOSE,TEXT);
    }

    @Test(expected=ProtocolException.class)
    public void testControlFramesAfterClose()
    {
        testSequence(TEXT,CLOSE,PING);
    }
}
