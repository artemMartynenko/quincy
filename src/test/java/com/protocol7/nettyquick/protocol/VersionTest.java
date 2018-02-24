package com.protocol7.nettyquick.protocol;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.protocol7.nettyquick.TestUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

public class VersionTest {

  @Test
  public void write() {
    ByteBuf bb = Unpooled.buffer();
    Version.DRAFT_09.write(bb);

    TestUtil.assertBuffer("ff000009", bb);
    TestUtil.assertBufferExhusted(bb);
  }

  @Test
  public void roundtrip() {
    ByteBuf bb = Unpooled.buffer();
    Version.DRAFT_09.write(bb);

    Version parsed = Version.read(bb);

    assertEquals(Version.DRAFT_09, parsed);
  }
}