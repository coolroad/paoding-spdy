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
 * 代表一个对服务器端的订阅
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface Subscription {

    /**
     * 所属的连接
     * 
     * @return
     */
    Connector getConnector();

    /**
     * 订阅请求的响应，使用者必须通过该响应判断服务端是否接收了该订阅
     * 
     * @return
     */
    Future<HttpResponse> getFuture();

    /**
     * 取消并关闭本订阅
     * 
     * @return
     */
    Future<Subscription> close();

}
