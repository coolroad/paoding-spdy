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
package net.paoding.spdy.client.netty;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.SubscriptionListener;
import net.paoding.spdy.client.SubscriptionStub;
import net.paoding.spdy.common.frame.PingListener;
import net.paoding.spdy.common.frame.frames.Ping;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.ExpireWheel;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
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
    private AtomicInteger nextStreamId = new AtomicInteger(1);

    // 其它

    /** 当前发出、但还未接收到响应的ping */
    ExpireWheel<ResponseFuture<Ping, Ping>> pings = new ExpireWheel<ResponseFuture<Ping, Ping>>(
            256, 1);

    //TODO: ExpireWheel的使用方式要Review
    ExpireWheel<ResponseFuture<?, HttpResponse>> requests = new ExpireWheel<ResponseFuture<?, HttpResponse>>(
            1024 * 16, 1); // step is 1 not 2

    /** 当前的server-push订阅 */
    Map<Integer, SubscriptionStubImpl> subscriptions = new HashMap<Integer, SubscriptionStubImpl>();

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
     * 返回远程服务资源的默认URL前缀 (包括scheme、host、port)
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
        closeFuture = new CloseFuture(this, channel.getCloseFuture());
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public Future<HttpResponse> doRequest(HttpRequest request) {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        SpdyRequest spdyRequest = new SpdyRequest(getNextStreamId(), request, 0);
        ResponseFuture<SpdyRequest, HttpResponse> responseFuture = new ResponseFuture<SpdyRequest, HttpResponse>(
                this, spdyRequest);
        requests.put(spdyRequest.streamId, responseFuture);
        //
        Channels.write(channel, spdyRequest);
        return responseFuture;
    }

    @Override
    public SubscriptionStub subscribe(HttpRequest request, SubscriptionListener listener) {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        SpdyRequest spdyRequest = new SpdyRequest(getNextStreamId(), request, 1);
        SubscriptionStubImpl subscription = new SubscriptionStubImpl(this, spdyRequest, listener);
        ResponseFuture<SubscriptionStub, HttpResponse> responseFuture = new ResponseFuture<SubscriptionStub, HttpResponse>(
                this, subscription);
        subscription.setResponseFuture(responseFuture);
        requests.put(spdyRequest.streamId, responseFuture);
        subscriptions.put(spdyRequest.streamId, subscription);
        Channels.write(channel, spdyRequest);
        return subscription;
    }

    @Override
    public Future<Ping> ping() {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        Ping ping = Ping.getPingToServer();
        ResponseFuture<Ping, Ping> pingFuture = new ResponseFuture<Ping, Ping>(this, ping);
        pings.put(ping.getId(), pingFuture);
        Channels.write(channel, ping);
        return pingFuture;
    }

    /**
     * {@link PingListener}的实现，当接收到服务端的ping响应时，会把该ping通知给future
     */
    @Override
    public void pingArrived(Ping ping) {
        ResponseFuture<Ping, Ping> pingFuture = pings.remove(ping.getId());
        if (pingFuture != null) {
            pingFuture.setResponse(ping);
            pingFuture.setSuccess();
        }
    }

    @Override
    public Future<Connector> close() {
        List<SubscriptionStub> list = new ArrayList<SubscriptionStub>(subscriptions.values());
        for (SubscriptionStub subscription : list) {
            subscription.close();
        }
        channel.close();
        return closeFuture;
    }

    @Override
    public Future<Connector> getCloseFuture() {
        if (channel == null) {
            throw new IllegalStateException();
        }
        return closeFuture;
    }

    /**
     * called by SubscriptionImpl#close()
     * 
     * @param subscription
     * @return
     * @see SubscriptionStubImpl#close()
     */
    ChannelFuture desubscript(SubscriptionStubImpl subscription) {
        subscriptions.remove(subscription.streamId);
        if (isConnected()) {
            // 向服务器发送一个取消订阅的SynStream
            SynStream syn = new SynStream();
            syn.setChannel(this.channel);
            syn.setStreamId(getNextStreamId());
            syn.setAssociatedId(subscription.streamId);
            syn.setFlags(SpdyFrame.FLAG_FIN);
            return Channels.write(channel, syn);
        }
        return Channels.succeededFuture(channel);
    }

    //----------------------------------------------

    protected int getNextStreamId() {
        int id = nextStreamId.getAndAdd(2);
        if (id < 0) {
            synchronized (this) {
                if (nextStreamId.intValue() < 0) {
                    nextStreamId = new AtomicInteger(1);
                }
            }
            id = nextStreamId.getAndAdd(2);
        }
        return id;
    }

    private static final class CloseFuture extends ChannelFutureAdapter<Connector> {

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
    }

}
