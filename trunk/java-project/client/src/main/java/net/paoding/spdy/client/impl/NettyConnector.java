package net.paoding.spdy.client.impl;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.HttpFuture;
import net.paoding.spdy.client.Subscription;
import net.paoding.spdy.client.SubscriptionListener;
import net.paoding.spdy.common.frame.PingListener;
import net.paoding.spdy.common.frame.frames.Ping;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.ExpireWheel;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
// TODO: 如何保证所有的frame都发送到server后才close?
// TODO: 在close之前要先取消订阅？
public class NettyConnector implements Connector, PingListener {

    // 配置信息

    /** 配置的远程服务地址 */
    private String host;

    /** 配置的远程服务端口 */
    private int port;

    /** 该远程服务的url的前缀，包含 schema、host、port */
    private String urlPrefix;

    // 连接及其相关对象

    final NettyBootstrap factory;

    /** 该连接所使用的channel */
    private Channel channel;

    /** {@link #close()} 操作的future对象 */
    private CloseFuture closeFuture;

    // 状态信息

    /** 该连接下一个使用的spdy stream编号 */
    private int nextStreamId = 1;

    // 其它

    /** 当前发出、但还未接收到响应的ping */
    ExpireWheel<HttpFutureImpl<Ping>> pings = new ExpireWheel<HttpFutureImpl<Ping>>(256, 1);

    //TODO: ExpireWheel的使用方式要Review
    ExpireWheel<HttpResponseFuture> requests = new ExpireWheel<HttpResponseFuture>(1024 * 16, 1); // step is 1 not 2

    /** 当前的server-push订阅 */
    Map<Integer, SubscriptionImpl> subscriptions = new HashMap<Integer, SubscriptionImpl>();

    /**
     * 创建一个尚未连接的connector
     * 
     * @param hostport "host:ip" the host&port of remote service
     * @see #connect()
     */
    public NettyConnector(NettyBootstrap factory, String hostport) {
        this.factory = factory;
        int index = hostport.indexOf(':');
        if (index == 0) {
            setHostPort("localhost", Integer.parseInt(hostport.substring(1)));
        } else if (index < 0) {
            setHostPort(hostport, 80);
        } else {
            setHostPort(hostport.substring(0, index),
                    Integer.parseInt(hostport.substring(index + 1)));
        }
    }

    /**
     * 
     * 创建一个尚未连接的connector
     * 
     * @param host the remote host address
     * @param port the port of remote service
     * @see #connect()
     */
    public NettyConnector(NettyBootstrap factory, String host, int port) {
        this.factory = factory;
        setHostPort(host, port);
    }

    /**
     * 
     * @param host
     * @param port
     */
    private void setHostPort(String host, int port) {
        this.host = host;
        this.port = port;
        String uriPrefix = "http://" + host;
        if (port != 80) {
            uriPrefix = uriPrefix + ":" + port;
        }
        this.urlPrefix = uriPrefix;
    }

    /**
     * 返回设置的远程服务地址
     * 
     * @return
     */
    public String getHost() {
        return host;
    }

    /**
     * 返回设置的远程服务端口
     * 
     * @return
     */
    public int getPort() {
        return port;
    }

    /**
     * 返回远程服务资源的默认URL前缀 (包括schema、host、port)
     * 
     * @return
     */
    public String getUrlPrefix() {
        return urlPrefix;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel == null ? null : channel.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel == null ? null : channel.getRemoteAddress();
    }

    void setChannelFuture(ChannelFuture future) {
        if (channel != null) {
            throw new IllegalStateException();
        }
        channel = future.getChannel();
        closeFuture = new CloseFuture(this, future);
        closeFuture.setTarget(this);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                closeFuture.setClosed();
            }
        });
    }

    @Override
    public HttpFuture<Connector> close() {
        channel.close();
        return closeFuture;
    }

    @Override
    public HttpFuture<Connector> getCloseFuture() {
        if (channel == null) {
            throw new IllegalStateException();
        }
        return closeFuture;
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public HttpFuture<HttpResponse> doRequest(HttpRequest request) {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        SpdyRequest spdyRequest = new SpdyRequest(getNextStreamId(), request, 0);
        HttpResponseFuture httpFutrue = new HttpResponseFuture(this, spdyRequest, false);
        requests.put(httpFutrue.getStreamId(), httpFutrue);
        //
        Channels.write(channel, spdyRequest);
        return httpFutrue;
    }

    @Override
    public Subscription subscribe(HttpRequest request, SubscriptionListener listener) {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        SpdyRequest spdyRequest = new SpdyRequest(getNextStreamId(), request, 1);
        HttpResponseFuture httpFutrue = new HttpResponseFuture(this, spdyRequest, false);
        SubscriptionImpl subscription = new SubscriptionImpl(spdyRequest.streamId, this,
                httpFutrue, listener);
        subscriptions.put(spdyRequest.streamId, subscription);
        // TODO: 为何要put到futures?
        requests.put(spdyRequest.streamId, httpFutrue);
        Channels.write(channel, spdyRequest);
        return subscription;
    }

    @Override
    public HttpFuture<Ping> ping() {
        // TODO: write如果太快，会导致pingArrived方法找不到pingFuture
        Ping ping = Ping.toServer();
        ChannelFuture future = Channels.write(channel, ping);
        HttpFutureImpl<Ping> pingFuture = new HttpFutureImpl<Ping>(this, future);
        pings.put(ping.getId(), pingFuture);
        return pingFuture;
    }

    /**
     * {@link PingListener}的实现，当接收到服务端的ping响应时，会把该ping通知给future
     */
    @Override
    public void pingArrived(Ping ping) {
        HttpFutureImpl<Ping> pingFuture = pings.remove(ping.getId());
        if (pingFuture != null) {
            pingFuture.setTarget(ping);
            pingFuture.setSuccess();
        }
    }

    /**
     * called by SubscriptionImpl#close()
     * 
     * @param pushingImpl
     * @return
     * @see SubscriptionImpl#close()
     */
    ChannelFuture desubscript(SubscriptionImpl pushingImpl) {
        subscriptions.remove(pushingImpl.streamId);
        // 向服务器发送一个取消订阅的SynStream
        SynStream syn = new SynStream();
        syn.setChannel(this.channel);
        syn.setStreamId(getNextStreamId());
        syn.setAssociatedId(pushingImpl.streamId);
        syn.setFlags(SpdyFrame.FLAG_FIN);
        return Channels.write(channel, syn);
    }

    //----------------------------------------------

    //-------------------

    protected synchronized int getNextStreamId() {
        nextStreamId += 2;
        if (nextStreamId < 0) {
            nextStreamId = 1;
        }
        return nextStreamId;
    }

    private final class CloseFuture extends HttpFutureImpl<Connector> {

        CloseFuture(Connector connection, ChannelFuture channelFuture) {
            super(connection, channelFuture);
        }

        @Override
        public boolean setSuccess() {
            // User is not supposed to call this method - ignore silently.
            return false;
        }

        @Override
        public boolean setFailure(Throwable cause) {
            // User is not supposed to call this method - ignore silently.
            return false;
        }

        boolean setClosed() {
            return super.setSuccess();
        }
    }

}
