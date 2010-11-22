package net.paoding.spdy.server.subscription;

import net.paoding.spdy.common.frame.frames.SynStream;

/**
 * 每个Channel有自己的SubscriptionFactory
 * 
 * @author qieqie
 * 
 */
public interface SubscriptionFactory {

    public ServerSubscription createSubscription(SynStream syn);

    public ServerSubscription getSubscription(int streamId);

}
