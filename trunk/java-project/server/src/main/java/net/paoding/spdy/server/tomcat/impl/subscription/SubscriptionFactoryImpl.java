package net.paoding.spdy.server.tomcat.impl.subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.server.subscription.ServerSubscription;
import net.paoding.spdy.server.subscription.SubscriptionFactory;

public class SubscriptionFactoryImpl implements SubscriptionFactory {

    private final Executor executor;

    private Map<Integer, ServerSubscriptionImpl> subscriptions = new HashMap<Integer, ServerSubscriptionImpl>();

    public SubscriptionFactoryImpl(Executor executor) {
        this.executor = executor;
    }

    public Executor getExecutor() {
        return executor;
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
        for (ServerSubscriptionImpl serverSubscriptionImpl : list) {
            serverSubscriptionImpl.setClosed();
        }

    }
}
