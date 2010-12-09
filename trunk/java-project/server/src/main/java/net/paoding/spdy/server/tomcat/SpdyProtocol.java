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
package net.paoding.spdy.server.tomcat;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.paoding.spdy.common.frame.FrameDecoder3;
import net.paoding.spdy.common.frame.FrameEncoder;
import net.paoding.spdy.common.frame.PingExecution;
import net.paoding.spdy.common.frame.PingListener;
import net.paoding.spdy.common.supports.ExecutorUtil;
import net.paoding.spdy.server.tomcat.impl.RequestDecoder;
import net.paoding.spdy.server.tomcat.impl.RequestExecution;
import net.paoding.spdy.server.tomcat.impl.subscriptionimpl.SubscriptionEncoder;
import net.paoding.spdy.server.tomcat.impl.subscriptionimpl.SubscriptionFactoryImpl;
import net.paoding.spdy.server.tomcat.impl.supports.SpdyOutputBuffer;

import org.apache.coyote.Adapter;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.logging.LoggingHandler;

/**
 * SpdyProtocol是一个可配在tomcat中的spdy协议处理器。
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SpdyProtocol extends SimpleChannelHandler implements ProtocolHandler {

    protected static Log logger = LogFactory.getLog(SpdyProtocol.class);

    private Adapter adapter;

    private ExecutorService executor;

    // 用于标识executor是自己创建的，还是外部设置进来的
    private boolean sharedExecutor;

    private ChannelGroup allChannels;

    private ServerBootstrap bootstrap;

    private InetSocketAddress bind;

    private int port = -1;

    private String addr;

    private Channel serverChannel;

    private PingListener pingListener;

    private Map<String, Object> attributes = new HashMap<String, Object>();

    private int ouputBufferSize = 1024;

    /**
     * 
     */
    public SpdyProtocol() {
    }

    /**
     * (可选设置项)设置侦听的地址，默认为null表示不绑特定地址
     * 
     * @param addr
     */
    public void setAddr(String addr) {
        this.addr = addr;
    }

    /**
     * (必须设置项)设置侦听的端口
     * 
     * @param port
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * (可选设置项)设置ping侦听器
     * 
     * @param pingListener
     */
    public void setPingListener(PingListener pingListener) {
        this.pingListener = pingListener;
    }

    /**
     * (可选设置项)设置业务执行器(非IO操作的执行器)
     * 
     * @param executor
     */
    public void setExecutor(ExecutorService executor) {
        if (this.executor != null) {
            throw new IllegalStateException("executor can't change once set.");
        }
        this.sharedExecutor = true;
        this.executor = executor;
    }

    /**
     * 返回设置的业务执行器，如果没有设置返回一个默认值
     * 
     * @return
     */
    protected synchronized ExecutorService getExecutor() {
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
            this.sharedExecutor = false;
        }
        return executor;
    }

    /**
     * (可选设置项)设置data缓冲大小，默认是1024bytes
     * 
     * @see SpdyOutputBuffer
     * @param ouputBufferSize
     */
    public void setOuputBufferSize(int ouputBufferSize) {
        this.ouputBufferSize = ouputBufferSize;
    }

    @Override
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public Object getAttribute(String name) {
        return attributes.get(name);
    }

    @Override
    public Iterator<String> getAttributeNames() {
        return attributes.keySet().iterator();
    }

    @Override
    public Adapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(Adapter adapter) {
        if (this.adapter != null) {
            throw new IllegalStateException();
        }
        this.adapter = adapter;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) {
        allChannels.add(e.getChannel());
        ctx.sendUpstream(e);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        allChannels.remove(e.getChannel());
        ctx.sendUpstream(e);
    }

    /**
     * 初始化
     */
    public void init() {
        allChannels = new DefaultChannelGroup("spdy-channels");
        // Configure the server.
        final ExecutorService bossExecutor = Executors.newCachedThreadPool();
        final ExecutorService workerExecutor = Executors.newCachedThreadPool();
        bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(bossExecutor,
                workerExecutor) {

            @Override
            public void releaseExternalResources() {
                ExecutorUtil.shutdown(bossExecutor, workerExecutor);
            }
        });
        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {

            LoggingHandler loggingHandler = new LoggingHandler();

            SimpleChannelHandler subscriptionEncoder = new SubscriptionEncoder();

            SimpleChannelHandler pingExecution = new PingExecution(pingListener);

            SimpleChannelHandler requestExecution = new RequestExecution(getExecutor(), adapter,
                    ouputBufferSize);

            @Override
            public ChannelPipeline getPipeline() throws Exception {
                ChannelPipeline pipeline = Channels.pipeline();
                pipeline.addLast("logger", loggingHandler);
                pipeline.addLast("allChannels", SpdyProtocol.this);
                pipeline.addLast("frameDecoder", new FrameDecoder3());
                pipeline.addLast("pingExecution", pingExecution);
                pipeline.addLast("coyoteRequestDecoder", new RequestDecoder(
                        new SubscriptionFactoryImpl()));
                pipeline.addLast("coyoteRequestExecution", requestExecution);
                pipeline.addLast("frameEncoder", new FrameEncoder());
                pipeline.addLast("subscriptionEncoder", subscriptionEncoder);
                return pipeline;
            }
        });

    }

    /**
     * 启动服务
     */
    public void start() {
        if (addr != null && addr.length() > 0 && !addr.equals("*")) {
            this.bind = new InetSocketAddress(addr, port);
        } else {
            this.bind = new InetSocketAddress(port);
        }
        serverChannel = bootstrap.bind(bind);
        allChannels.add(serverChannel);
        logger.info("SPDY listening on on " + bind);
    }

    /**
     * 关闭服务
     */
    // TODO: 如何gracefull的关闭: 
    // 新来stream不接收(应该回送refuse-reply!不能不理会，特别是来自proxy的stream)
    // 老stream的dataframe继续接收，等所有reponse都结束再真正shutdown!
    
    // 做完上面的这个TODO后，再看看有无必要"重载"Netty的ExecutorUtil.shutdown，如无必要则应该去掉
    public void destroy() {
        if (bootstrap != null) {
            // 先解除绑定
            serverChannel.unbind().awaitUninterruptibly();
            ChannelGroupFuture future = allChannels.close();
            future.awaitUninterruptibly();
            bootstrap.getFactory().releaseExternalResources();
            bootstrap = null;
            allChannels = null;
            if (!sharedExecutor) {
                ExecutorUtil.shutdown(executor);
            }
            executor = null;
            adapter = null;
            pingListener = null;
            attributes = null;
        }
    }

    @Override
    public void pause() throws Exception {
    }

    @Override
    public void resume() throws Exception {
    }

}
