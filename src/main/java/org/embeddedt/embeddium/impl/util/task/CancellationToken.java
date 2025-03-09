package org.embeddedt.embeddium.impl.util.task;

public interface CancellationToken {
    boolean isCancelled();

    void setCancelled();
}
