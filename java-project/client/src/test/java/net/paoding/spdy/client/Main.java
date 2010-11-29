package net.paoding.spdy.client;

import java.io.IOException;

import net.paoding.spdy.client.netty.NettyBootstrap;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        Bootstrap bootstrap = new NettyBootstrap();
        Future<Connector> connecting = bootstrap.connect("localhost", 8081);
        Connector connector = connecting.awaitUninterruptibly().getConnector();
        //        //
        HttpRequest request = new DefaultHttpRequest("post", "/the8/test");
        HttpParameters parameters = new HttpParameters();
        setParameters(args, parameters).copyTo(request);

        //
        Future<HttpResponse> responseFuture = connector.doRequest(request);
        responseFuture.awaitUninterruptibly();
        HttpResponse response = responseFuture.getTarget();

        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        //
        System.out.println();
        if (true) {
            connector.close().awaitUninterruptibly();
            System.out.println("closed");
            return;
        }

        System.out.println(".... enter subscribe");
        //            Thread.sleep(10000);
        System.out.println("entered");

        DefaultHttpRequest request2 = new DefaultHttpRequest("get", "/the8/register");
        Subscription sub = connector.subscribe(request2, new SubscriptionListener() {

            @Override
            public void responseReceived(Subscription subscription, HttpResponse response) {

                System.out.println("subscription.content=" + getContentAsString(response));
            }
        });
        HttpResponse response2 = sub.getFuture().awaitUninterruptibly().getTarget();

        System.out.println(getContentAsString(response2));

        Thread.sleep(100000);

        //
        connector.close().awaitUninterruptibly();
        System.out.println("closed");

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
        HttpResponse response = responseFuture.getTarget();
        //
        System.out.println("status=" + response.getStatus());
        System.out.println("content=" + getContentAsString(response));
        connector.close().awaitUninterruptibly();
        System.out.println("closed");

    }

    private static String getContentAsString(HttpResponse response) {
        return new String(response.getContent().array(), response.getContent().readerIndex(),
                response.getContent().readableBytes());
    }

    private static HttpParameters setParameters(String[] args, HttpParameters parameters) {
        parameters.setParameter("p", "hello world");
        parameters.setParameter("view", "not-view");
        return parameters;
    }

    public static void main0(String[] args) throws IOException {
        Bootstrap factory = new NettyBootstrap();
        Future<Connector> connecting = factory.connect("localhost", 8081);
        Connector connection = connecting.awaitUninterruptibly().getTarget();
        //
        HttpRequest request = new DefaultHttpRequest("GET", "/blog/123456");
        HttpParameters parameters = new HttpParameters();
        parameters.setParameter("view", "preview");
        parameters.copyTo(request);
        Future<HttpResponse> responseFuture = connection.doRequest(request);
        HttpResponse response = responseFuture.awaitUninterruptibly().getTarget();
        System.out.println(response.getStatus());
    }
}
