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

import java.io.IOException;

import net.paoding.spdy.common.http.DefaultHttpResponse;
import net.paoding.spdy.server.subscription.Subscription;

import org.apache.coyote.ActionCode;
import org.apache.coyote.Adapter;
import org.apache.coyote.Request;
import org.apache.coyote.Response;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.net.SocketStatus;
import org.jboss.netty.buffer.ChannelBuffers;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class MockAdapter implements Adapter {

    static byte[] hello = "hello".getBytes();

    static byte[] accepted = "accepted".getBytes();

    @Override
    public void service(Request request, Response response) throws Exception {
        String uri = request.requestURI().toString();
        if (uri.equals("/hello")) {
            doHello(request, response);
        } else if (uri.equals("/subscribe")) {
            doSubscribe(request, response);
        } else {
            response.setStatus(404);
            response.finish();
        }
    }

    private void doSubscribe(Request request, Response response) throws IOException {
        final Subscription sub = (Subscription) request.getAttribute(Subscription.REQUEST_ATTR);
        sub.accept();

        response.setStatus(200);
        response.setHeader("content-length", String.valueOf(accepted.length));
        response.setHeader("content-type", "plain/text");
        response.getHook().action(ActionCode.ACTION_COMMIT, null);
        ByteChunk chunk = new ByteChunk();
        chunk.append(accepted, 0, accepted.length);
        response.getOutputBuffer().doWrite(chunk, response);
        response.finish();

        // push
        new Thread() {

            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {}
                DefaultHttpResponse pushed = new DefaultHttpResponse();
                pushed.setContent(ChannelBuffers.wrappedBuffer("pushed".getBytes()));
                sub.push(pushed);
            }
        }.start();
    }

    private void doHello(Request request, Response response) throws IOException {
        response.setStatus(200);
        response.setHeader("content-length", String.valueOf(hello.length));
        response.setHeader("content-type", "plain/text");
        response.getHook().action(ActionCode.ACTION_COMMIT, null);
        ByteChunk chunk = new ByteChunk();
        chunk.append(hello, 0, hello.length);
        response.getOutputBuffer().doWrite(chunk, response);
        response.finish();
    }

    @Override
    public boolean event(Request request, Response response, SocketStatus ss) throws Exception {
        return false;
    }

}
