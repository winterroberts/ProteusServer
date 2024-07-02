package net.winrob.proteus.api.h2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import net.winrob.proteus.api.request.ProteusH2ConnectionImpl;
import net.winrob.proteus.api.request.StreamBuffer;

public class StreamImpl implements Stream {
	
	private final int id;
	private final ProteusH2ConnectionImpl connection;
	
	private StreamBuffer buffer;
	private StreamState state;
	
	public StreamImpl(ProteusH2ConnectionImpl connection, int id) {
		this.connection = connection;
		this.id = id;
		state = StreamState.IDLE;
	}
	
	public void readFrame(FrameType type, int length, byte flags, InputStream in) throws IOException {
		switch (type) {
		case DATA: {
			boolean padded = getPaddedFlag(flags);
			boolean endStream = getEndStreamFlag(flags);
			int padOctets = padded ? in.read() : 0;
			int readLen = length - padOctets;
			if (readLen <= 0) {
				// TODO connection error
			}
			byte[] data = in.readNBytes(readLen);
			in.readNBytes(padOctets);
			
			// TODO buffer
			break;
		}
		case HEADERS: {
			boolean priority = getPriorityFlag(flags);
			boolean padded = getPaddedFlag(flags);
			boolean endHeaders = getEndHeadersFlag(flags);
			boolean endStream = getEndStreamFlag(flags);
			int padOctets = padded ? in.read() : 0;
			int readLen = length - padOctets;
			if (priority) {
				ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
				b.put(in.readNBytes(4));
				b.rewind();
				int streamDependency = b.getInt();
				boolean exclusive = ((streamDependency >> 63) & 1) == 1;
				streamDependency = streamDependency & 0x7FFFFFFF;
				byte weight = (byte) in.read();
				readLen -= 5;
			}
			if (readLen <= 0) {
				// TODO connection error
			}
			
			byte[] data = in.readNBytes(readLen);
			in.readNBytes(padOctets);
			
			break;
		}
		case PRIORITY: {
			// this is deprecated!
			break;
		}
		case RST_STREAM: {
			ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
			b.put(in.readNBytes(4));
			b.rewind();
			int errorCode = b.getInt();
			
			break;
		}
		case SETTINGS: {
			boolean ack = getAckFlag(flags);
			if (length % 6 != 0) {
				// TODO frame size error
			}
			byte[] data = in.readNBytes(length);
			
			break;
		}
		case PUSH_PROMISE: {
			boolean padded = getPaddedFlag(flags);
			boolean endHeaders = getEndHeadersFlag(flags);
			int padOctets = padded ? in.read() : 0;
			
			ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
			b.put(in.readNBytes(4));
			b.rewind();
			int promisedStreamID = b.getInt();
			boolean reserved = ((promisedStreamID >> 63) & 1) == 1;
			promisedStreamID = promisedStreamID & 0x7FFFFFFF;
			
			int readLen = length - padOctets - 4;
			if (readLen <= 0) {
				// TODO error
			}
			
			byte[] data  = in.readNBytes(readLen);
			in.readNBytes(padOctets);
			
			break;
		}
		case PING: {
			boolean ack = getAckFlag(flags);
			byte[] data = in.readNBytes(64);
			
			break;
		}
		case GOAWAY: {
			ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
			b.put(in.readNBytes(4));
			b.rewind();
			int lastStreamID = b.getInt();
			boolean reserved = ((lastStreamID >> 63) & 1) == 1;
			lastStreamID = lastStreamID & 0x7FFFFFFF;
			
			b.clear();
			b.put(in.readNBytes(4));
			b.rewind();
			int errorCode = b.getInt();
			
			int readLen = length - 8;
			if (readLen <= 0) {
				// TODO error
			}
			
			byte[] data = in.readNBytes(readLen);
			
			break;
		}
		case WINDOW_UPDATE: {
			ByteBuffer b = ByteBuffer.allocate(Integer.BYTES);
			b.put(in.readNBytes(4));
			b.rewind();
			int windowSizeIncrement = b.getInt();
			boolean reserved = ((windowSizeIncrement >> 63) & 1) == 1;
			windowSizeIncrement = windowSizeIncrement & 0x7FFFFFFF;
			
			break;
		}
		case CONTINUATION: {
			boolean endHeaders = getEndHeadersFlag(flags);
		}
		default:
			// TODO
		}
	}
	
	public void buffer(byte[] payload) {
		
	}
	
	@Override
	public int getID() {
		return id;
	}

	@Override
	public StreamState getState() {
		return state;
	}

	@Override
	public StreamBuffer getStreamBuffer() {
		return buffer;
	}
	
	private boolean getPriorityFlag(byte flags) {
		return ((flags >> 5) & 1) == 1;
	}
	
	private boolean getPaddedFlag(byte flags) {
		return ((flags >> 3) & 1) == 1;
	}
	
	private boolean getEndHeadersFlag(byte flags) {
		return ((flags >> 2) & 1) == 1;
	}
	
	private boolean getEndStreamFlag(byte flags) {
		return (flags & 1) == 1;
	}
	
	private boolean getAckFlag(byte flags) {
		return (flags & 1) == 1;
	}

}
