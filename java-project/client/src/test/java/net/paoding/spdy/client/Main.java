package net.paoding.spdy.client;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import net.paoding.spdy.client.netty.NettyBootstrap;
import net.paoding.spdy.common.http.DefaultHttpRequest;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        final Bootstrap bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect("localhost", 8081);
        final Connector connector = connecting.awaitUninterruptibly().getConnector();
        //        //
        HttpRequest request = new DefaultHttpRequest("post", "/the8/test");
        HttpParameters parameters = new HttpParameters();
        setParameters(args, parameters).copyTo(request);

        //
        Future<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.get();

        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        //
        System.out.println();
        if (false) {
            close(bootstrap, connector);
            return;
        }

        System.out.println(".... enter subscribe");
        //            Thread.sleep(10000);
        System.out.println("entered");

        DefaultHttpRequest subRequest = new DefaultHttpRequest("get", "/the8/register");
        SubscriptionStub sub = connector.subscribe(subRequest, new SubscriptionListener() {

            @Override
            public void responseReceived(SubscriptionStub subscription, HttpResponse response) {

                String msg = getContentAsString(response);
                System.out.println("subscription responseReceived: content=" + msg);
                if ("close".equals(msg)) {
                    System.out.println("closing by server-push");
                    // 不能在IO线程中close,否则关闭不掉
                    new Thread(new Runnable() {
                        
                        @Override
                        public void run() {
                            try {
                                Thread.currentThread().sleep(1000);
                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                            close(bootstrap, connector);
                        }
                    }).start();
                    
                }
            }
        });
        HttpResponse response2 = sub.getResponseFuture().awaitUninterruptibly().get();

        System.out.println(getContentAsString(response2));

        Thread.sleep(100000);

        //
        System.out.println("closed");
        if (true) {
            close(bootstrap, connector);
            return;
        }

    }
    

    private static void close(Bootstrap bootstrap, Connector connector) {
        connector.close().awaitUninterruptibly();
        System.out.println("connector closed");
        bootstrap.destroy();
        System.out.println("bootstrap destroyed");
    }

    public static void main1(String[] args) throws IOException {
        Bootstrap bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect("localhost", 8081);
        Connector connector = connecting.awaitUninterruptibly().getConnector();
        //
        HttpRequest request = new DefaultHttpRequest("get", "/the8/test");
        HttpParameters parameters = new HttpParameters();
        setParameters(args, parameters).copyTo(request);
        //
        Future<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.get();
        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        connector.close().awaitUninterruptibly();
        System.out.println("closed");

    }

    private static String getContentAsString(HttpResponse response) {
        try {
            return new String(response.getContent().array(), response.getContent().readerIndex(),
                    response.getContent().readableBytes(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "error";
        }
    }

    private static HttpParameters setParameters(String[] args, HttpParameters parameters) {
        parameters.setParameter("p", "hello world");
        parameters.setParameter("view", "not-view");
        return parameters;
    }

    public static void main0(String[] args) throws IOException {
        Bootstrap factory = new NettyBootstrap();
        Future<Connector> connecting = factory.connect("localhost", 8081);
        Connector connection = connecting.awaitUninterruptibly().get();
        //
        HttpRequest request = new DefaultHttpRequest("GET", "/blog/123456");
        HttpParameters parameters = new HttpParameters();
        parameters.setParameter("view", "preview");
        parameters.copyTo(request);
        Future<HttpResponse> responseFuture = connection.doRequest(request);
        HttpResponse response = responseFuture.awaitUninterruptibly().get();
        System.out.println(response.getStatus());
    }
}
