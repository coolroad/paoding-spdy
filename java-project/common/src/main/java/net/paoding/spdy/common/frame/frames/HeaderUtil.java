package net.paoding.spdy.common.frame.frames;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferFactory;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.buffer.DynamicChannelBuffer;

/**
 * Header编解码
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public class HeaderUtil {

    private static Charset utf8 = Charset.forName("utf8");

    private final static Log logger = LogFactory.getLog(HeaderUtil.class);

    // SPDY Protocol - Draft 2 : Name/Value header block format
    private final static byte[] dictionary = (""//
            + "optionsgetheadpostputdeletetraceacceptaccept-charsetaccept-encodingaccept-"
            + "languageauthorizationexpectfromhostif-modified-sinceif-matchif-none-matchi"
            + "f-rangeif-unmodifiedsincemax-forwardsproxy-authorizationrangerefererteuser"
            + "-agent10010120020120220320420520630030130230330430530630740040140240340440"
            + "5406407408409410411412413414415416417500501502503504505accept-rangesageeta"
            + "glocationproxy-authenticatepublicretry-afterservervarywarningwww-authentic"
            + "ateallowcontent-basecontent-encodingcache-controlconnectiondatetrailertran"
            + "sfer-encodingupgradeviawarningcontent-languagecontent-lengthcontent-locati"
            + "oncontent-md5content-rangecontent-typeetagexpireslast-modifiedset-cookieMo"
            + "ndayTuesdayWednesdayThursdayFridaySaturdaySundayJanFebMarAprMayJunJulAugSe"
            + "pOctNovDecchunkedtext/htmlimage/pngimage/jpgimage/gifapplication/xmlapplic"
            + "ation/xhtmltext/plainpublicmax-agecharset=iso-8859-1utf-8gzipdeflateHTTP/1"
            + ".1statusversionurl"//
    ).getBytes();

    private static final byte[] bytes32 = new byte[32];

    /**
     * 解码
     * 
     * @param buffer
     * @return
     * @throws DataFormatException
     */
    public static Map<String, String> decode(ChannelBuffer buffer, int length,
            boolean usingDecompressing) throws DataFormatException {
        int size = buffer.readUnsignedShort();
        boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("decoding: headers.size=" + size);
        }
        Inflater inflater = null;
        byte[] array;
        int arrayOffset;
        int arrayLength = length - 2;
        if (buffer.hasArray()) {
            array = buffer.array();
            arrayOffset = buffer.arrayOffset() + buffer.readerIndex();
            buffer.skipBytes(arrayLength);
        } else {
            array = new byte[arrayLength];
            buffer.readBytes(array, 0, arrayLength);
            arrayOffset = 0;
        }
        if (usingDecompressing) {
            if (debugEnabled) {
                logger.debug("decoding: using decompressing");
            }
            inflater = new Inflater();
            inflater.setInput(array, arrayOffset, arrayLength);
            ChannelBuffer tbuffer = ChannelBuffers.dynamicBuffer(length < 128 ? 128 : length);
            int inflated = inflater.inflate(tbuffer.array());
            if (inflated == 0 && inflater.needsDictionary()) {
                inflater.setDictionary(dictionary);
                inflated = inflater.inflate(tbuffer.array());
            }
            tbuffer.writerIndex(inflated);
            while (tbuffer.writerIndex() == tbuffer.array().length) {
                // expand the array
                final int writerIndex = tbuffer.writerIndex();
                tbuffer.ensureWritableBytes(32);
                inflated = inflater.inflate(tbuffer.array(), writerIndex, tbuffer.array().length
                        - writerIndex);
                //
                tbuffer.writerIndex(writerIndex + inflated);
                if (debugEnabled) {
                    logger.debug("expand decode ouput: expand "
                            + (tbuffer.array().length - writerIndex) + "  actual " + inflated
                            + " readbles " + tbuffer.readableBytes());
                }
            }
            inflater.end();
            buffer = tbuffer;
        } else {
            if (!buffer.hasArray()) {
                buffer = ChannelBuffers.wrappedBuffer(array, arrayOffset, arrayLength);
            } else {
                buffer.readerIndex(buffer.readerIndex() - arrayLength);
            }
        }
        Map<String, String> pairs = new HashMap<String, String>(size);
        for (int i = 0; i < size; i++) {
            int nameLength = buffer.readUnsignedShort();
            String name = new String(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(),
                    nameLength, utf8).toLowerCase();
            buffer.skipBytes(nameLength);
            int valueLength = buffer.readUnsignedShort();
            String value = new String(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(),
                    valueLength, utf8);
            buffer.skipBytes(valueLength);
            pairs.put(name, value);
            if (debugEnabled) {
                logger.debug("decoding: [" + (i + 1) + "/" + size + "] " + name + "='" + value
                        + "'");
            }
        }
        return pairs;
    }

    /**
     * 编码
     * 
     * @param headers
     * @param buffer
     */
    public static ChannelBuffer encode(int bufferOffset, Map<String, String> headers,
            boolean usingCompressing, ChannelBufferFactory factory) {
        int estimatedLength = 64 + bufferOffset + HeaderUtil.estimatedLength(headers);
        ChannelBuffer buffer = ChannelBuffers.dynamicBuffer(//
                estimatedLength, factory);
        buffer.writerIndex(bufferOffset);
        buffer.writeShort(headers.size());
        boolean debugEnabled = logger.isDebugEnabled();
        if (debugEnabled) {
            logger.debug("encoding: headers.size=" + headers.size());
        }
        final int offset = buffer.writerIndex();
        //
        int index = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String keyString = entry.getKey();
            byte[] key = keyString.getBytes(utf8);
            byte[] value = entry.getValue().getBytes(utf8);
            buffer.writeShort(key.length);
            buffer.writeBytes(key);
            buffer.writeShort(value.length);
            buffer.writeBytes(value);
            if (debugEnabled) {
                index++;
                logger.debug("encoding: [" + index + "/" + headers.size() + "] " + keyString + "='"
                        + entry.getValue() + "'");
            }
        }
        if (usingCompressing) {
            if (debugEnabled) {
                logger.debug("decoding: using compressing");
            }
            Deflater deflater = new Deflater();
            deflater.setDictionary(dictionary);
            deflater.setInput(buffer.array(), offset, buffer.writerIndex() - offset);
            deflater.finish();
            ChannelBuffer tbuffer = ChannelBuffers.dynamicBuffer(buffer.array().length);
            int deflated = deflater.deflate(tbuffer.array());
            tbuffer.writerIndex(deflated);
            while (tbuffer.writerIndex() == tbuffer.array().length) {
                final int writerIndex = tbuffer.writerIndex();
                tbuffer.writeBytes(bytes32);
                deflated = deflater.deflate(//
                        tbuffer.array(), writerIndex, tbuffer.array().length - writerIndex);
                //
                tbuffer.writerIndex(writerIndex + deflated);
                if (debugEnabled) {
                    logger.debug("expand encode ouput: expand "
                            + (tbuffer.array().length - writerIndex) + "  actual " + deflated
                            + "  readable " + tbuffer.readableBytes());
                }
            }
            deflater.end();
            buffer.writerIndex(offset);
            buffer = ChannelBuffers.wrappedBuffer(buffer, tbuffer);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("estimatedLength=" + estimatedLength + " actual=" + buffer.readableBytes());
        }
        return buffer;
    }

    public static int estimatedLength(Map<String, String> headers) {
        if (headers == null || headers.size() == 0) {
            return 0;
        }
        return headers.size() << 5; //32 = (2shorts+key+value);
    }

}
