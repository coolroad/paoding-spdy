/*
 * Copyright 2010-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License i distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.paoding.spdy.test.server;

import java.util.concurrent.Executor;

import net.paoding.spdy.server.tomcat.SpdyProtocol;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class MockServer {

    private int port;

    private SpdyProtocol acceptor;

    private Executor executor = new Executor() {

        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    public MockServer(int port) {
        this.port = port;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public void start() {
        if (acceptor != null) {
            throw new IllegalStateException();
        }
        // server
        SpdyProtocol acceptor = new SpdyProtocol();
        acceptor.setAdapter(new MockAdapter());
        acceptor.setPort(port);
        acceptor.setExecutor(executor);
        acceptor.init();
        acceptor.start();

    }

    public SpdyProtocol getAcceptor() {
        return acceptor;
    }

    public void destroy() {
        if (acceptor != null) {
            acceptor.destroy();
        }
    }
}
