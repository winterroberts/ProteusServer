package net.winrob.proteus.api.request;

import java.nio.ByteBuffer;

import net.winrob.proteus.api.Constants;
import net.winrob.proteus.api.h2.FrameType;
import net.winrob.proteus.util.StreamUtils;

public class StreamBufferImpl implements StreamBuffer {
	
	private ByteBuffer buffer;
	private int bufferSize;
	private final FrameType frameType;
	
	public StreamBufferImpl(FrameType frameType) {
		buffer = ByteBuffer.allocate(0);
		bufferSize = 0;
		this.frameType = frameType;
	}

	@Override
	public boolean put(byte[] payload) {
		if (bufferSize + payload.length <= Constants.MAX_PAYLOAD) {
			buffer = StreamUtils.joinByteArrayToBuffer(buffer.array(), payload);
			bufferSize += payload.length;
			return true;
		}
		return false;
	}

	@Override
	public FrameType getFrameType() {
		return frameType;
	}

	@Override
	public int getBufferSize() {
		return bufferSize;
	}

	@Override
	public byte[] getData() {
		return buffer.array();
	}

}
