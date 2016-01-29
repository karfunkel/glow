package org.aklein.glow

class GlowException extends RuntimeException {
    final static GlowException CANCEL = new GlowException('CANCEL')
    final static GlowException NEXT = new GlowException('NEXT')
    final static GlowException PREVIOUS = new GlowException('PREVIOUS')
    final static GlowException NEXT_SIBLING = new GlowException('NEXT_SIBLING')
    final static GlowException PREVIOUS_SIBLING = new GlowException('PREVIOUS_SIBLING')

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
