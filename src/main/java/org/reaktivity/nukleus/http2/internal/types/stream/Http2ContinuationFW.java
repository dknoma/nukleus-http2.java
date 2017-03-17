/**
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package org.reaktivity.nukleus.http2.internal.types.stream;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.reaktivity.nukleus.http2.internal.types.Flyweight;

import java.nio.ByteOrder;
import java.util.function.Consumer;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2Flags.END_HEADERS;
import static org.reaktivity.nukleus.http2.internal.types.stream.Http2FrameType.CONTINUATION;

/*

    Flyweight for HTTP2 CONTINUATION frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |                   Header Block Fragment (*)                 ...
    +---------------------------------------------------------------+

 */
public class Http2ContinuationFW extends Flyweight
{
    private static final int LENGTH_OFFSET = 0;
    private static final int TYPE_OFFSET = 3;
    private static final int FLAGS_OFFSET = 4;
    private static final int STREAM_ID_OFFSET = 5;
    private static final int PAYLOAD_OFFSET = 9;

    private final HpackHeaderBlockFW headerBlockRO = new HpackHeaderBlockFW();

    public int payloadLength()
    {
        int length = (buffer().getByte(offset() + LENGTH_OFFSET) & 0xFF) << 16;
        length += (buffer().getByte(offset() + LENGTH_OFFSET + 1) & 0xFF) << 8;
        length += buffer().getByte(offset() + LENGTH_OFFSET + 2) & 0xFF;
        return length;
    }

    public Http2FrameType type()
    {
        //assert buffer().getByte(offset() + TYPE_OFFSET) == CONTINUATION.getType();
        return CONTINUATION;
    }

    public byte flags()
    {
        return buffer().getByte(offset() + FLAGS_OFFSET);
    }

    // streamId != 0, caller to validate
    public int streamId()
    {
        // Most significant bit is reserved and is ignored when receiving
        return buffer().getInt(offset() + STREAM_ID_OFFSET, BIG_ENDIAN) & 0x7F_FF_FF_FF;
    }

    public boolean endHeaders()
    {
        return Http2Flags.endHeaders(flags());
    }

    public void forEach(Consumer<HpackHeaderFieldFW> headerField)
    {
        headerBlockRO.forEach(headerField);
    }

    @Override
    public int limit()
    {
        return offset() + PAYLOAD_OFFSET + payloadLength();
    }

    @Override
    public Http2ContinuationFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);
        headerBlockRO.wrap(buffer(), offset() + PAYLOAD_OFFSET, offset() + PAYLOAD_OFFSET + payloadLength());

        checkLimit(limit(), maxLimit);
        return this;
    }

    @Override
    public String toString()
    {
        return String.format("%s frame <length=%s, type=%s, flags=%s, id=%s>",
                type(), payloadLength(), type(), flags(), streamId());
    }

    public static final class Builder extends Flyweight.Builder<Http2ContinuationFW>
    {
        private final HpackHeaderBlockFW.Builder blockRW = new HpackHeaderBlockFW.Builder();

        public Builder()
        {
            super(new Http2ContinuationFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            int length = 0;
            buffer().putByte(offset() + LENGTH_OFFSET, (byte) ((length & 0x00_FF_00_00) >>> 16));
            buffer().putByte(offset() + LENGTH_OFFSET +1, (byte) ((length & 0x00_00_FF_00) >>> 8));
            buffer().putByte(offset() + LENGTH_OFFSET + 2, (byte) ((length & 0x00_00_00_FF)));

            buffer().putByte(offset() + TYPE_OFFSET, CONTINUATION.getType());

            buffer().putByte(offset() + FLAGS_OFFSET, (byte) 0);

            limit(offset() + PAYLOAD_OFFSET);

            blockRW.wrap(buffer(), offset() + PAYLOAD_OFFSET, maxLimit());

            return this;
        }

        public Builder streamId(int streamId)
        {
            buffer().putInt(offset() + STREAM_ID_OFFSET, streamId, ByteOrder.BIG_ENDIAN);
            return this;
        }

        public Builder endHeaders()
        {
            buffer().putByte(offset() + FLAGS_OFFSET, END_HEADERS);
            return this;
        }

        public Builder header(Consumer<HpackHeaderFieldFW.Builder> mutator)
        {
            blockRW.header(mutator);
            int length = blockRW.limit() - offset() - PAYLOAD_OFFSET;
            buffer().putByte(offset() + LENGTH_OFFSET, (byte) ((length & 0x00_FF_00_00) >>> 16));
            buffer().putByte(offset() + LENGTH_OFFSET +1, (byte) ((length & 0x00_00_FF_00) >>> 8));
            buffer().putByte(offset() + LENGTH_OFFSET + 2, (byte) ((length & 0x00_00_00_FF)));

            limit(blockRW.limit());
            return this;
        }

    }
}

