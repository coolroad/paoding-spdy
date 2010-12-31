package net.paoding.spdy.test.util;

import java.io.UnsupportedEncodingException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.HttpResponse;

/**
 * @author Li Weibo (weibo.leo@gmail.com) //I believe spring-brother
 * @author qieqie.wang@gmail.com
 */
public class TestUtil {
    
	public static String getContentAsString(HttpResponse response) {
        ChannelBuffer buffer = response.getContent();
        try {
            if (buffer.hasArray()) {
                return new String(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(),
                        buffer.readableBytes(), "UTF-8");
            } else {
                byte[] bs = new byte[buffer.readableBytes()];
                buffer.readBytes(bs);
                return new String(bs, "UTF-8");
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return "error";
        }
    }
	
}
