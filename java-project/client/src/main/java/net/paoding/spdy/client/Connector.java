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
     * 远程服务建立连接，使可以发送请求
     * 
     * @throws IllegalStateException 如果已经连接过
     */
    HttpFuture<Connector> connect();

    /**
     * 返回连接操作的future对象
     * 
     * @see #connect()
     */
    HttpFuture<Connector> getConnectFuture();

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
    HttpFuture<Ping> ping();

    /**
     * 向服务器发送情求
     */
    HttpFuture<HttpResponse> doRequest(HttpRequest request);

    /**
     * 
     * @param request
     * @param listener
     * @return
     */
    Subscription subscribe(HttpRequest request, SubscriptionListener listener);

    /**
     * 关闭connector，使不能再发送请求
     */
    HttpFuture<Connector> close();

    /**
     * @throws IllegalStateException 如果还未连接
     * @see #close()
     */
    HttpFuture<Connector> getCloseFuture();

}
