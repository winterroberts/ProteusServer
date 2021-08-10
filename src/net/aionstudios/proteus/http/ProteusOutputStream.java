package net.aionstudios.proteus.http;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class ProteusOutputStream extends FilterOutputStream {

	private boolean readyClose;
	
	public ProteusOutputStream(OutputStream out) {
		super(out);
		readyClose = false;
	}
	
	@Override
	public void close() throws IOException {
		if (readyClose) {
			if (out instanceof ProteusOutputStream) {
				((ProteusOutputStream) out).readyClose();
			}
			out.close();
		}
	}
	
	@Override
	public void write(byte[] b, int off, int len) {
		// skip
	}
	
	public void readyClose() {
		readyClose = true;
	}

}
