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

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.paoding.spdy.client.Bootstrap;
import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.common.frame.ChannelConfig;
import net.paoding.spdy.common.frame.FrameDecoder;
import net.paoding.spdy.common.frame.FrameEncoder;
import net.paoding.spdy.common.frame.PingExecution;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.logging.LoggingHandler;
import org.jboss.netty.util.internal.IoWorkerRunnable;

/**
 * 使用Netty实现的Bootstrap
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class NettyBootstrap implements Bootstrap {

    /** 该引导器使用的channel工厂 */
    private final ClientSocketChannelFactory channelFactory;

    /**
     * 构造一个Worker线程为cpu核2倍的NettyBootstrap
     */
    public NettyBootstrap() {
        this(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
                Executors.newCachedThreadPool(), Runtime.getRuntime().availableProcessors() * 2));
    }

    /**
     * 通过外部提供的bossExecutor、workerExecutor以及woker线程数构造NettyBootstrap
     * 
     * @param bossExecutor 用于选择channel事件
     * @param workerExecutor 用于执行各个channel的I/O读写操作
     * @param workerCount worker线程数
     */
    public NettyBootstrap(Executor bossExecutor, Executor workerExecutor, int workerCount) {
        this(new NioClientSocketChannelFactory(bossExecutor, workerExecutor, workerCount));
    }

    /**
     * 通过外部提供的channelFactory构造引导器
     * 
     * @param channelFactory
     */
    public NettyBootstrap(ClientSocketChannelFactory channelFactory) {
        this.channelFactory = channelFactory;
    }

    /**
     * 返回该引导器的channelFactory
     * 
     * @return
     */
    public ClientSocketChannelFactory getChannelFactory() {
        return channelFactory;
    }

    @Override
    public Future<Connector> connect(String host, int port) {
        SocketAddress remoteAddress = new InetSocketAddress(host, port);
        NettyConnector connector = new NettyConnector(this, host, port);
        ChannelFuture future = getBootstrap(connector).connect(remoteAddress);
        connector.setChannelFuture(future);
        return new ChannelFutureAdapter<Connector>(connector, future);
    }

    @Override
    public void destroy() {
        if (IoWorkerRunnable.IN_IO_THREAD.get()) {
            throw new IllegalStateException("don't call destroy() in I/O thread.");
        }
        channelFactory.releaseExternalResources();
    }

    /**
     * 获取给定连接所使用的 {@link ClientBootstrap}
     * 
     * @param connecting
     * @return
     */
    protected ClientBootstrap getBootstrap(final NettyConnector connecting) {
        ClientBootstrap bootstrap = new ClientBootstrap(getChannelFactory());
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelConfig config = new ChannelConfig();

                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("logger", new LoggingHandler());
                pipeline.addLast("frameDecoder", new FrameDecoder(config));
                pipeline.addLast("pingExecution", new PingExecution(connecting));
                pipeline.addLast("responseExecution", new ResponseExecution(connecting));
                pipeline.addLast("frameEncoder", new FrameEncoder(config));
                pipeline.addLast("requestEncoder", new RequestEncoder(connecting, false));
                return pipeline;
            }
        });
        return bootstrap;
    }

}
