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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.server.subscription.Subscription;
import net.paoding.spdy.server.subscription.SubscriptionFactory;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionFactoryImpl implements SubscriptionFactory {

    private Map<Integer, Subscription> subscriptions = new HashMap<Integer, Subscription>();

    public SubscriptionFactoryImpl() {
    }

    @Override
    public Subscription createSubscription(SynStream syn) {
        Subscription subscription = new SubscriptionImpl(this, syn);
        subscriptions.put(subscription.getAssociatedId(), subscription);
        return subscription;
    }

    @Override
    public Subscription getSubscription(int streamId) {
        return subscriptions.get(streamId);
    }

    // package access
    void deregister(Subscription subscription) {
        subscriptions.remove(subscription.getAssociatedId());
    }

    @Override
    public void destory() {
        List<Subscription> list = new ArrayList<Subscription>(subscriptions.values());
        for (Subscription subscription : list) {
            subscription.close();
        }

    }
}
