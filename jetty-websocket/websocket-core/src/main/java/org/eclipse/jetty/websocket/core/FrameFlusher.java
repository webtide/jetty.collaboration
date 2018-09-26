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

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingCallback;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class FrameFlusher extends IteratingCallback
{
    public static final Frame FLUSH_FRAME = new Frame(OpCode.BINARY);
    private static final Logger LOG = Log.getLogger(FrameFlusher.class);

    private final ByteBufferPool bufferPool;
    private final EndPoint endPoint;
    private final int bufferSize;
    private final Generator generator;
    private final int maxGather;
    private final Deque<FrameEntry> queue = new ArrayDeque<>();
    private final List<FrameEntry> pending;
    private final List<ByteBuffer> buffers;
    private boolean closed;
    private Throwable terminated;
    private ByteBuffer aggregate;
    private BatchMode batchMode;

    public FrameFlusher(ByteBufferPool bufferPool, Generator generator, EndPoint endPoint, int bufferSize)
    {
        this(bufferPool,generator, endPoint,bufferSize, 8);
    }

    public FrameFlusher(ByteBufferPool bufferPool, Generator generator, EndPoint endPoint, int bufferSize, int maxGather)
    {
        this.bufferPool = bufferPool;
        this.endPoint = endPoint;
        this.bufferSize = bufferSize;
        this.generator = Objects.requireNonNull(generator);
        this.maxGather = maxGather;
        this.pending = new ArrayList<>(maxGather);
        this.buffers = new ArrayList<>((maxGather * 2) + 1);
    }

    public void enqueue(Frame frame, Callback callback, BatchMode batchMode)
    {
        FrameEntry entry = new FrameEntry(frame, callback, batchMode);

        Throwable closed;
        synchronized (this)
        {
            closed = terminated;
            if (closed == null)
            {
                byte opCode = frame.getOpCode();
                if (opCode == OpCode.PING || opCode == OpCode.PONG)
                    queue.offerFirst(entry);
                else
                    queue.offerLast(entry);
            }
        }

        if (closed == null)
            iterate();
        else
            notifyCallbackFailure(callback, closed);
    }

    @Override
    protected Action process() throws Throwable
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Flushing {}", this);

        int space = aggregate == null ? bufferSize : BufferUtil.space(aggregate);
        BatchMode currentBatchMode = BatchMode.AUTO;
        synchronized (this)
        {
            if (closed)
                return Action.SUCCEEDED;

            if (terminated != null)
                throw terminated;

            while (!queue.isEmpty() && pending.size() <= maxGather)
            {
                FrameEntry entry = queue.poll();
                currentBatchMode = BatchMode.max(currentBatchMode, entry.batchMode);

                // Force flush if we need to.
                if (entry.frame == FLUSH_FRAME)
                    currentBatchMode = BatchMode.OFF;

                int payloadLength = BufferUtil.length(entry.frame.getPayload());
                int approxFrameLength = Generator.MAX_HEADER_LENGTH + payloadLength;

                // If it is a "big" frame, avoid copying into the aggregate buffer.
                if (approxFrameLength > (bufferSize >> 2))
                    currentBatchMode = BatchMode.OFF;

                // If the aggregate buffer overflows, do not batch.
                space -= approxFrameLength;
                if (space <= 0)
                    currentBatchMode = BatchMode.OFF;

                pending.add(entry);
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("{} processing {} pending: {}", this, pending.size(), pending);

        if (pending.isEmpty())
        {
            if (batchMode != BatchMode.AUTO)
            {
                // Nothing more to do, release the aggregate buffer if we need to.
                // Releasing it here rather than in succeeded() allows for its reuse.
                releaseAggregate();
                return Action.IDLE;
            }

            if (LOG.isDebugEnabled())
                LOG.debug("{} auto flushing", this);

            return flush();
        }

        batchMode = currentBatchMode;

        return currentBatchMode == BatchMode.OFF ? flush() : batch();
    }

    private Action batch()
    {
        if (aggregate == null)
        {
            aggregate = bufferPool.acquire(bufferSize, true);
            if (LOG.isDebugEnabled())
                LOG.debug("{} acquired aggregate buffer {}", this, aggregate);
        }

        for (FrameEntry entry : pending)
        {
            entry.generateHeaderBytes(aggregate);

            ByteBuffer payload = entry.frame.getPayload();
            if (BufferUtil.hasContent(payload))
                BufferUtil.append(aggregate, payload);
        }
        if (LOG.isDebugEnabled())
            LOG.debug("{} aggregated {} frames: {}", this, pending.size(), pending);

        // We just aggregated the pending, so we need to succeed their callbacks.
        succeeded();

        return Action.SCHEDULED;
    }

    private Action flush()
    {
        if (!BufferUtil.isEmpty(aggregate))
        {
            buffers.add(aggregate);
            if (LOG.isDebugEnabled())
                LOG.debug("{} flushing aggregate {}", this, aggregate);
        }

        for (FrameEntry entry : pending)
        {
            // Skip the "synthetic" frame used for flushing.
            if (entry.frame == FLUSH_FRAME)
                continue;

            buffers.add(entry.generateHeaderBytes());
            ByteBuffer payload = entry.frame.getPayload();
            if (BufferUtil.hasContent(payload))
                buffers.add(payload);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("{} flushing {} frames: {}", this, pending.size(), pending);

        if (buffers.isEmpty())
        {
            releaseAggregate();
            // We may have the FLUSH_FRAME to notify.
            succeedEntries();
            return Action.IDLE;
        }

        endPoint.write(this, buffers.toArray(new ByteBuffer[buffers.size()]));
        buffers.clear();
        return Action.SCHEDULED;
    }

    private int getQueueSize()
    {
        synchronized (this)
        {
            return queue.size();
        }
    }

    @Override
    public void succeeded()
    {
        succeedEntries();
        super.succeeded();
    }

    private void succeedEntries()
    {
        for (FrameEntry entry : pending)
        {
            notifyCallbackSuccess(entry.callback);
            entry.release();
            if (entry.frame.getOpCode() == OpCode.CLOSE)
            {
                terminate(new ClosedChannelException(), true);
                endPoint.shutdownOutput();
            }
        }
        pending.clear();
    }

    @Override
    public void onCompleteFailure(Throwable failure)
    {
        releaseAggregate();

        Throwable closed;
        synchronized (this)
        {
            closed = terminated;
            if (closed == null)
                terminated = failure;
            pending.addAll(queue);
            queue.clear();
        }

        for (FrameEntry entry : pending)
        {
            notifyCallbackFailure(entry.callback, failure);
            entry.release();
        }
        pending.clear();
    }

    private void releaseAggregate()
    {
        if (BufferUtil.isEmpty(aggregate))
        {
            bufferPool.release(aggregate);
            aggregate = null;
        }
    }

    public void terminate(Throwable cause, boolean close)
    {
        Throwable reason;
        synchronized (this)
        {
            closed = close;
            reason = terminated;
            if (reason == null)
                terminated = cause;
        }
        if (LOG.isDebugEnabled())
            LOG.debug("{} {}", reason == null ? "Terminating" : "Terminated", this);
        if (reason == null && !close)
            iterate();
    }

    protected void notifyCallbackSuccess(Callback callback)
    {
        try
        {
            if (callback != null)
            {
                callback.succeeded();
            }
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying success of callback " + callback, x);
        }
    }

    protected void notifyCallbackFailure(Callback callback, Throwable failure)
    {
        try
        {
            if (callback != null)
            {
                callback.failed(failure);
            }
        }
        catch (Throwable x)
        {
            if (LOG.isDebugEnabled())
                LOG.debug("Exception while notifying failure of callback " + callback, x);
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s@%x[queueSize=%d,aggregateSize=%d,terminated=%s]",
                             getClass().getSimpleName(),
                             hashCode(),
                             getQueueSize(),
                             aggregate == null ? 0 : aggregate.position(),
                             terminated);
    }

    private class FrameEntry
    {
        private final Frame frame;
        private final Callback callback;
        private final BatchMode batchMode;
        private ByteBuffer headerBuffer;

        private FrameEntry(Frame frame, Callback callback, BatchMode batchMode)
        {
            this.frame = Objects.requireNonNull(frame);
            this.callback = callback;
            this.batchMode = batchMode;
        }

        private ByteBuffer generateHeaderBytes()
        {
            return headerBuffer = generator.generateHeaderBytes(frame);
        }

        private void generateHeaderBytes(ByteBuffer buffer)
        {
            int pos = BufferUtil.flipToFill(buffer);
            generator.generateHeaderBytes(frame, buffer);
            BufferUtil.flipToFlush(buffer,pos);
        }

        private void release()
        {
            if (headerBuffer != null)
            {
                generator.getBufferPool().release(headerBuffer);
                headerBuffer = null;
            }
        }

        @Override
        public String toString()
        {
            return String.format("%s[%s,%s,%s,%s]", getClass().getSimpleName(), frame, callback, batchMode, terminated);
        }
    }
}
