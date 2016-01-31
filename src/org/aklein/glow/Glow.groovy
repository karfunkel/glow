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
    Map<String, Object> context = [:]
    Step firstChild

    Closure setup
    Closure cleanup
    Closure onCancel
    Closure onError

    Glow(Map<String, Object> context = [:]) {
        this.context = context
    }

    Glow(Binding context) {
        this.context = context.variables
    }

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
        current.call()
    }

    void cancel() {
        throw GlowException.CANCEL
    }

    void next() {
        throw GlowException.NEXT
    }

    void previous() {
        throw GlowException.PREVIOUS
    }

    void nextSibling() {
        throw GlowException.NEXT_SIBLING
    }

    void previousSibling() {
        throw GlowException.PREVIOUS_SIBLING
    }

    void previousSibling() {
        throw GlowException.PREVIOUS_SIBLING
    }

    void jump(Step to) {
        throw new GlowException(jumpStep: to)
    }

    def call(Step step) {
        current = step
        return step()
    }

    Step getNextStep() {
        if (!current)
            throw new GlowException('Glow has not been started')
        else {
            if (current.firstChild)
                return current.firstChild
            else {
                def cur = current
                while (!cur.nextSibling) {
                    if (!cur.parent)
                        return null
                    cur = cur.parent
                }
                return cur.nextSibling
            }
        }
    }

    Step getPreviousStep() {
        return current.previousStep
    }

    Step getNextSiblingStep() {
        return current.nextSibling
    }

    Step getPreviousSiblingStep() {
        return current.previousSibling
    }

    void retry() {
        current()
    }

    void reset() {
        current = null
    }

    boolean onEvent(String event, Closure defaultAction = null, Object... args) {
        Closure closure = this."$event" ?: defaultAction
        context.remove('bubble')
        if (closure)
            runClosure(closure, args)
        return false
    }

    private def runClosure(Closure closure, Object... args) {
        closure.delegate = this
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        return closure(*args)
    }

    def propertyMissing(String name) {
        if(context.containsKey(name))
            context[name]
        else
            throw new MissingPropertyException(name, Glow)
    }

    def propertyMissing(String name, def value) {
        context[name] = value
    }

    def methodMissing(String name, def args) {
        def caller = this."$name"
        return caller?.call(args)
    }
}