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
package net.paoding.spdy.client;

import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 订阅侦听器
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface SubscriptionListener {

    /**
     * 当服务端向客户端推送消息时候此方法被调用
     * 
     * @param subscription 所属的订阅
     * @param response 推送的消息
     */
    void responseReceived(Subscription subscription, HttpResponse response);

}
