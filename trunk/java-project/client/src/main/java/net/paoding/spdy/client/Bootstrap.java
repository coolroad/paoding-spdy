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

/**
 * Bootstrap用于创建到远程服务的连接
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface Bootstrap {

    /**
     * 创建一个新的远程服务连接
     * <p>
     * 
     * 
     * @param host
     * @param port
     * @return
     */
    public Future<Connector> connect(String host, int port);

    /**
     * 销毁该引导器的资源(比如各个连接共享的线程池)，建议在所有连接都关闭后并sleep/await些许时间后再调用此方法
     * <p>
     * 不销毁仍在连接状态的连接，不保证仍在服务的连接可用(很可能是不可用)
     */
    public void destroy();
}
