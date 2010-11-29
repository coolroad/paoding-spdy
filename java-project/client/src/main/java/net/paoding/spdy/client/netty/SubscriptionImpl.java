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
import net.paoding.spdy.client.Subscription;
import net.paoding.spdy.client.SubscriptionListener;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionImpl implements Subscription {

    /** 所属的连接 */
    final NettyConnector connector;

    /** 订阅请求的streamId */
    final int streamId;

    /** 订阅请求的响应 */
    final Future<HttpResponse> subscriptionFuture;

    /** 订阅服务器端推送侦听器 */
    final FutureListener<HttpResponse> listener;

    public SubscriptionImpl(int streamId, NettyConnector connector,
            Future<HttpResponse> subscriptionFutrue, final SubscriptionListener listener) {
        this.connector = connector;
        this.streamId = streamId;
        this.subscriptionFuture = subscriptionFutrue;
        this.listener = new FutureListener<HttpResponse>() {

            @Override
            public void operationComplete(Future<HttpResponse> httpFuture) throws Exception {
                listener.responseReceived(SubscriptionImpl.this, httpFuture.getTarget());
            }
        };
    }

    @Override
    public Connector getConnector() {
        return connector;
    }

    @Override
    public Future<HttpResponse> getFuture() {
        return subscriptionFuture;
    }

    @Override
    public Future<Subscription> close() {
        ChannelFuture channelFuture = connector.desubscript(this);
        final FutureImpl<Subscription> future = new FutureImpl<Subscription>(connector,
                channelFuture);
        channelFuture.addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isSuccess()) {
                    future.setSuccess();
                } else {
                    future.setFailure(future.getCause());
                }
            }
        });
        return future;
    }
}
