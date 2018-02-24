package com.protocol7.nettyquick.protocol;

import com.google.common.primitives.Longs;
import com.google.common.primitives.UnsignedLong;
import com.protocol7.nettyquick.utils.Rnd;
import io.netty.buffer.ByteBuf;

public class ConnectionId {

  public static ConnectionId random() {
    return new ConnectionId(Rnd.rndUnsignedLong());
  }

  public static ConnectionId read(final ByteBuf bb) {
    return new ConnectionId(UnsignedLong.fromLongBits(bb.readLong()));
  }

  private final UnsignedLong id;

  public ConnectionId(final UnsignedLong id) {
    this.id = id;
  }

  public void write(final ByteBuf bb) {
    bb.writeBytes(Longs.toByteArray(id.longValue()));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ConnectionId that = (ConnectionId) o;

    return id.equals(that.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return id.toString();
  }
}
