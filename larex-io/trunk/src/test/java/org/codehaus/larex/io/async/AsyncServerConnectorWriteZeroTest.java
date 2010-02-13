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

package org.codehaus.larex.io.async;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.larex.io.ByteBuffers;
import org.codehaus.larex.io.RuntimeSocketClosedException;
import org.codehaus.larex.io.ThreadLocalByteBuffers;
import org.codehaus.larex.io.connector.async.StandardAsyncServerConnector;
import org.junit.Assert;
import org.junit.Test;

/**
 * @version $Revision: 903 $ $Date$
 */
public class AsyncServerConnectorWriteZeroTest
{
    @Test
    public void testWriteZero() throws Exception
    {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try
        {
            AsyncInterpreterFactory interpreterFactory = new AsyncInterpreterFactory()
            {
                public AsyncInterpreter newAsyncInterpreter(final AsyncCoordinator coordinator)
                {
                    return new EchoAsyncInterpreter(coordinator);
                }
            };

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicInteger writes = new AtomicInteger();
            final AtomicInteger needWrites = new AtomicInteger();
            InetAddress loopback = InetAddress.getByName(null);
            InetSocketAddress address = new InetSocketAddress(loopback, 0);

            StandardAsyncServerConnector serverConnector = new StandardAsyncServerConnector(address, interpreterFactory, threadPool, new ThreadLocalByteBuffers())
            {
                @Override
                protected AsyncChannel newAsyncChannel(SocketChannel channel, AsyncCoordinator coordinator, ByteBuffers byteBuffers)
                {
                    return new StandardAsyncChannel(channel, coordinator, byteBuffers)
                    {
                        private final AtomicInteger writes = new AtomicInteger();

                        @Override
                        protected int writeAggressively(SocketChannel channel, ByteBuffer buffer) throws IOException
                        {
                            if (this.writes.compareAndSet(0, 1))
                            {
                                // In the first aggressive write, we simulate a zero bytes write
                                return 0;
                            }
                            else if (this.writes.compareAndSet(1, 2))
                            {
                                // In the second aggressive write, simulate 1 byte write
                                ByteBuffer newBuffer = ByteBuffer.allocate(1);
                                newBuffer.put(buffer.get());
                                channel.write(newBuffer);
                                return newBuffer.capacity();
                            }
                            else
                            {
                                int result = super.writeAggressively(channel, buffer);
                                latch.countDown();
                                return result;
                            }
                        }
                    };
                }

                @Override
                protected AsyncCoordinator newCoordinator(AsyncSelector selector, Executor threadPool)
                {
                    return new StandardAsyncCoordinator(selector, threadPool)
                    {
                        @Override
                        public void write(ByteBuffer buffer) throws RuntimeSocketClosedException
                        {
                            writes.incrementAndGet();
                            super.write(buffer);
                        }

                        @Override
                        public void needsWrite(boolean needsWrite)
                        {
                            needWrites.incrementAndGet();
                            super.needsWrite(needsWrite);
                        }
                    };
                }
            };
            int port = serverConnector.listen();
            try
            {
                Socket socket = new Socket(loopback, port);
                try
                {
                    OutputStream output = socket.getOutputStream();
                    byte[] bytes = "HELLO".getBytes("UTF-8");
                    output.write(bytes);
                    output.flush();

                    Assert.assertTrue(latch.await(5000, TimeUnit.MILLISECONDS));

                    // One write call from the interpreter
                    Assert.assertEquals(1, writes.get());
                    // Four needsWrite calls:
                    // after writing 0 bytes to enable the writes, then to disable;
                    // after writing 1 byte to enable the writes, then to disable
                    Assert.assertEquals(4, needWrites.get());
                }
                finally
                {
                    socket.close();
                }
            }
            finally
            {
                serverConnector.close();
            }
        }
        finally
        {
            threadPool.shutdown();
        }
    }
}