package net.paoding.spdy.client.impl;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.ConnectorFuture;
import net.paoding.spdy.client.HttpFuture;
import net.paoding.spdy.client.Subscription;
import net.paoding.spdy.client.SubscriptionListener;
import net.paoding.spdy.common.frame.FrameDecoder1;
import net.paoding.spdy.common.frame.FrameEncoder;
import net.paoding.spdy.common.frame.PingExecution;
import net.paoding.spdy.common.frame.PingListener;
import net.paoding.spdy.common.frame.frames.SpdyFrame;
import net.paoding.spdy.common.frame.frames.Ping;
import net.paoding.spdy.common.frame.frames.SynStream;
import net.paoding.spdy.common.supports.ExpireWheel;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class SpdyConnector implements Connector, PingListener {

    //--------------------------------------

    private Channel channel;

    private ConnectorFutureImpl connectionFuture;

    private String host;

    private int port;

    private String uriPrefix;

    private int nextStreamId = 1;

    ExpireWheel<HttpResponseFuture> httpRequests = new ExpireWheel<HttpResponseFuture>(1024 * 16, 1);

    Map<Integer, SubscriptionImpl> subscriptions = new HashMap<Integer, SubscriptionImpl>();

    ExpireWheel<HttpFutureImpl<Ping>> pingRequests = new ExpireWheel<HttpFutureImpl<Ping>>(256, 1);

    protected final ExecutorService executor;

    private CloseFuture closeFuture;

    public SpdyConnector(ExecutorService futureExecutor, String hostport) {
        this.executor = futureExecutor;
        int index = hostport.indexOf(':');
        if (index == 0) {
            init("localhost", Integer.parseInt(hostport.substring(1)));
        } else if (index < 0) {
            init(hostport, 80);
        } else {
            init(hostport.substring(0, index), Integer.parseInt(hostport.substring(index + 1)));
        }
    }

    public SpdyConnector(ExecutorService executor, String host, int port) {
        this.executor = executor;
        init(host, port);
    }

    private void init(String host, int port) {
        this.host = host;
        this.port = port;
        String uriPrefix = "http://" + host;
        if (port != 80) {
            uriPrefix = uriPrefix + ":" + port;
        }
        this.uriPrefix = uriPrefix;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUriPrefix() {
        return uriPrefix;
    }

    @Override
    public SocketAddress getLocalAddress() {
        return channel.getLocalAddress();
    }

    @Override
    public SocketAddress getRemoteAddress() {
        return channel.getRemoteAddress();
    }

    @Override
    public ConnectorFuture connect() {
        if (channel != null) {
            throw new IllegalStateException();
        }
        ChannelFuture future = connect0();
        channel = future.getChannel();
        connectionFuture = new ConnectorFutureImpl(this, future);
        connectionFuture.setTarget(this);
        closeFuture = new CloseFuture(this, future);
        closeFuture.setTarget(this);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {

            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                closeFuture.setClosed();
            }
        });
        return connectionFuture;
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
    public HttpFuture<Connector> getConnectFuture() {
        return connectionFuture;
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public HttpFuture<Ping> ping() {
        Ping ping = Ping.toServer();
        ChannelFuture future = Channels.write(channel, ping);
        HttpFutureImpl<Ping> pingFuture = new HttpFutureImpl<Ping>(this, future);
        pingRequests.put(ping.getId(), pingFuture);
        return pingFuture;
    }

    @Override
    public void pingArrived(Ping ping) {
        //System.out.println("pingArrived " + ping.getId());
        if (ping.getId() % 2 == 0) {
            HttpFutureImpl<Ping> pingFuture = pingRequests.remove(ping.getId());
            if (pingFuture != null) {
                pingFuture.setTarget(ping);
                pingFuture.setSuccess();
            }
        }
    }

    @Override
    public HttpFuture<HttpResponse> doRequest(HttpRequest request) {
        if (!isConnected()) {
            throw new IllegalStateException("not connected");
        }
        SpdyRequest spdyRequest = new SpdyRequest(getNextStreamId(), request, 0);
        HttpResponseFuture httpFutrue = new HttpResponseFuture(this, spdyRequest, false);
        httpRequests.put(httpFutrue.getStreamId(), httpFutrue);
        //
        Channels.write(channel, spdyRequest);
        return httpFutrue;
    }

    //TODO: 请求会把订阅干掉，要处理！
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
        httpRequests.put(spdyRequest.streamId, httpFutrue);
        Channels.write(channel, spdyRequest);
        return subscription;
    }

    void desubscript(SubscriptionImpl pushingImpl) {
        subscriptions.remove(pushingImpl.streamId);
        SynStream syn = new SynStream();
        syn.setChannel(this.channel);
        syn.setStreamId(getNextStreamId());
        syn.setAssociatedId(pushingImpl.streamId);
        syn.setFlags(SpdyFrame.FLAG_FIN);
        Channels.write(channel, syn);
    }

    protected Executor getBossExecutor() {
        return Executors.newCachedThreadPool();
    }

    protected Executor getWorkerExecutor() {
        return Executors.newCachedThreadPool();
    }

    protected int getWorkerCount() {
        return Runtime.getRuntime().availableProcessors() * 2;
    }

    //----------------------------------------------

    protected ChannelFuture connect0() {
        ClientBootstrap bootstrap = new ClientBootstrap(//
                new NioClientSocketChannelFactory(//
                        getBossExecutor(), getWorkerExecutor(), getWorkerCount()));
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            RequestEncoder requestEncoder = new RequestEncoder(SpdyConnector.this);

            PingExecution pingExecution = new PingExecution(executor, SpdyConnector.this);

            ResponseExecution responseExecution = new ResponseExecution(executor,
                    SpdyConnector.this);

            FrameEncoder frameEncoder = new FrameEncoder();

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("frameDecoder", new FrameDecoder1());
                pipeline.addLast("pingExecution", pingExecution);
                pipeline.addLast("responseExecution", responseExecution);
                pipeline.addLast("frameEncoder", frameEncoder);
                pipeline.addLast("requestEncoder", requestEncoder);
                return pipeline;
            }
        });
        SocketAddress remoteAddress = new InetSocketAddress(host, port);
        ChannelFuture future = bootstrap.connect(remoteAddress);
        return future;
    }

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
