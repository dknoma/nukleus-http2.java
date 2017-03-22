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
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.reaktivity.nukleus.http2.internal.types.Flyweight;

import java.nio.ByteOrder;

import static org.reaktivity.nukleus.http2.internal.types.stream.Flags.ACK;
import static org.reaktivity.nukleus.http2.internal.types.stream.FrameType.PING;

/*

    Flyweight for HTTP2 PING frame

    +-----------------------------------------------+
    |                 Length (24)                   |
    +---------------+---------------+---------------+
    |   Type (8)    |   Flags (8)   |
    +-+-------------+---------------+-------------------------------+
    |R|                 Stream Identifier (31)                      |
    +=+=============+===============================================+
    |                                                               |
    |                      Opaque Data (64)                         |
    |                                                               |
    +---------------------------------------------------------------+

 */
public class PingFW extends Flyweight
{
    private static final int LENGTH_OFFSET = 0;
    private static final int TYPE_OFFSET = 3;
    private static final int FLAGS_OFFSET = 4;
    private static final int STREAM_ID_OFFSET = 5;
    private static final int PAYLOAD_OFFSET = 9;

    private final AtomicBuffer payloadRO = new UnsafeBuffer(new byte[0]);

    public int payloadOffset()
    {
        return offset() + PAYLOAD_OFFSET;
    }

    public int payloadLength()
    {
        return 8;
    }

    public FrameType type()
    {
        return PING;
    }

    public byte flags()
    {
        return buffer().getByte(offset() + FLAGS_OFFSET);
    }

    public int streamId()
    {
        return 0;
    }

    public DirectBuffer payload()
    {
        return payloadRO;
    }

    public boolean ack()
    {
        return Flags.ack(flags());
    }

    @Override
    public int limit()
    {
        return offset() + PAYLOAD_OFFSET + payloadLength();
    }

    @Override
    public PingFW wrap(DirectBuffer buffer, int offset, int maxLimit)
    {
        super.wrap(buffer, offset, maxLimit);

        int streamId = Http2FrameFW.streamId(buffer, offset);
        if (streamId != 0)
        {
            throw new Http2Exception(String.format("Invalid PING frame stream-id=%d", streamId));
        }

        FrameType type = Http2FrameFW.type(buffer, offset);
        if (type != PING)
        {
            throw new Http2Exception(String.format("Invalid PING frame type=%s", type));
        }

        int payloadLength = Http2FrameFW.payloadLength(buffer, offset);
        if (payloadLength != 8)
        {
            throw new Http2Exception(String.format("Invalid PING frame length=%d (must be 8)", payloadLength));
        }

        payloadRO.wrap(buffer, payloadOffset(), payloadLength());

        checkLimit(limit(), maxLimit);
        return this;
    }

    @Override
    public String toString()
    {
        return String.format("%s frame <length=%s, type=%s, flags=%s, id=%s>",
                type(), payloadLength(), type(), flags(), streamId());
    }

    public static final class Builder extends Flyweight.Builder<PingFW>
    {

        public Builder()
        {
            super(new PingFW());
        }

        @Override
        public Builder wrap(MutableDirectBuffer buffer, int offset, int maxLimit)
        {
            super.wrap(buffer, offset, maxLimit);

            Http2FrameFW.putPayloadLength(buffer, offset + LENGTH_OFFSET, 8);

            buffer.putByte(offset + TYPE_OFFSET, PING.getType());

            buffer.putByte(offset + FLAGS_OFFSET, (byte) 0);

            buffer.putInt(offset + STREAM_ID_OFFSET, 0, ByteOrder.BIG_ENDIAN);

            limit(offset + PAYLOAD_OFFSET + 8);

            return this;
        }

        public Builder ack()
        {
            buffer().putByte(offset() + FLAGS_OFFSET, ACK);

            return this;
        }

        public PingFW.Builder payload(DirectBuffer payload)
        {
            return payload(payload, 0, payload.capacity());
        }

        public PingFW.Builder payload(DirectBuffer payload, int offset, int length)
        {
            if (length != 8)
            {
                throw new IllegalArgumentException(String.format("Invalid PING frame length = %d (must be 8)", length));
            }

            buffer().putBytes(offset() + PAYLOAD_OFFSET, payload, offset, 8);
            return this;
        }

    }
}

