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
package net.paoding.spdy.server.tomcat.impl.subscriptionimpl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.Listeners;
import net.paoding.spdy.server.subscription.Subscription;
import net.paoding.spdy.server.subscription.SubscriptionFactory;
import net.paoding.spdy.server.subscription.SubscriptionFuture;
import net.paoding.spdy.server.subscription.SubscriptionStateListener;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionImpl implements Subscription {

    private static int nextStreamId = 2;

    static synchronized int nextStreamId() {
        nextStreamId += 2;
        if (nextStreamId < 0) {
            nextStreamId = 2;
        }
        return nextStreamId;
    }

    //---------------------------------

    /** 属性容器 */
    private Map<String, Object> attributes;

    /** 该订阅请求的streamId,即associatedStreamId */
    private final int associatedId;

    /** 所属的工厂 */
    private SubscriptionFactoryImpl factory;

    /** 到客户端的连接 */
    private Channel channel;

    /** 被(业务逻辑)接受了？ */
    private boolean accepted;

    /** 被关闭了？ */
    private boolean closed;

    /** 侦听器容器 */
    private final Listeners<SubscriptionStateListener> listeners = new Listeners<SubscriptionStateListener>(
            false) {

        /** 通知给各个注册的侦听器 */
        public void notifyListener(SubscriptionStateListener listener) {
            listener.closed(SubscriptionImpl.this);
        };
    };

    /**
     * 构造一个订阅对象
     * 
     * @param factory
     * @param syn
     */
    public SubscriptionImpl(SubscriptionFactoryImpl factory, SynStream syn) {
        this.factory = factory;
        this.associatedId = syn.getStreamId();
        this.channel = syn.getChannel();
    }

    /**
     * 返回该订阅所属的factory
     * 
     * @return
     */
    public SubscriptionFactory getFactory() {
        return factory;
    }

    @Override
    public synchronized void accept() {
        if (isClosed()) {
            throw new IllegalStateException("closed");
        }
        if (!accepted) {
            factory.register(this);
            accepted = true;
        }
    }

    @Override
    public boolean isAccepted() {
        return accepted;
    }

    @Override
    public void setAttribute(String name, Object o) {
        if (attributes == null) {
            attributes = new HashMap<String, Object>();
        }
        this.attributes.put(name, o);
    }

    @Override
    public Map<String, Object> getAttributes() {
        if (attributes == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(this.attributes);
    }

    @Override
    public Object getAttribute(String name) {
        if (attributes == null) {
            return null;
        }
        return this.attributes.get(name);
    }

    @Override
    public void removeAttribute(String name) {
        if (attributes != null) {
            this.attributes.remove(name);
        }
    }

    public int getAssociatedId() {
        return associatedId;
    }

    @Override
    public void addListener(SubscriptionStateListener listener) {
        listeners.addListener(listener);
    }

    @Override
    public SubscriptionFuture push(HttpResponse message) {
        if (!accepted) {
            throw new IllegalStateException("not accepted");
        }
        if (closed) {
            throw new IllegalStateException("closed");
        }
        SpdyResponse spdyMessage = new SpdyResponse();
        spdyMessage.setMessage(message);
        spdyMessage.setAssociatedId(associatedId);
        spdyMessage.setStreamId(nextStreamId());
        ChannelFuture channelFuture = Channels.write(channel, spdyMessage);
        return new SubscriptionFutureImpl(this, channelFuture);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        factory.subscriptionClosed(this);
        listeners.setSuccess();
        this.attributes = null;
        this.channel = null;
        this.factory = null;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

}