package com.ghostchu.btn.sparkle.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.zip.GZIPInputStream;

// https://stackoverflow.com/a/26226246/1778299
@Slf4j
@WebFilter
@Component
public class GzipBodyDecompressFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;

        String contentEncoding = httpServletRequest.getHeader("Content-Encoding");
        if (contentEncoding != null && contentEncoding.contains("gzip")) {
            try {
                final InputStream decompressStream = StreamHelper.decompressStream(httpServletRequest.getInputStream());
                httpServletRequest = new HttpServletRequestWrapper(httpServletRequest) {
                    @Override
                    public ServletInputStream getInputStream() {
                        return new ServletInputStream() {

                            private ReadListener readListener;
                            @Override public boolean isFinished() {
                                try {
                                    return decompressStream.available() <= 0;
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override public boolean isReady() {
                                try {
                                    return decompressStream.available() > 0;
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override public void setReadListener(ReadListener readListener) {
                                this.readListener = readListener;
                            }

                            public ReadListener getReadListener() {
                                return readListener;
                            }

                            public int read() throws IOException {
                                return decompressStream.read();
                            }

                            @Override
                            public void close() throws IOException {
                                super.close();
                                decompressStream.close();
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(new InputStreamReader(decompressStream));
                    }
                };
            } catch (IOException e) {
                log.error("Error while handling the request: {}: {}", e.getClass().getName(), e.getMessage());
            }
        }
        chain.doFilter(httpServletRequest, response);
    }

    public static class StreamHelper
    {

        /**
         * Gzip magic number, fixed values in the beginning to identify the gzip
         * format <br>
         * http://www.gzip.org/zlib/rfc-gzip.html#file-format
         */
        private static final byte GZIP_ID1 = 0x1f;
        /**
         * Gzip magic number, fixed values in the beginning to identify the gzip
         * format <br>
         * http://www.gzip.org/zlib/rfc-gzip.html#file-format
         */
        private static final byte GZIP_ID2 = (byte) 0x8b;

        /**
         * Maximum uncompressed size to prevent zip bomb
         */
        private static final long MAX_UNCOMPRESSED_SIZE = 64 * 1024 * 1024; // 64MB

        /**
         * Return decompression input stream if needed.
         *
         * @param input
         *            original stream
         * @return decompression stream
         * @throws IOException
         *             exception while reading the input
         */
        public static InputStream decompressStream(InputStream input) throws IOException
        {
            PushbackInputStream pushbackInput = new PushbackInputStream(input, 2);

            byte[] signature = new byte[2];
            pushbackInput.read(signature);
            pushbackInput.unread(signature);

            if (signature[0] == GZIP_ID1 && signature[1] == GZIP_ID2)
            {
                return new MonitoredGZIPInputStream(pushbackInput, MAX_UNCOMPRESSED_SIZE);
            }
            return pushbackInput;
        }

        /**
         * A GZIPInputStream that monitors the total number of bytes read
         * and throws an exception if the size exceeds a specified limit.
         */
        private static class MonitoredGZIPInputStream extends GZIPInputStream {
            private final long maxSize;
            private long totalRead;

            public MonitoredGZIPInputStream(InputStream in, long maxSize) throws IOException {
                super(in);
                this.maxSize = maxSize;
                this.totalRead = 0;
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                int bytesRead = super.read(b, off, len);
                if (bytesRead > 0) {
                    totalRead += bytesRead;
                    if (totalRead > maxSize) {
                        throw new IOException("Uncompressed data exceeds the maximum allowed size of " + maxSize + " bytes.");
                    }
                }
                return bytesRead;
            }

            @Override
            public int read() throws IOException {
                int byteRead = super.read();
                if (byteRead != -1) {
                    totalRead++;
                    if (totalRead > maxSize) {
                        throw new IOException("Uncompressed data exceeds the maximum allowed size of " + maxSize + " bytes.");
                    }
                }
                return byteRead;
            }
        }
    }
}