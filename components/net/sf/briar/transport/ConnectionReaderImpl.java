package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;

import java.io.IOException;
import java.io.InputStream;

import net.sf.briar.api.FormatException;
import net.sf.briar.api.transport.ConnectionReader;

class ConnectionReaderImpl extends InputStream implements ConnectionReader {

	private final IncomingReliabilityLayer in;
	private final boolean tolerateErrors;

	private Frame frame;
	private int offset = 0, length = 0;

	ConnectionReaderImpl(IncomingReliabilityLayer in, boolean tolerateErrors) {
		this.in = in;
		this.tolerateErrors = tolerateErrors;
		frame = new Frame(in.getMaxFrameLength());
	}

	public InputStream getInputStream() {
		return this;
	}

	@Override
	public int read() throws IOException {
		if(length == -1) return -1;
		while(length == 0) if(!readFrame()) return -1;
		int b = frame.getBuffer()[offset] & 0xff;
		offset++;
		length--;
		return b;
	}

	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if(length == -1) return -1;
		while(length == 0) if(!readFrame()) return -1;
		len = Math.min(len, length);
		System.arraycopy(frame.getBuffer(), offset, b, off, len);
		offset += len;
		length -= len;
		return len;
	}

	private boolean readFrame() throws IOException {
		assert length == 0;
		while(true) {
			try {
				frame = in.readFrame(frame);
				if(frame == null) {
					length = -1;
					return false;
				}
				offset = FRAME_HEADER_LENGTH;
				length = HeaderEncoder.getPayloadLength(frame.getBuffer());
				return true;
			} catch(InvalidDataException e) {
				if(tolerateErrors) continue;
				throw new FormatException();
			}
		}
	}
}
