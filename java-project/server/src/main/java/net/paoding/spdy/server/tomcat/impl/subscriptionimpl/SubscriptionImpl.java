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
import java.util.concurrent.atomic.AtomicInteger;

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
// TODO: 订阅的取消还得阅读spdy协议，按照协议的规范实现Server Implementation＆Client Implementation
public class SubscriptionImpl implements Subscription {

    private static AtomicInteger nextStreamId = new AtomicInteger(2);

    static int nextStreamId() {
        int id = nextStreamId.getAndAdd(2);
        if (id < 0) {
            synchronized (SubscriptionImpl.class) {
                if (nextStreamId.intValue() < 0) {
                    nextStreamId = new AtomicInteger(2);
                }
            }
            id = nextStreamId.getAndAdd(2);
        }
        return id;
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

    // 和close()同步
    @Override
    public synchronized void accept() {
        if (closed) {
            throw new IllegalStateException("closed");
        }
        accepted = true;
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

    // 和accept()同步
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        factory.deregister(this);
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
