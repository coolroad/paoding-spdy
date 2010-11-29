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

import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.FutureListener;

import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 * @param <T>
 */
public class FutureAdapter<T> implements ChannelFutureListener {

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
