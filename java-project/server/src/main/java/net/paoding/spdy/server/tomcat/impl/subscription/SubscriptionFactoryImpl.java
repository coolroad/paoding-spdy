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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.server.subscription.ServerSubscription;
import net.paoding.spdy.server.subscription.SubscriptionFactory;

public class SubscriptionFactoryImpl implements SubscriptionFactory {

    private Map<Integer, ServerSubscriptionImpl> subscriptions = new HashMap<Integer, ServerSubscriptionImpl>();

    public SubscriptionFactoryImpl() {
    }

    @Override
    public ServerSubscription createSubscription(SynStream syn) {
        return new ServerSubscriptionImpl(this, syn);
    }

    public void register(ServerSubscriptionImpl subscription) {
        if (subscription.getFactory() != this) {
            throw new IllegalStateException("wrong factory");
        }
        subscriptions.put(subscription.getAssociatedId(), subscription);
    }

    @Override
    public ServerSubscriptionImpl getSubscription(int streamId) {
        return subscriptions.get(streamId);
    }

    public void subscriptionClosed(ServerSubscriptionImpl sub) {
        subscriptions.remove(sub.getAssociatedId());
    }

    public void close() {
        List<ServerSubscriptionImpl> list = new ArrayList<ServerSubscriptionImpl>(
                subscriptions.values());
        for (ServerSubscription subscription : list) {
            subscription.setClosed();
        }

    }
}
