package net.paoding.spdy.client;

import java.net.SocketAddress;

import net.paoding.spdy.common.frame.frames.Ping;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface Connector {

    /**
     * 当时是否处于连接状态
     */
    boolean isConnected();

    /**
     * 返回远程服务地址，如果尚未连接返回null
     */
    SocketAddress getRemoteAddress();

    /**
     * 返回本地地址，如果尚未连接返回null
     */
    SocketAddress getLocalAddress();

    /**
     * 向远程服务发送一个ping
     */
    Future<Ping> ping();

    /**
     * 向服务器发送情求
     */
    Future<HttpResponse> doRequest(HttpRequest request);

    /**
     * 
     * @param request
     * @param listener
     * @return
     */
    SubscriptionStub subscribe(HttpRequest request, SubscriptionListener listener);

    /**
     * 关闭connector，使不能再发送请求
     */
    Future<Connector> close();

    /**
     * @throws IllegalStateException 如果还未连接
     * @see #close()
     */
    Future<Connector> getCloseFuture();

}
