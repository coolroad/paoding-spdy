package net.paoding.spdy.common.frame.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * @author weibo.leo@gmail.com
 */
public class ControlFrameUtilTest {

	@Test
	public void test() {
		for (int version = 1; version < 10000; version++) {
			//先编码
			short first16bits = ControlFrameUtil.encodeVersion(version);
			//再解码
			int _version = ControlFrameUtil.extractVersion(first16bits);
			//最后比较
			assertEquals(version, _version);
		}
	}
}
