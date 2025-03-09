package org.embeddedt.embeddium.impl.util.iterator;

public interface ByteIterator {
    boolean hasNext();

    int nextByteAsInt();
}
