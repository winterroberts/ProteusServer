package net.winrob.proteus.compression;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterInputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.nixxcode.jvmbrotli.dec.BrotliInputStream;
import com.nixxcode.jvmbrotli.enc.BrotliOutputStream;
import com.nixxcode.jvmbrotli.enc.Encoder.Parameters;

/**
 * A utility class which wraps input in the named {@link CompressionEncoding} to a byte[].
 * 
 * @author Winter Roberts
 *
 */
public class Compressor {
	
	/**
	 * Compresses the string to an encoded byte[]
	 * 
	 * @param str The string to be compressed.
	 * @param ce The {@link CompressionEncoding} to be used.
	 * @return The resultant encoded string as a byte[].
	 * @throws IOException If the stream or charset encoding causes a failure.
	 */
	public static byte[] compress(String str, CompressionEncoding ce) throws IOException {
		return compress(new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8)), ce);
	}
	
	public static byte[] tryCompress(String str, CompressionEncoding ce) {
		try {
			return compress(str, ce);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new byte[0];
	}
	
	/**
	 * Compresses the byte[] to an encoded byte[]
	 * 
	 * @param bytes The byte[] to be compressed.
	 * @param ce The {@link CompressionEncoding} to be used.
	 * @return The resultant encoded byte[] as a byte[].
	 * @throws IOException If the stream encoding causes a failure.
	 */
	public static byte[] compress(InputStream stream, CompressionEncoding ce) throws IOException {
		ByteArrayOutputStream obj = new ByteArrayOutputStream();
		OutputStream o;
		switch(ce) {
			case BR:
				o = new BrotliOutputStream(obj, new Parameters(), 65536);
				break;
			case DEFLATE:
				o = new DeflaterOutputStream(obj, new Deflater(Deflater.DEFAULT_COMPRESSION), 65536);
				break;
			case GZIP:
				o = new GZIPOutputStream(obj, 65536);
				break;
			case NONE:
			default:
				return stream.readAllBytes();
		}
		byte[] bytes;
		while ((bytes = stream.readNBytes(65536)).length != 0) {
			o.write(bytes);
		}
		o.flush();
		o.close();
		return obj.toByteArray();
	}
	
	public static byte[] tryCompress(byte[] bytes, CompressionEncoding ce) {
		try {
			return compress(new ByteArrayInputStream(bytes), ce);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new byte[0];
	}
	
	/**
	 * Decompresses the encoded byte[] to an byte[]
	 * 
	 * @param bytes The byte[] to be decompressed.
	 * @param ce The {@link CompressionEncoding} to be used.
	 * @return The resultant decoded byte[] as a byte[].
	 * @throws IOException If the stream decoding causes a failure.
	 */
	public static byte[] decompress(byte[] bytes, CompressionEncoding ce) throws IOException {
		if ((bytes == null) || (bytes.length == 0)) {
			return null;
		}
		ByteArrayOutputStream obj = new ByteArrayOutputStream();
		InputStream in;
		switch(ce) {
			case BR:
				in = new BrotliInputStream(new ByteArrayInputStream(bytes));
				break;
			case DEFLATE:
				in = new DeflaterInputStream(new ByteArrayInputStream(bytes));
				break;
			case GZIP:
				in = new GZIPInputStream(new ByteArrayInputStream(bytes));
				break;
			case NONE:
			default:
				return bytes;
		}
		obj.write(in.readAllBytes());
		obj.flush();
		obj.close();
		return obj.toByteArray();
	}
	
	public static byte[] tryDecompress(byte[] bytes, CompressionEncoding ce) {
		try {
			return decompress(bytes, ce);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new byte[0];
	}

}
