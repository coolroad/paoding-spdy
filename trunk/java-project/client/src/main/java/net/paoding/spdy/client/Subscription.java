package net.paoding.spdy.client;

import org.jboss.netty.handler.codec.http.HttpResponse;

public interface Subscription {

    public void close();

    public HttpFuture<HttpResponse> getSubscriptionFutrue();

}
