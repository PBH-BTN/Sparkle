package com.ghostchu.btn.sparkle.util;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class LargeFileReader {
    private final List<MappedByteBuffer> buffers = new ArrayList<>();
    private final long fileSize;
    private final int chunkSize = Integer.MAX_VALUE; // 每个 MappedByteBuffer 的最大大小
    private long cursor = 0;

    public LargeFileReader(FileChannel channel) throws IOException {
        this.fileSize = channel.size();
        long offset = 0;
        while (offset < fileSize) {
            long size = Math.min(chunkSize, fileSize - offset);
            buffers.add(channel.map(FileChannel.MapMode.READ_ONLY, offset, size));
            offset += size;
        }
    }

    // 读取指定长度的数据并移动光标
    public byte[] read(int length) throws IOException {
        if (cursor >= fileSize) {
            return null; // 已到文件末尾
        }
        int remaining = (int) Math.min(length, fileSize - cursor);
        byte[] data = new byte[remaining];
        int bytesRead = 0;

        while (bytesRead < remaining) {
            MappedByteBuffer buffer = getBufferForOffset(cursor);
            int bufferOffset = (int) (cursor % chunkSize);
            buffer.position(bufferOffset);
            int toRead = Math.min(buffer.remaining(), remaining - bytesRead);
            buffer.get(data, bytesRead, toRead);
            bytesRead += toRead;
            cursor += toRead;
        }
        return data;
    }

    // 从指定偏移量读取指定长度的数据，不影响光标
    public byte[] rawRead(long offset, int length) {
        if (offset >= fileSize) {
            throw new IllegalArgumentException("Offset exceeds file size");
        }
        int remaining = (int) Math.min(length, fileSize - offset);
        byte[] data = new byte[remaining];
        int bytesRead = 0;

        while (bytesRead < remaining) {
            MappedByteBuffer buffer = getBufferForOffset(offset);
            int bufferOffset = (int) (offset % chunkSize);
            buffer.position(bufferOffset);
            int toRead = Math.min(buffer.remaining(), remaining - bytesRead);
            buffer.get(data, bytesRead, toRead);
            bytesRead += toRead;
            offset += toRead;
        }
        return data;
    }

    // 根据偏移量获取对应的 MappedByteBuffer
    private MappedByteBuffer getBufferForOffset(long offset) {
        int bufferIndex = (int) (offset / chunkSize);
        return buffers.get(bufferIndex);
    }

    public boolean hasNext() {
        return cursor < fileSize;
    }

    public void close() {
        buffers.clear(); // 清理所有缓冲区
    }

    public long getCursor() {
        return cursor;
    }

    public void setCursor(long position) {
        if (position < 0 || position > fileSize) {
            throw new IllegalArgumentException("Invalid cursor position");
        }
        this.cursor = position;
    }

    public long available() {
        return fileSize - cursor;
    }


    public long getFileSize() {
        return fileSize;
    }
}
