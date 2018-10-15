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

import org.eclipse.jetty.websocket.core.FrameHandler;

/**
 * WebSocket Core API - Factory for Servlet based API's to use for creating API specific FrameHandler instances that
 * websocket-core will eventually utilize.
 * <p>
 *     This is used by Servlet based APIs only.
 * </p>
 */
public interface FrameHandlerFactory
{
    String ATTR_HANDLERS = "org.eclipse.jetty.websocket.servlet.FrameHandlerFactories";

    /**
     * Attempt to create a FrameHandler from the provided websocketPojo.
     *
     * @param websocketPojo the websocket pojo to work with
     * @param upgradeRequest the Upgrade Handshake Request used to create the FrameHandler
     * @param upgradeResponse the Upgrade Handshake Response used to create the FrameHandler
     * @return the API specific FrameHandler, or null if this implementation is unable to create the FrameHandler (allowing another {@link FrameHandlerFactory} to try)
     */
    FrameHandler newFrameHandler(Object websocketPojo, ServletUpgradeRequest upgradeRequest, ServletUpgradeResponse upgradeResponse);
}
