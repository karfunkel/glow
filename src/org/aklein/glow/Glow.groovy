package org.aklein.glow

class Glow {
    final static Closure CONTINUE = {
        next()
    }

    final static Closure CANCEL = {
        cancel()
    }

    final static Closure DEFAULT_ACTION = {
        next()
    }

    Step current
    Map<String, Step> steps = [:]
    Step firstChild

    Step getLastChild() {
        Step last = firstChild
        while (last?.nextChild)
            last = last?.nextChild
        return last
    }

    void start() {
        if (!steps) {
            throw new GlowException('Cannot start a glow without steps')
        }
        current = firstChild
        current()
    }

    void cancel() {
        throw GlowException.CANCEL
    }

    Step getNext() {
        if (!current)
            throw new GlowException('Glow has not been started')
        else {
            if (current.firstChild)
                current = current.firstChild
            else {
                while (!current.nextSibling) {
                    current = current.parent
                    if (!current)
                        return null
                }
                current = current.nextSibling
            }
        }
        return current
    }

    Step getPrevious() {
        return current.previousStep
    }

    void retry() {
        current()
    }

    void reset() {
        current = null
    }

    def propertyMissing(String name) {
        return steps[name]
    }

    def propertyMissing(String name, def arg) {
        throw new UnsupportedOperationException('You may not add steps manually')
    }
}