package net.paoding.spdy.test.util;

import java.io.UnsupportedEncodingException;

import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * @author Li Weibo (weibo.leo@gmail.com) //I believe spring-brother
 */
public class TestUtil {

	public static String getContentAsString(HttpResponse response) {
        try {
            return new String(response.getContent().array(), response.getContent().arrayOffset()
                    + response.getContent().readerIndex(), response.getContent().readableBytes(),
                    "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "error";
        }
    }
	
}
