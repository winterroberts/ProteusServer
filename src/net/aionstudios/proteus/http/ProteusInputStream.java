package net.aionstudios.proteus.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ProteusInputStream extends FilterInputStream {
	
	private boolean readyClose;
	
	public ProteusInputStream(InputStream stream) {
		super(stream);
		readyClose = false;
	}
	
	@Override
	public void close() throws IOException {
		if (readyClose) {
			if (in instanceof ProteusInputStream) {
				((ProteusInputStream) in).readyClose();
			}
			in.close();
		}
	}
	
	public void readyClose() {
		readyClose = true;
	}

}
