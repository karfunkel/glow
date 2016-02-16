package org.aklein.glow


class GlowException extends RuntimeException {
    final static GlowException CANCEL = new GlowException(GlowActionType.CANCEL)
    final static GlowException NEXT = new GlowException(GlowActionType.NEXT)
    final static GlowException PREVIOUS = new GlowException(GlowActionType.PREVIOUS)
    final static GlowException NEXT_SIBLING = new GlowException(GlowActionType.NEXT_SIBLING)
    final static GlowException PREVIOUS_SIBLING = new GlowException(GlowActionType.PREVIOUS_SIBLING)

    Step jumpStep
    int maximum = 0

    GlowActionType type

    GlowException() {
        super()
        type = GlowActionType.EXCEPTION
    }

    GlowException(Throwable cause) {
        super(cause)
        type = GlowActionType.EXCEPTION
    }

    GlowException(String message) {
        super(message)
        this.type = GlowActionType.EXCEPTION
    }

    GlowException(GlowActionType type) {
        super(type.toString())
        this.type = type
    }

    GlowException(int maximum) {
        this(GlowActionType.RETRY)
        this.maximum = maximum
    }

    GlowException(Step jumpStep) {
        this(GlowActionType.JUMP)
        this.jumpStep = jumpStep
    }

    GlowException(String message, Throwable cause) {
        super(message, cause)
        type = GlowActionType.EXCEPTION
    }

    GlowException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
        type = GlowActionType.EXCEPTION
    }
}



