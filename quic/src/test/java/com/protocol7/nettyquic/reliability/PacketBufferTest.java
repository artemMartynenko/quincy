package com.protocol7.nettyquic.reliability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.protocol7.nettyquic.protocol.PacketNumber;
import com.protocol7.nettyquic.protocol.frames.Frame;
import com.protocol7.nettyquic.protocol.frames.MaxDataFrame;
import com.protocol7.nettyquic.protocol.packets.FullPacket;
import com.protocol7.nettyquic.protocol.packets.ShortPacket;
import com.protocol7.nettyquic.utils.Ticker;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class PacketBufferTest {

  @Mock private Ticker ticker;
  private PacketNumber pn1 = new PacketNumber(1);
  private PacketNumber pn2 = new PacketNumber(2);
  private FullPacket packet1 = p(pn1);
  private FullPacket packet2 = p(pn2);

  private PacketBuffer buffer;

  @Before
  public void setUp() {
    when(ticker.nanoTime()).thenReturn(0L);

    buffer = new PacketBuffer(ticker);
  }

  @Test
  public void test() {
    assertTrue(buffer.isEmpty());
    assertFalse(buffer.contains(pn1));

    buffer.put(packet1);

    assertFalse(buffer.isEmpty());
    assertTrue(buffer.contains(pn1));

    assertTrue(buffer.remove(pn1));

    assertTrue(buffer.isEmpty());
    assertFalse(buffer.contains(pn1));
  }

  @Test
  public void remove() {
    assertFalse(buffer.remove(pn1));
    buffer.put(packet1);
    assertTrue(buffer.remove(pn1));
  }

  @Test
  public void drain() {
    when(ticker.nanoTime()).thenReturn(100L);

    assertTrue(buffer.drainSince(10, TimeUnit.NANOSECONDS).isEmpty());

    buffer.put(packet1);
    when(ticker.nanoTime()).thenReturn(200L);
    buffer.put(packet2);

    assertEquals(List.of(f(1)), buffer.drainSince(10, TimeUnit.NANOSECONDS));
    assertTrue(buffer.drainSince(10, TimeUnit.NANOSECONDS).isEmpty());

    when(ticker.nanoTime()).thenReturn(300L);
    assertEquals(List.of(f(2)), buffer.drainSince(10, TimeUnit.NANOSECONDS));
    assertTrue(buffer.drainSince(10, TimeUnit.NANOSECONDS).isEmpty());
  }

  private FullPacket p(PacketNumber pn) {
    return ShortPacket.create(false, Optional.empty(), pn, f(pn.asLong()));
  }

  private Frame f(long i) {
    return new MaxDataFrame(i);
  }
}