/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.protocol7.quincy.http3;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.UnstableApi;

/**
 * Translates HTTP/1.x object writes into HTTP/2 frames.
 *
 * <p>See {@link InboundHttp2ToHttpAdapter} to get translation from HTTP/2 frames to HTTP/1.x
 * objects.
 */
@UnstableApi
public class HttpToHttp2ConnectionHandler extends Http2ConnectionHandler {

  private final boolean validateHeaders;
  private int currentStreamId;

  protected HttpToHttp2ConnectionHandler(
      final Http2ConnectionDecoder decoder,
      final Http2ConnectionEncoder encoder,
      final Http2Settings initialSettings,
      final boolean validateHeaders) {
    super(decoder, encoder, initialSettings);
    this.validateHeaders = validateHeaders;
  }

  protected HttpToHttp2ConnectionHandler(
      final Http2ConnectionDecoder decoder,
      final Http2ConnectionEncoder encoder,
      final Http2Settings initialSettings,
      final boolean validateHeaders,
      final boolean decoupleCloseAndGoAway) {
    super(decoder, encoder, initialSettings, decoupleCloseAndGoAway);
    this.validateHeaders = validateHeaders;
  }

  /**
   * Get the next stream id either from the {@link HttpHeaders} object or HTTP/2 codec
   *
   * @param httpHeaders The HTTP/1.x headers object to look for the stream id
   * @return The stream id to use with this {@link HttpHeaders} object
   * @throws Exception If the {@code httpHeaders} object specifies an invalid stream id
   */
  private int getStreamId(final HttpHeaders httpHeaders) throws Exception {
    return httpHeaders.getInt(
        HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(),
        connection().local().incrementAndGetNextStreamId());
  }

  /** Handles conversion of {@link HttpMessage} and {@link HttpContent} to HTTP/2 frames. */
  @Override
  public void write(
      final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) {

    if (!(msg instanceof HttpMessage || msg instanceof HttpContent)) {
      ctx.write(msg, promise);
      return;
    }

    boolean release = true;
    final Http2CodecUtil.SimpleChannelPromiseAggregator promiseAggregator =
        new Http2CodecUtil.SimpleChannelPromiseAggregator(promise, ctx.channel(), ctx.executor());
    try {
      final Http2ConnectionEncoder encoder = encoder();
      boolean endStream = false;
      if (msg instanceof HttpMessage) {
        final HttpMessage httpMsg = (HttpMessage) msg;

        // Provide the user the opportunity to specify the streamId
        currentStreamId = getStreamId(httpMsg.headers());

        // Convert and write the headers.
        final Http2Headers http2Headers =
            HttpConversionUtil.toHttp2Headers(httpMsg, validateHeaders);
        endStream =
            msg instanceof FullHttpMessage && !((FullHttpMessage) msg).content().isReadable();
        writeHeaders(
            ctx,
            encoder,
            currentStreamId,
            httpMsg.headers(),
            http2Headers,
            endStream,
            promiseAggregator);
      }

      if (!endStream && msg instanceof HttpContent) {
        boolean isLastContent = false;
        HttpHeaders trailers = EmptyHttpHeaders.INSTANCE;
        Http2Headers http2Trailers = EmptyHttp2Headers.INSTANCE;
        if (msg instanceof LastHttpContent) {
          isLastContent = true;

          // Convert any trailing headers.
          final LastHttpContent lastContent = (LastHttpContent) msg;
          trailers = lastContent.trailingHeaders();
          http2Trailers = HttpConversionUtil.toHttp2Headers(trailers, validateHeaders);
        }

        // Write the data
        final ByteBuf content = ((HttpContent) msg).content();
        endStream = isLastContent && trailers.isEmpty();
        release = false;
        encoder.writeData(
            ctx, currentStreamId, content, 0, endStream, promiseAggregator.newPromise());

        if (!trailers.isEmpty()) {
          // Write trailing headers.
          writeHeaders(
              ctx, encoder, currentStreamId, trailers, http2Trailers, true, promiseAggregator);
        }
      }
    } catch (final Throwable t) {
      onError(ctx, true, t);
      promiseAggregator.setFailure(t);
    } finally {
      if (release) {
        ReferenceCountUtil.release(msg);
      }
      promiseAggregator.doneAllocatingPromises();
    }
  }

  private static void writeHeaders(
      final ChannelHandlerContext ctx,
      final Http2ConnectionEncoder encoder,
      final int streamId,
      final HttpHeaders headers,
      final Http2Headers http2Headers,
      final boolean endStream,
      final Http2CodecUtil.SimpleChannelPromiseAggregator promiseAggregator) {
    final int dependencyId =
        headers.getInt(HttpConversionUtil.ExtensionHeaderNames.STREAM_DEPENDENCY_ID.text(), 0);
    final short weight =
        headers.getShort(
            HttpConversionUtil.ExtensionHeaderNames.STREAM_WEIGHT.text(),
            Http2CodecUtil.DEFAULT_PRIORITY_WEIGHT);
    encoder.writeHeaders(
        ctx,
        streamId,
        http2Headers,
        dependencyId,
        weight,
        false,
        0,
        endStream,
        promiseAggregator.newPromise());
  }
}
