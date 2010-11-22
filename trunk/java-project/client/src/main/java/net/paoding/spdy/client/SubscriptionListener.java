package net.paoding.spdy.client;

import org.jboss.netty.handler.codec.http.HttpResponse;

public interface SubscriptionListener {

    void responseReceived(Subscription subscription, HttpResponse response);

    //
}
