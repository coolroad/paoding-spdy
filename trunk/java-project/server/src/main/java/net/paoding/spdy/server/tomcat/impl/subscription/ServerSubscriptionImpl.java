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
package net.paoding.spdy.server.tomcat.impl.subscription;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.Listeners;
import net.paoding.spdy.server.subscription.Message;
import net.paoding.spdy.server.subscription.ServerSubscription;
import net.paoding.spdy.server.subscription.SubscriptionListener;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.Channels;

public class ServerSubscriptionImpl implements ServerSubscription {

    private Map<String, Object> attributes = new HashMap<String, Object>();

    private final int associatedId;

    private SubscriptionFactoryImpl factory;

    private Channel channel;

    private boolean closed;

    private boolean accepted;

    private final String uriPrefix;

    private static int nextStreamId = 2;

    static synchronized int nextStreamId() {
        nextStreamId += 2;
        if (nextStreamId < 0) {
            nextStreamId = 2;
        }
        return nextStreamId;
    }

    public SubscriptionFactoryImpl getFactory() {
        return factory;
    }

    private Listeners<SubscriptionListener> listeners = new Listeners<SubscriptionListener>(false) {

        public void notifyListener(final SubscriptionListener listener) {
            listener.closed(ServerSubscriptionImpl.this);
        };
    };

    public ServerSubscriptionImpl(SubscriptionFactoryImpl factory, SynStream syn) {
        this.factory = factory;
        this.associatedId = syn.getStreamId();
        this.channel = syn.getChannel();
        try {
            URI uri = new URI(syn.getHeader("url"));
            String uriString = uri.toString();
            int i = uriString.indexOf('/', uri.getScheme().length() + "://".length());
            if (i != -1) {
                uriPrefix = uriString.substring(0, i);
            } else {
                uriPrefix = uriString;
            }
        } catch (URISyntaxException e) {
            throw new Error(e);
        }
    }

    public void setAttribute(String name, Object o) {
        this.attributes.put(name, o);
    }

    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    public Object getAttribute(String name) {
        return this.attributes.get(name);
    }

    @Override
    public void removeAttribute(String name) {
        this.attributes.remove(name);
    }

    public int getAssociatedId() {
        return associatedId;
    }

    @Override
    public void push(Message message) {
        if (!accepted) {
            throw new IllegalStateException("the subscription has not yet been accepted.");
        }
        SpdyMessage spdyMessage = new SpdyMessage(uriPrefix);
        spdyMessage.setMessage(message);
        spdyMessage.setAssociatedId(associatedId);
        spdyMessage.setStreamId(nextStreamId());
        Channels.write(channel, spdyMessage);
    }

    // TODO: accept有问题；应该只有在accept后，这个类才保存channel等对象？以免被错误一直保存住，从而内存内存泄漏
    @Override
    public void accept() {
        factory.register(this);
        accepted = true;
    }

    @Override
    public boolean isAccepted() {
        return accepted;
    }

    @Override
    public void setClosed() {
        if (closed) {
            return;
        }
        closed = true;
        factory.subscriptionClosed(this);
        listeners.setSuccess();
        this.channel = null;
        this.factory = null;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void addListener(SubscriptionListener listener) {
        listeners.addListener(listener);
    }

}
