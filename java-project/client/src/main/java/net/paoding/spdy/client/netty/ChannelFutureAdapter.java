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

import java.util.concurrent.TimeUnit;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.FutureListener;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * {@link Future}的实现: 只关心本端的操作是否成功发送，不关心远程对等方的回应
 * 
 * @author qieqie.wang@gmail.com
 * 
 * @param <T>
 */
class ChannelFutureAdapter<T> implements Future<T> {

    /** 所属的连接 */
    private final Connector connector;

    /** 所属的channelFuture，所有对本future的操作都将通过channelFuture来实现 */
    private final ChannelFuture channelFuture;

    /**
     * 
     * @param connector
     * @param channelFuture
     */
    ChannelFutureAdapter(Connector connector, ChannelFuture channelFuture) {
        this.connector = connector;
        this.channelFuture = channelFuture;
    }

    @Override
    public Connector getConnector() {
        return connector;
    }

    @Override
    public T get() {
        // don't call this method
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDone() {
        return channelFuture.isDone();
    }

    @Override
    public boolean isSuccess() {
        return channelFuture.isSuccess();
    }

    boolean setSuccess() {
        return channelFuture.setSuccess();
    }

    boolean setFailure(Throwable cause) {
        return channelFuture.setFailure(cause);
    }

    boolean cancel() {
        return channelFuture.cancel();
    }

    @Override
    public Throwable getCause() {
        return channelFuture.getCause();
    }

    @Override
    public void addListener(final FutureListener<T> listener) {
        channelFuture.addListener(new FutureAdapter<T>(listener, this));
    }

    @Override
    public void removeListener(FutureListener<T> listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Future<T> await() throws InterruptedException {
        channelFuture.await();
        return this;
    }

    @Override
    public Future<T> awaitUninterruptibly() {
        channelFuture.awaitUninterruptibly();
        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return channelFuture.await(timeout, unit);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return channelFuture.await(timeoutMillis);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        return channelFuture.awaitUninterruptibly(timeout, unit);
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        return channelFuture.awaitUninterruptibly(timeoutMillis);
    }
    
    static class FutureAdapter<T> implements ChannelFutureListener {

        private final FutureListener<T> listener;

        private final Future<T> future;

        public FutureAdapter(FutureListener<T> listener, Future<T> future) {
            this.listener = listener;
            this.future = future;
        }

        @Override
        public void operationComplete(ChannelFuture cf) throws Exception {
            listener.operationComplete(this.future);
        }
    }


}
