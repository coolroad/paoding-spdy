package net.paoding.spdy.client.impl;

import net.paoding.spdy.client.HttpFuture;
import net.paoding.spdy.client.HttpFutureListener;
import net.paoding.spdy.client.Subscription;
import net.paoding.spdy.client.SubscriptionListener;

import org.jboss.netty.handler.codec.http.HttpResponse;

public class SubscriptionImpl implements Subscription {

    final NettyConnector connector;

    final int streamId;

    final HttpFutureListener<HttpResponse> listener;

    final HttpFuture<HttpResponse> subscriptionFutrue;

    public SubscriptionImpl(int streamId, NettyConnector connector,
            HttpFuture<HttpResponse> subscriptionFutrue, final SubscriptionListener listener) {
        this.connector = connector;
        this.streamId = streamId;
        this.subscriptionFutrue = subscriptionFutrue;
        this.listener = new HttpFutureListener<HttpResponse>() {

            @Override
            public void operationComplete(HttpFuture<HttpResponse> httpFuture) throws Exception {
                listener.responseReceived(SubscriptionImpl.this, httpFuture.getTarget());
            }
        };
    }

    // TODO: 要不要return future?
    @Override
    public void close() {
        connector.desubscript(this);
    }

    @Override
    public HttpFuture<HttpResponse> getSubscriptionFutrue() {
        return subscriptionFutrue;
    }

}
