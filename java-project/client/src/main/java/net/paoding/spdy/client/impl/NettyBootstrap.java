package net.paoding.spdy.client.impl;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.paoding.spdy.client.Bootstrap;
import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.HttpFuture;
import net.paoding.spdy.common.frame.FrameDecoder3;
import net.paoding.spdy.common.frame.FrameEncoder;
import net.paoding.spdy.common.frame.PingExecution;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class NettyBootstrap implements Bootstrap {

    private ClientSocketChannelFactory channelFactory;

    private ClientBootstrap bootstrap;

    private ThreadLocal<NettyConnector> connectings = new ThreadLocal<NettyConnector>();

    public NettyBootstrap() {
        this(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool()));
    }

    public NettyBootstrap(Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this(new NioClientSocketChannelFactory(bossExecutor, workerExecutor, workerCount));
    }

    public NettyBootstrap(ClientSocketChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public ClientSocketChannelFactory getChannelFactory() {
        return channelFactory;
    }

    @Override
    public HttpFuture<Connector> connect(String host, int port) {
        try {
            SocketAddress remoteAddress = new InetSocketAddress(host, port);
            NettyConnector connector = new NettyConnector(this, host, port);
            connectings.set(connector);

            ChannelFuture future = getBootstrap().connect(remoteAddress);
            connector.setChannelFuture(future);
            HttpFutureImpl<Connector> connectFuture = new HttpFutureImpl<Connector>(connector,
                    future);
            connectFuture.setTarget(connector);
            return connectFuture;
        } finally {
            connectings.remove();
        }
    }

    private synchronized ClientBootstrap getBootstrap() {
        if (bootstrap != null) {
            return bootstrap;
        }
        bootstrap = new ClientBootstrap(channelFactory);
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            FrameEncoder frameEncoder = new FrameEncoder();

            FrameDecoder3 frameDecoder = new FrameDecoder3();

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                NettyConnector connecting = connectings.get();
                if (connecting == null) {
                    throw new IllegalAccessError("not allowed out of doConnect method");
                }

                RequestEncoder requestEncoder = new RequestEncoder(connecting);
                PingExecution pingExecution = new PingExecution(connecting);
                ResponseExecution responseExecution = new ResponseExecution(connecting);

                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("frameDecoder", frameDecoder);
                pipeline.addLast("pingExecution", pingExecution);
                pipeline.addLast("responseExecution", responseExecution);
                pipeline.addLast("frameEncoder", frameEncoder);
                pipeline.addLast("requestEncoder", requestEncoder);
                return pipeline;
            }
        });
        return bootstrap;
    }

}
