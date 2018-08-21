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

package org.eclipse.jetty.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.Name;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.util.thread.ThreadPool;


/**
 *
 * A monitor for low resources, low resources can be detected by:
 * <ul>
 * <li>{@link ThreadPool#isLowOnThreads()} if {@link Connector#getExecutor()} is
 * an instance of {@link ThreadPool} and {@link #setMonitorThreads(boolean)} is true.</li>
 * <li>If {@link #setMaxMemory(long)} is non zero then low resources is detected if the JVMs
 * {@link Runtime} instance has {@link Runtime#totalMemory()} minus {@link Runtime#freeMemory()}
 * greater than {@link #getMaxMemory()}</li>
 * <li>If {@link #setMaxConnections(int)} is non zero then low resources is detected if the total number
 * of connections exceeds {@link #getMaxConnections()}.  This feature is deprecated and replaced by
 * {@link ConnectionLimit}</li>
 * </ul>
 *
 */
@ManagedObject ("Monitor for low resource conditions and activate a low resource mode if detected")
public class LowResourceMonitor extends AbstractLifeCycle
{
    private static final Logger LOG = Log.getLogger(LowResourceMonitor.class);

    protected final Server _server;
    private Scheduler _scheduler;
    private Connector[] _monitoredConnectors;
    private Set<AbstractConnector> _acceptingConnectors = new HashSet<>();
    private int _period=1000;


    private int _lowResourcesIdleTimeout=1000;
    private int _maxLowResourcesTime=0;

    private final AtomicBoolean _low = new AtomicBoolean();

    private String _reasons;

    private long _lowStarted;
    private boolean _acceptingInLowResources = true;

    private Set<LowResourceCheck> _lowResourceChecks = new HashSet<>();

    private final Runnable _monitor = new Runnable()
    {
        @Override
        public void run()
        {
            if (isRunning())
            {
                monitor();
                _scheduler.schedule( _monitor, _period, TimeUnit.MILLISECONDS);
            }
        }
    };

    private boolean _monitorThreads=true;
    private int _maxConnections;
    private long _maxMemory;

    public LowResourceMonitor(@Name("server") Server server)
    {
        _server = server;

        // create default LowResourceChecks..
        if(_monitorThreads){
            this._lowResourceChecks.add( new MainThreadPoolLowResourceCheck() );
        }

        if(_monitorThreads && !getMonitoredConnectors().isEmpty()){
            this._lowResourceChecks.add( new ConnectorsThreadPoolLowResourceCheck());
        }

        this._lowResourceChecks.add(new MemoryLowResourceCheck());
    }

    @ManagedAttribute("True if low available threads status is monitored")
    public boolean getMonitorThreads()
    {
        return _monitorThreads;
    }

    /**
     * @param monitorThreads If true, check connectors executors to see if they are
     * {@link ThreadPool} instances that are low on threads.
     */
    public void setMonitorThreads(boolean monitorThreads)
    {
        _monitorThreads = monitorThreads;
    }

    /**
     * @return The maximum connections allowed for the monitored connectors before low resource handling is activated
     * @deprecated Replaced by ConnectionLimit
     */
    @ManagedAttribute("The maximum connections allowed for the monitored connectors before low resource handling is activated")
    @Deprecated
    public int getMaxConnections()
    {
        return _maxConnections;
    }

    /**
     * @param maxConnections The maximum connections before low resources state is triggered
     * @deprecated Replaced by ConnectionLimit
     */
    @Deprecated
    public void setMaxConnections(int maxConnections)
    {
        if (maxConnections>0)
            LOG.warn("LowResourceMonitor.setMaxConnections is deprecated. Use ConnectionLimit.");
        _maxConnections = maxConnections;
    }


    @ManagedAttribute("The reasons the monitored connectors are low on resources")
    public String getReasons()
    {
        return _reasons;
    }

    protected void setReasons(String reasons)
    {
        _reasons = reasons;
    }

    @ManagedAttribute("Are the monitored connectors low on resources?")
    public boolean isLowOnResources()
    {
        return _low.get();
    }

    protected boolean enableLowOnResources(boolean expectedValue, boolean newValue)
    {
        return _low.compareAndSet(expectedValue, newValue);
    }

    @ManagedAttribute("The reason(s) the monitored connectors are low on resources")
    public String getLowResourcesReasons()
    {
        return _reasons;
    }

    protected void setLowResourcesReasons(String reasons)
    {
        _reasons = reasons;
    }

    @ManagedAttribute("Get the timestamp in ms since epoch that low resources state started")
    public long getLowResourcesStarted()
    {
        return _lowStarted;
    }

    public void setLowResourcesStarted(long lowStarted)
    {
        _lowStarted = lowStarted;
    }

    @ManagedAttribute("The monitored connectors. If null then all server connectors are monitored")
    public Collection<Connector> getMonitoredConnectors()
    {
        if (_monitoredConnectors==null)
            return Collections.emptyList();
        return Arrays.asList( _monitoredConnectors);
    }

    /**
     * @param monitoredConnectors The collections of Connectors that should be monitored for low resources.
     */
    public void setMonitoredConnectors(Collection<Connector> monitoredConnectors)
    {
        if (monitoredConnectors==null || monitoredConnectors.size()==0)
            _monitoredConnectors=null;
        else
            _monitoredConnectors = monitoredConnectors.toArray(new Connector[monitoredConnectors.size()]);
    }

    protected Connector[] getMonitoredOrServerConnectors()
    {
        if (_monitoredConnectors!=null && _monitoredConnectors.length>0)
            return _monitoredConnectors;
        return _server.getConnectors();
    }

    @ManagedAttribute("If false, new connections are not accepted while in low resources")
    public boolean isAcceptingInLowResources()
    {
        return _acceptingInLowResources;
    }

    public void setAcceptingInLowResources(boolean acceptingInLowResources)
    {
        _acceptingInLowResources = acceptingInLowResources;
    }

    @ManagedAttribute("The monitor period in ms")
    public int getPeriod()
    {
        return _period;
    }

    /**
     * @param periodMS The period in ms to monitor for low resources
     */
    public void setPeriod(int periodMS)
    {
        _period = periodMS;
    }

    @ManagedAttribute("The idletimeout in ms to apply to all existing connections when low resources is detected")
    public int getLowResourcesIdleTimeout()
    {
        return _lowResourcesIdleTimeout;
    }

    /**
     * @param lowResourcesIdleTimeoutMS The timeout in ms to apply to EndPoints when in the low resources state.
     */
    public void setLowResourcesIdleTimeout(int lowResourcesIdleTimeoutMS)
    {
        _lowResourcesIdleTimeout = lowResourcesIdleTimeoutMS;
    }

    @ManagedAttribute("The maximum time in ms that low resources condition can persist before lowResourcesIdleTimeout is applied to new connections as well as existing connections")
    public int getMaxLowResourcesTime()
    {
        return _maxLowResourcesTime;
    }

    /**
     * @param maxLowResourcesTimeMS The time in milliseconds that a low resource state can persist before the low resource idle timeout is reapplied to all connections
     */
    public void setMaxLowResourcesTime(int maxLowResourcesTimeMS)
    {
        _maxLowResourcesTime = maxLowResourcesTimeMS;
    }



    @ManagedAttribute("The maximum memory (in bytes) that can be used before low resources is triggered.  Memory used is calculated as (totalMemory-freeMemory).")
    public long getMaxMemory()
    {
        return _maxMemory;
    }

    /**
     * @param maxMemoryBytes The maximum memory in bytes in use before low resources is triggered.
     */
    public void setMaxMemory(long maxMemoryBytes)
    {
        _maxMemory = maxMemoryBytes;
    }

    public Set<LowResourceCheck> getLowResourceChecks()
    {
        return _lowResourceChecks;
    }

    public void setLowResourceChecks( Set<LowResourceCheck> lowResourceChecks )
    {
        this._lowResourceChecks = lowResourceChecks;
    }

    public void addLowResourceCheck( LowResourceCheck lowResourceCheck )
    {
        this._lowResourceChecks.add(lowResourceCheck);
    }

    protected void monitor()
    {

        String reasons="";


        for(LowResourceCheck lowResourceCheck : _lowResourceChecks)
        {
            if(lowResourceCheck.isLowOnResources())
            {
                reasons = lowResourceCheck.toString();
                break;
            }
        }

        if (reasons!=null)
        {
            // Log the reasons if there is any change in the cause
            if (!reasons.equals(getReasons()))
            {
                LOG.warn("Low Resources: {}",reasons);
                setReasons(reasons);
            }

            // Enter low resources state?
            if (enableLowOnResources(false,true))
            {
                setLowResourcesReasons(reasons);
                setLowResourcesStarted(System.currentTimeMillis());
                setLowResources();
            }

            // Too long in low resources state?
            if ( getMaxLowResourcesTime()>0 && (System.currentTimeMillis()-getLowResourcesStarted())>getMaxLowResourcesTime())
                setLowResources();
        }
        else
        {
            if (enableLowOnResources(true,false))
            {
                LOG.info("Low Resources cleared");
                setLowResourcesReasons(null);
                setLowResourcesStarted(0);
                setReasons(null);
                clearLowResources();
            }
        }
    }

    @Override
    protected void doStart() throws Exception
    {
        _scheduler = _server.getBean(Scheduler.class);

        if (_scheduler==null)
        {
            _scheduler=new LRMScheduler();
            _scheduler.start();
        }
        super.doStart();

        _scheduler.schedule(_monitor,_period,TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doStop() throws Exception
    {
        if (_scheduler instanceof LRMScheduler )
            _scheduler.stop();
        super.doStop();
    }

    protected void setLowResources()
    {
        for(Connector connector : getMonitoredOrServerConnectors())
        {
            if (connector instanceof AbstractConnector)
            {
                AbstractConnector c = (AbstractConnector)connector;
                if (!isAcceptingInLowResources() && c.isAccepting())
                {
                    _acceptingConnectors.add(c);
                    c.setAccepting(false);
                }
            }

            for ( EndPoint endPoint : connector.getConnectedEndPoints())
                endPoint.setIdleTimeout(_lowResourcesIdleTimeout);
        }
    }

    protected void clearLowResources()
    {
        for(Connector connector : getMonitoredOrServerConnectors())
        {
            for (EndPoint endPoint : connector.getConnectedEndPoints())
                endPoint.setIdleTimeout(connector.getIdleTimeout());
        }

        for (AbstractConnector connector : _acceptingConnectors)
        {
            connector.setAccepting(true);
        }
        _acceptingConnectors.clear();
    }

    protected String low(String reasons, String newReason)
    {
        if (reasons==null)
            return newReason;
        return reasons+", "+newReason;
    }


    private static class LRMScheduler extends ScheduledExecutorScheduler
    {
    }

    interface LowResourceCheck
    {
        boolean isLowOnResources();
    }

    //------------------------------------------------------
    // default implementations for backward compat
    //------------------------------------------------------

    public class MainThreadPoolLowResourceCheck implements LowResourceCheck
    {
        private String reason;

        public MainThreadPoolLowResourceCheck()
        {
            // no op
        }

        @Override
        public boolean isLowOnResources()
        {
            ThreadPool serverThreads = _server.getThreadPool();
            if (serverThreads.isLowOnThreads())
            {
                reason="Server low on threads: "+serverThreads;
                return true;
            }
            return false;
        }

        @Override
        public String toString()
        {
            return reason;
        }
    }

    public class ConnectorsThreadPoolLowResourceCheck implements LowResourceCheck
    {
        private String reason;


        public ConnectorsThreadPoolLowResourceCheck()
        {
            // no op
        }

        @Override
        public boolean isLowOnResources()
        {
            ThreadPool serverThreads = _server.getThreadPool();

            int connections=0;

            for(Connector connector : getMonitoredConnectors())
            {
                connections+=connector.getConnectedEndPoints().size();
                Executor executor = connector.getExecutor();
                if (executor instanceof ThreadPool && executor!=serverThreads)
                {
                    ThreadPool connectorThreads=(ThreadPool)executor;
                    if (_monitorThreads && connectorThreads.isLowOnThreads())
                    {
                        reason ="Connector low on threads: "+connectorThreads;
                        return true;
                    }
                }
            }
            if (_maxConnections>0 && connections>_maxConnections)
            {
                reason ="Max Connections exceeded: "+connections+">"+_maxConnections;
                return true;
            }
            return false;
        }

        @Override
        public String toString()
        {
            return reason;
        }
    }

    public class MemoryLowResourceCheck implements LowResourceCheck
    {
        private String reason;

        public MemoryLowResourceCheck()
        {
            // no op
        }

        @Override
        public boolean isLowOnResources()
        {
            long memory=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
            if (_maxMemory>0 && memory>_maxMemory)
            {
                reason = "Max memory exceeded: "+memory+">"+_maxMemory;
                return true;
            }
            return false;
        }

        @Override
        public String toString()
        {
            return reason;
        }
    }


}
