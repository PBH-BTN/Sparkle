package com.ghostchu.btn.sparkle.spring.filter;

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
                log.error("error while handling the request", e);
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
                return new GZIPInputStream(pushbackInput);
            }
            return pushbackInput;
        }
    }
}