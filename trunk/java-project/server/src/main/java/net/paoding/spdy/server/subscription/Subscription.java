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
package net.paoding.spdy.server.subscription;

import java.util.Map;

import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * 代表一个客户端对服务器的订阅,当服务器接收到一个订阅并确认后必须调用 {@link #accept()}进行确认方才有效
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface Subscription {

    /**
     * 当接收到一个订阅请求时，在该请求中该订阅的属性名
     */
    String REQUEST_ATTR = Subscription.class.getName();

    /**
     * 
     * @return
     */
    SubscriptionFactory getFactory();

    /**
     * 
     * @return
     */
    int getAssociatedId();

    /**
     * 同意该订阅
     * <p>
     * 当收到一个订阅请求时候，必须在当前请求未完成前调用accept进行确认，否则该订阅将被视为无效。 <br>
     * 如果已经被accept，不会产生其他影响
     * 
     * @throws IllegalStateException 如果在非当前订阅请求下调用时
     */
    void accept();

    /**
     * 已经被接受了?
     * <p>
     * 只有 {@link #accept()}方法在正确的时间里被调用过才算被接受
     * 
     * @return
     */
    boolean isAccepted();

    /**
     * 向订阅的客户端推送信息
     * 
     * @param message
     * @throws IllegalStateException 如果该订阅已经被断开(被关闭)
     */
    SubscriptionFuture push(HttpResponse message);

    /**
     * 增加一个侦听器，当订阅发生变化时，将收到通知
     * 
     * @param listener
     */
    void addListener(SubscriptionStateListener listener);

    /**
     * 设置一个附属在该订阅的属性
     * 
     * @param name
     * @param value
     */
    void setAttribute(String name, Object value);

    /**
     * 取附属在该订阅上的某个属性
     * 
     * @param name
     * @return
     */
    Object getAttribute(String name);

    /**
     * 获取附属在该订阅上的所有属性
     * 
     * @return
     */
    Map<String, Object> getAttributes();

    /**
     * 移除附属在该订阅上的某个属性
     * 
     * @param name
     */
    void removeAttribute(String name);

    /**
     * 已经断开(关闭)了
     * <p>
     * 当 {@link #close()}被调用过或由客户端已经取消订阅时，此方法返回true
     * 
     * @return
     */
    boolean isClosed();

    /**
     * 关闭订阅使得无效
     * <p>
     * 
     * 会自动向客户端发送一个关闭事件
     */
    void close();

}
