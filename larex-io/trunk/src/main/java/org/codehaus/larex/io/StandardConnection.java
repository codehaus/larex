/*
 * Copyright (c) 2010-2010 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.larex.io;

import java.nio.ByteBuffer;

/**
 * @version $Revision$ $Date$
 */
public abstract class StandardConnection extends WritableConnection
{
    public StandardConnection(Coordinator coordinator)
    {
        super(coordinator);
    }

    public final void onOpen()
    {
        getCoordinator().needsRead(true);
    }

    public void onReady()
    {
    }

    public void onRead(ByteBuffer buffer)
    {
        try
        {
            read(buffer);
        }
        catch (Exception x)
        {
            logger.info("Unexpected exception", x);
        }
        finally
        {
            getCoordinator().needsRead(true);
        }
    }

    public void onReadTimeout()
    {
    }

    protected abstract void read(ByteBuffer buffer);
}
