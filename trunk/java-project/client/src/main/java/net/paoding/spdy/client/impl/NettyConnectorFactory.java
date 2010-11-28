package net.paoding.spdy.client.impl;

import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.ConnectorFactory;
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
public class NettyConnectorFactory implements ConnectorFactory {

    private ClientSocketChannelFactory channelFactory;

    private ClientBootstrap bootstrap;

    private ThreadLocal<NettyConnector> connectings = new ThreadLocal<NettyConnector>();

    public NettyConnectorFactory() {
        this(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool()));
    }

    public NettyConnectorFactory(Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this(new NioClientSocketChannelFactory(bossExecutor, workerExecutor, workerCount));
    }

    public NettyConnectorFactory(ClientSocketChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    public ClientSocketChannelFactory getChannelFactory() {
        return channelFactory;
    }

    @Override
    public Connector get(String host, int port) {
        return new NettyConnector(this, host, port);
    }

    ChannelFuture connect(NettyConnector connector, SocketAddress remoteAddress) {
        try {
            if (connector.factory != this) {
                throw new IllegalArgumentException();
            }
            connectings.set(connector);
            return getBootstrap().connect(remoteAddress);
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
