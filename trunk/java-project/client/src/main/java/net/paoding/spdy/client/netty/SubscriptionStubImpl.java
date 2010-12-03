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
package net.paoding.spdy.client.netty;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.FutureListener;
import net.paoding.spdy.client.SubscriptionStub;
import net.paoding.spdy.client.SubscriptionListener;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionStubImpl implements SubscriptionStub {

    /** 所属的连接 */
    final NettyConnector connector;

    final SpdyRequest request;

    /** 订阅请求的streamId */
    final int streamId;

    /** 订阅请求的响应 */
    Future<HttpResponse> responseFuture;

    /** 订阅服务器端推送侦听器 */
    final FutureListener<HttpResponse> listener;

    private final CloseFuture closeFuture;

    private boolean closed;

    public SubscriptionStubImpl(NettyConnector connector, SpdyRequest request,
            final SubscriptionListener listener) {
        this.connector = connector;
        this.request = request;
        this.streamId = request.streamId;
        this.closeFuture = new CloseFuture(this);
        this.listener = new FutureListener<HttpResponse>() {

            @Override
            public void operationComplete(Future<HttpResponse> httpFuture) throws Exception {
                listener.responseReceived(SubscriptionStubImpl.this, httpFuture.get());
            }
        };
    }

    @Override
    public Connector getConnector() {
        return connector;
    }

    @Override
    public HttpRequest getRequest() {
        return request.httpRequest;
    }

    @Override
    public Future<HttpResponse> getResponseFuture() {
        return responseFuture;
    }

    public void setResponseFuture(Future<HttpResponse> responseFuture) {
        this.responseFuture = responseFuture;
    }

    @Override
    public Future<SubscriptionStub> getCloseFuture() {
        return closeFuture;
    }

    @Override
    public synchronized Future<SubscriptionStub> close() {
        if (!closed) {
            ChannelFuture channelFuture = connector.desubscript(this);
            channelFuture.addListener(new ChannelFutureListener() {

                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        closeFuture.setClosed();
                    }
                }
            });
            closed = true;
        }
        return closeFuture;
    }

    private static final class CloseFuture extends ResponseFuture<SubscriptionStub, SubscriptionStub> {

        CloseFuture(SubscriptionStub subscription) {
            super(subscription.getConnector(), subscription);
        }

        @Override
        public boolean setSuccess() {
            // User is not supposed to call this method - ignore silently.
            return false;
        }

        @Override
        public boolean setFailure(Throwable cause) {
            // User is not supposed to call this method - ignore silently.
            return false;
        }

        boolean setClosed() {
            return super.setSuccess();
        }
    }
}
