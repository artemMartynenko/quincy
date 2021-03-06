package com.protocol7.quincy.protocol.packets;

import com.protocol7.quincy.protocol.ConnectionId;
import com.protocol7.quincy.protocol.Version;
import com.protocol7.quincy.tls.aead.AEAD;
import com.protocol7.quincy.tls.aead.AEADProvider;
import com.protocol7.quincy.utils.Hex;
import com.protocol7.quincy.utils.Opt;
import com.protocol7.quincy.utils.Pair;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class RetryPacket implements Packet {

  public static HalfParsedPacket<RetryPacket> parse(final ByteBuf bb) {
    final byte b = bb.readByte(); // TODO verify reserved and packet types

    final int odcil = ConnectionId.lastLength(b & 0xFF);
    final Version version = Version.read(bb);

    final Pair<Optional<ConnectionId>, Optional<ConnectionId>> cids = ConnectionId.readPair(bb);

    final Optional<ConnectionId> destConnId = cids.getFirst();
    final Optional<ConnectionId> srcConnId = cids.getSecond();

    final ConnectionId orgConnId = ConnectionId.readOptional(odcil, bb).get();

    final byte[] retryToken = new byte[bb.readableBytes()];
    bb.readBytes(retryToken);

    return new HalfParsedPacket<>() {

      @Override
      public Optional<Version> getVersion() {
        return Optional.of(version);
      }

      @Override
      public Optional<ConnectionId> getConnectionId() {
        return destConnId;
      }

      @Override
      public RetryPacket complete(final AEADProvider aeadProvider) {
        return new RetryPacket(version, destConnId, srcConnId, orgConnId, retryToken);
      }
    };
  }

  private final Version version;
  private final Optional<ConnectionId> destinationConnectionId;
  private final Optional<ConnectionId> sourceConnectionId;
  private final ConnectionId originalConnectionId;
  private final byte[] retryToken;

  public RetryPacket(
      final Version version,
      final Optional<ConnectionId> destinationConnectionId,
      final Optional<ConnectionId> sourceConnectionId,
      final ConnectionId originalConnectionId,
      final byte[] retryToken) {
    this.version = version;
    this.destinationConnectionId = destinationConnectionId;
    this.sourceConnectionId = sourceConnectionId;
    this.originalConnectionId = originalConnectionId;
    this.retryToken = retryToken;
  }

  public Optional<ConnectionId> getDestinationConnectionId() {
    return destinationConnectionId;
  }

  @Override
  public void write(final ByteBuf bb, final AEAD aead) {
    int b = (PACKET_TYPE_MASK | PacketType.Retry.getType() << 4) & 0xFF;
    b = b | 0x40; // fixed

    b |= ((originalConnectionId.getLength() - 3) & 0b1111);
    bb.writeByte(b);

    version.write(bb);

    ConnectionId.write(destinationConnectionId, sourceConnectionId, bb);

    originalConnectionId.write(bb);

    bb.writeBytes(retryToken);
  }

  public Optional<ConnectionId> getSourceConnectionId() {
    return sourceConnectionId;
  }

  public ConnectionId getOriginalConnectionId() {
    return originalConnectionId;
  }

  public byte[] getRetryToken() {
    return retryToken;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    final RetryPacket that = (RetryPacket) o;
    return version == that.version
        && Objects.equals(destinationConnectionId, that.destinationConnectionId)
        && Objects.equals(sourceConnectionId, that.sourceConnectionId)
        && Objects.equals(originalConnectionId, that.originalConnectionId)
        && Arrays.equals(retryToken, that.retryToken);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(version, destinationConnectionId, sourceConnectionId, originalConnectionId);
    result = 31 * result + Arrays.hashCode(retryToken);
    return result;
  }

  @Override
  public String toString() {
    return "RetryPacket{"
        + "version="
        + version
        + ", destinationConnectionId="
        + Opt.toString(destinationConnectionId)
        + ", sourceConnectionId="
        + Opt.toString(sourceConnectionId)
        + ", originalConnectionId="
        + originalConnectionId
        + ", retryToken="
        + Hex.hex(retryToken)
        + '}';
  }
}
