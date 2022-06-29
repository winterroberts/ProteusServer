package net.aionstudios.proteus.api.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import net.aionstudios.proteus.compression.CompressionEncoding;
import net.aionstudios.proteus.compression.Compressor;
import net.aionstudios.proteus.header.HeaderValue;
import net.aionstudios.proteus.header.ProteusHeaderBuilder;
import net.aionstudios.proteus.header.ProteusHttpHeaders;
import net.aionstudios.proteus.header.QualityValue;
import net.aionstudios.proteus.util.StreamUtils;

public class RequestBodyImpl implements RequestBody {
	
	private ParameterMap<String> bodyData;
	private ParameterMap<MultipartFileStream> fileData;
	
	private String contentType;
	private String rawText;
	private MultipartFileStream rawFile;
	
	private boolean resume;

	private RequestBodyImpl() {
		bodyData = new ParameterMap<>();
		fileData = new ParameterMap<>();
		resume = false;
		contentType = null;
	}
	
	@Override
	public ParameterMap<String> getBodyParams() {
		return bodyData;
	}
	
	@Override
	public ParameterMap<MultipartFileStream> getFiles() {
		return fileData;
	}
	
	/**
	 * Creates a new RequestBody object, processing body data.
	 * 
	 * @param request The {@link ProteusHttpRequest} to process.
	 * @param inputStream The {@link InputStream} containing the body of the request, if any.
	 * @return The RequestBody object that has been created, which may be empty.
	 */
	public static RequestBody createRequestBody(ProteusHttpRequest request, InputStream inputStream) {
		RequestBodyImpl body = new RequestBodyImpl();
		ProteusHttpHeaders headers = request.getHeaders();
		if (headers.hasHeader("Content-Type")) {
			if (headers.hasHeader("Content-Length")) {
				HeaderValue contentType = headers.getHeader("Content-Type").getLast();
				int contentLength = Integer.parseInt(headers.getHeader("Content-Length").getLast().getValue());
				body.contentType = contentType.getValue();
				switch(contentType.getValue()) {
				case "application/x-www-form-urlencoded":
					body.contentFormUrlEncoded(contentLength, inputStream);
					break;
				case "multipart/form-data":
					body.contentFormData(inputStream, contentType, null);
					break;
				case "application/json":
				case "text/plain":
				case "text/html":
				case "text/xml":
					body.contentRaw(headers, contentLength, inputStream);
					break;
				default:
					body.fileRaw(headers, contentLength, inputStream);
				}
			} else if (headers.hasHeader("Transfer-Encoding")) {
				HeaderValue contentType = headers.getHeader("Content-Type").getLast();
				body.contentType = contentType.getValue();
				try {
					byte[] file = body.readFileAsChunks(headers, inputStream);
					List<CompressionEncoding> decompressOrder = new LinkedList<>();
					if (headers.hasHeader("Content-Encoding")) {
						List<HeaderValue> contentEncoding = headers.getHeader("Content-Encoding").getValues();
						for (int i = contentEncoding.size() - 1; i >= 0; i--) {
							HeaderValue hv = contentEncoding.get(i);
							List<QualityValue> qv = hv.getValues();
							for (int j = qv.size()-1; i >= 0; i--) {
								CompressionEncoding ce = CompressionEncoding.forName(qv.get(j).getValue());
								if (ce != CompressionEncoding.NONE) {
									if (!decompressOrder.contains(ce)) {
										decompressOrder.add(ce);
									} else {
										// TODO error
									}
								}
							}
						}
					}
					for (CompressionEncoding ce : decompressOrder) {
						file = Compressor.decompress(file, ce);
					}
					body.rawFile = new MultipartFileStreamImpl(new ByteArrayInputStream(file), null, null, body.contentType, file.length);
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				// TODO Error on missing length
				return null;
			}
		}
		return body;
	}
	
	@Override
	public String getContentType() {
		return contentType;
	}
	
	@Override
	public String getRawText() {
		return rawText;
	}
	
	@Override
	public MultipartFileStream getRawFile() {
		return rawFile;
	}
	
	private boolean fileRaw(ProteusHttpHeaders headers, int length, InputStream in) {
		try {
			byte[] data = in.readNBytes(length);
			List<CompressionEncoding> decompressOrder = new LinkedList<>();
			if (headers.hasHeader("Content-Encoding")) {
				List<HeaderValue> contentEncoding = headers.getHeader("Content-Encoding").getValues();
				for (int i = contentEncoding.size() - 1; i >= 0; i--) {
					HeaderValue hv = contentEncoding.get(i);
					List<QualityValue> qv = hv.getValues();
					for (int j = qv.size()-1; i >= 0; i--) {
						CompressionEncoding ce = CompressionEncoding.forName(qv.get(j).getValue());
						if (ce != CompressionEncoding.NONE) {
							if (!decompressOrder.contains(ce)) {
								decompressOrder.add(ce);
							} else {
								// TODO error
							}
						}
					}
				}
			}
			for (CompressionEncoding ce : decompressOrder) {
				data = Compressor.decompress(data, ce);
			}
			rawFile = new MultipartFileStreamImpl(new ByteArrayInputStream(data), null, null, contentType, data.length);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean contentRaw(ProteusHttpHeaders headers, int length, InputStream in) {
		try {
			byte[] data = in.readNBytes(length);
			List<CompressionEncoding> decompressOrder = new LinkedList<>();
			if (headers.hasHeader("Content-Encoding")) {
				List<HeaderValue> contentEncoding = headers.getHeader("Content-Encoding").getValues();
				for (int i = contentEncoding.size() - 1; i >= 0; i--) {
					HeaderValue hv = contentEncoding.get(i);
					List<QualityValue> qv = hv.getValues();
					for (int j = qv.size()-1; i >= 0; i--) {
						CompressionEncoding ce = CompressionEncoding.forName(qv.get(j).getValue());
						if (ce != CompressionEncoding.NONE) {
							if (!decompressOrder.contains(ce)) {
								decompressOrder.add(ce);
							} else {
								// TODO error
							}
						}
					}
				}
			}
			for (CompressionEncoding ce : decompressOrder) {
				data = Compressor.decompress(data, ce);
			}
			rawText = new String(data, StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean contentFormUrlEncoded(int length, InputStream in) {
		try {
			byte[] data = in.readNBytes(length);
			String kvs = new String(data, StandardCharsets.UTF_8);
			for (String kvt : kvs.split("&")) {
				String[] keyValue = kvt.split("=");
				if (keyValue.length != 2) {
					continue;
				}
				bodyData.putParameter(keyValue[0], URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean contentFormData(InputStream in, HeaderValue contentType, String retainName) {
		try {
			if (contentType.getParams().hasParameter("boundary")) {
				String boundary = contentType.getParams().getParameter("boundary");
				String boundaryStart = "--" + boundary;
				String boundaryEnd = boundaryStart + "--";
				boolean flag = false;
				while (!flag) {
					String boundarySeek = StreamUtils.readLine(in, true);
					if (boundarySeek.equals(boundaryStart)) {
						flag = true;
						resume = true;
					} else if (boundarySeek.equals(boundaryEnd)) {
						flag = true;
					}
				}
				while (resume) {
					flag = false;
					resume = false; // TODO: Support multiple files
					StringBuilder contentBuilder = new StringBuilder();
			        String line;
			        while (!(line = StreamUtils.readLine(in, true)).isBlank()) {
			        	contentBuilder.append(line + "\r\n");
			        }
			        
			        String[] contentLines = contentBuilder.toString().split("\r\n");
			        
					ProteusHeaderBuilder headerBuilder = ProteusHeaderBuilder.newBuilder();
			        for (int h = 0; h < contentLines.length; h++) {
			        	String header = contentLines[h];
			        	String[] headerSplit = header.split(":", 2);
			        	if (headerSplit.length == 2 && headerSplit[0].length() > 0 && headerSplit[1].length() > 0) {
			        		headerBuilder.putHeader(headerSplit[0].trim(), headerSplit[1].trim());
			        	} else {
			        		// TODO error
			        	}
			        }
			        ProteusHttpHeaders headers = headerBuilder.toHeaders();
			        
			        if (headers.hasHeader("Content-Disposition")) {
			        	HeaderValue disposition = headers.getHeader("Content-Disposition").getLast();
			        	String name = getOrRetainName(retainName, disposition);
			        	if (disposition.getValue().equals("form-data")) {
			        		if (disposition.getParams().hasParameter("filename")) {
			        			String filename = trimQuotes(disposition.getParams().getParameter("filename"));
			        			handleFile(headers, name, filename, in, boundaryStart, boundaryEnd);
			        		} else {
			        			if (name != null) {
			        				if (headers.hasHeader("Content-Type")) {
				        				contentFormData(in, headers.getHeader("Content-Type").getLast(), name);
				        			} else {
				        				String value = readParamToBoundary(in, boundaryStart, boundaryEnd);
				        				bodyData.putParameter(name, value);
				        			}
			        			}
			        		}
			        	} else if (disposition.getValue().equals("file") || disposition.getValue().equals("attachment")) {
			        		if (disposition.getParams().hasParameter("filename")) {
			        			String filename = trimQuotes(disposition.getParams().getParameter("filename"));
			        			handleFile(headers, name, filename, in, boundaryStart, boundaryEnd);
			        		}
			        	} else {
			        		// TODO error
			        	}
			        } else {
			        	// TODO error
			        }
				}
			} else {
				// TODO error
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private String getOrRetainName(String retain, HeaderValue header) {
		String name = null;
		if (retain != null) {
			name = retain;
		}
		if (header.getParams().hasParameter("name")) {
			name = trimQuotes(header.getParams().getParameter("name"));
		}
		return name;
	}
	
	private boolean handleFile(ProteusHttpHeaders headers, String name, String filename, InputStream inputStream, String boundaryStart, String boundaryEnd) throws IOException {
		if (name != null) {
			String contentType = headers.getHeader("Content-Type").getLast().getValue();
			boolean useCl = false;
			int contentLength = 0;
			if (headers.hasHeader("Content-Length")) {
				contentLength = Integer.parseInt(headers.getHeader("Content-Length").getLast().getValue());
				useCl = true;
			}
			byte[] bytes = null;
			if (headers.hasHeader("Transfer-Encoding")) {
				bytes = readFileAsChunks(headers, inputStream);
			} else {
				bytes = readFileToBoundary(useCl, contentLength, inputStream, boundaryStart, boundaryEnd);
			}
			List<CompressionEncoding> decompressOrder = new LinkedList<>();
			if (headers.hasHeader("Content-Encoding")) {
				List<HeaderValue> contentEncoding = headers.getHeader("Content-Encoding").getValues();
				for (int i = contentEncoding.size() - 1; i >= 0; i--) {
					HeaderValue hv = contentEncoding.get(i);
					List<QualityValue> qv = hv.getValues();
					for (int j = qv.size()-1; i >= 0; i--) {
						CompressionEncoding ce = CompressionEncoding.forName(qv.get(j).getValue());
						if (ce != CompressionEncoding.NONE) {
							if (!decompressOrder.contains(ce)) {
								decompressOrder.add(ce);
							} else {
								// TODO error
							}
						}
					}
				}
			}
			for (CompressionEncoding ce : decompressOrder) {
				bytes = Compressor.decompress(bytes, ce);
			}
			fileData.putParameter(name, new MultipartFileStreamImpl(new ByteArrayInputStream(bytes), name, filename, contentType, bytes.length));
		}
		return false;
	}
	
	private String readParamToBoundary(InputStream in, String boundaryStart, String boundaryEnd) throws IOException {
		StringBuilder reply = new StringBuilder();
		String line = StreamUtils.readLine(in, true);
		boolean lineAhead = false;
		while (lineAhead || (!line.equals(boundaryStart) && !line.equals(boundaryEnd))) {
			lineAhead = false;
			reply.append(line);
			line = StreamUtils.readLine(in, true);
			if (!line.equals(boundaryStart) && !line.equals(boundaryEnd)) {
				reply.append("\r\n");
				lineAhead = true;
			}
		}
		if (line.equals(boundaryStart)) {
			resume = true;
		}
		return reply.toString();
	}
	
	private byte[] readFileAsChunks(ProteusHttpHeaders headers, InputStream in) throws NumberFormatException, IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		List<CompressionEncoding> decompressOrder = new LinkedList<>();
		boolean chunked = false;
		List<HeaderValue> transferEncoding = headers.getHeader("Transfer-Encoding").getValues();
		for (int i = transferEncoding.size() - 1; i >= 0; i--) {
			HeaderValue hv = transferEncoding.get(i);
			List<QualityValue> qv = hv.getValues();
			for (int j = qv.size()-1; i >= 0; i--) {
				CompressionEncoding ce = CompressionEncoding.forName(qv.get(j).getValue());
				if (ce != CompressionEncoding.NONE) {
					if (!decompressOrder.contains(ce)) {
						if (qv.get(j).getValue().equals("chunked")) {
							chunked = true;
						} else {
							decompressOrder.add(ce);
						}
					} else {
						// TODO error
					}
				}
			}
		}
		
		if (chunked) {
			boolean read = true;
			while (read) {
				int lineLength = Integer.parseInt(StreamUtils.readLine(in, true));
				baos.write(in.readNBytes(lineLength));
				StreamUtils.consumeLine(in);
				if (lineLength == 0) {
					read = false;
				}
			}
		} else {
			// TODO error
		}
		byte[] bytes = baos.toByteArray();
		for (CompressionEncoding ce : decompressOrder) {
			bytes = Compressor.decompress(bytes, ce);
		}
		return bytes;
	}
	
	private byte[] readFileToBoundary(boolean useContentLength, int length, InputStream in, String boundaryStart, String boundaryEnd) throws IOException {
		if (!useContentLength) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] byteLine = StreamUtils.readRawLine(in, true);
			String line = new String(byteLine, StandardCharsets.UTF_8);
			boolean lineAhead = false;
			while (lineAhead || (!line.equals(boundaryStart) && !line.equals(boundaryEnd))) {
				lineAhead = false;
				baos.write(byteLine);
				byteLine = StreamUtils.readRawLine(in, true);
				line = new String(byteLine, StandardCharsets.UTF_8);
				if (!line.equals(boundaryStart) && !line.equals(boundaryEnd)) {
					baos.write('\r');
					baos.write('\n');
					lineAhead = true;
				}
			}
			if (line.equals(boundaryStart)) {
				resume = true;
			}
			return baos.toByteArray();
		} else {
			byte[] bytes = in.readNBytes(length);
			String line = StreamUtils.readLine(in, true);
			while (!line.equals(boundaryStart) && !line.equals(boundaryEnd)) {
				line = StreamUtils.readLine(in, true);
			}
			if (line.equals(boundaryStart)) {
				resume = true;
			}
			return bytes;
		}
	}
	
	private String trimQuotes(String before) {
		if (before.startsWith("\"")) {
			before = before.substring(1);
		}
		if (before.endsWith("\"")) {
			before = before.substring(0, before.length() - 1);
		}
		return before;
	}
	
}
