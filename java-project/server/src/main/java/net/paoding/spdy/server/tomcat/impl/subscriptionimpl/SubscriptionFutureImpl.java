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
package net.paoding.spdy.server.tomcat.impl.subscriptionimpl;

import java.util.concurrent.TimeUnit;

import net.paoding.spdy.server.subscription.SubscriptionFutureListener;
import net.paoding.spdy.server.subscription.Subscription;
import net.paoding.spdy.server.subscription.SubscriptionFuture;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class SubscriptionFutureImpl implements SubscriptionFuture {

    private final SubscriptionImpl subscription;

    private final ChannelFuture channelFuture;

    public SubscriptionFutureImpl(SubscriptionImpl subscription, ChannelFuture write) {
        this.subscription = subscription;
        this.channelFuture = write;
    }

    @Override
    public Subscription getSubscription() {
        return subscription;
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
    public void addListener(final SubscriptionFutureListener listener) {
        channelFuture.addListener(new FutureAdapter(listener, this));
    }

    @Override
    public void removeListener(SubscriptionFutureListener listener) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscriptionFuture await() throws InterruptedException {
        channelFuture.await();
        return this;
    }

    @Override
    public SubscriptionFuture awaitUninterruptibly() {
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

    static class FutureAdapter implements ChannelFutureListener {

        private final SubscriptionFutureListener listener;

        private final SubscriptionFuture future;

        public FutureAdapter(SubscriptionFutureListener listener, SubscriptionFuture future) {
            this.listener = listener;
            this.future = future;
        }

        @Override
        public void operationComplete(ChannelFuture cf) throws Exception {
            listener.operationComplete(this.future);
        }
    }

}
