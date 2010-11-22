package net.paoding.spdy.client;

import java.net.SocketAddress;

import net.paoding.spdy.common.frame.frames.Ping;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public interface Connector {

    public HttpFuture<Connector> connect();

    public HttpFuture<Connector> getConnectFuture();

    boolean isConnected();

    public SocketAddress getRemoteAddress();

    public SocketAddress getLocalAddress();

    public HttpFuture<Connector> getCloseFuture();

    public HttpFuture<Ping> ping();

    public HttpFuture<HttpResponse> doRequest(HttpRequest request);
    
    public Subscription subscribe(HttpRequest request, SubscriptionListener listener);

    public HttpFuture<Connector> close();

}
