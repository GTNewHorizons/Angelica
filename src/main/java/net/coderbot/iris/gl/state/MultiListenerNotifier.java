package net.coderbot.iris.gl.state;

import java.util.Arrays;

public final class MultiListenerNotifier implements ValueUpdateNotifier {

    private Runnable[] listeners = new Runnable[4];
    private int count;

    @Override
    public void setListener(Runnable listener) {
        if (listener == null) {
            count = 0;
            return;
        }
        for (int i = 0; i < count; i++) {
            if (listeners[i] == listener) return;
        }
        if (count == listeners.length) listeners = Arrays.copyOf(listeners, count << 1);
        listeners[count++] = listener;
    }

    public void run() {
        final Runnable[] l = listeners;
        for (int i = 0, n = count; i < n; i++) {
            l[i].run();
        }
    }
}
