package com.gtnewhorizons.angelica.experimental.surround;

final class SurroundRejection extends RuntimeException {

    private static final long serialVersionUID = 1L;

    SurroundRejection(String message) {
        super(message, null, false, false);
    }
}
