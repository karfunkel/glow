package org.aklein.glow

class GlowException extends RuntimeException {
    final static GlowException CANCEL = new GlowException()

    GlowException() {
        super()
    }

    GlowException(Throwable cause) {
        super(cause)
    }

    GlowException(String message) {
        super(message)
    }

    GlowException(String message, Throwable cause) {
        super(message, cause)
    }

    GlowException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
    }
}
