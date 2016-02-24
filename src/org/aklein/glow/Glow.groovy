package org.aklein.glow

import groovy.beans.ListenerList

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

    @ListenerList
    List<StepListener> stepListeners

    Step current
    Map<String, Step> steps = [:]
    Map<String, Object> context = [:]
    Step firstChild

    Closure setup
    Closure cleanup
    Closure onCancel
    Closure onError

    int eventCount = 0

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
        eventCount = 0
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

    void jump(Step to) {
        throw new GlowException(to)
    }

    void retry(int maximum = 1) {
        throw new GlowException(maximum)
    }

    def call(Step step) {
        current = step
        return step()
    }

    void status(Object status) {
        current.setStatus(status)
    }

    Object getStatus() {
        return current.status
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

    void reset() {
        eventCount = 0
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
        if (closure.maximumNumberOfParameters < args.size())
            return closure(args)
        else
            return closure(*args)
    }

    def propertyMissing(String name) {
        if (context.containsKey(name))
            context[name]
        else
            throw new MissingPropertyException(name, Glow)
    }

    def propertyMissing(String name, def value) {
        context[name] = value
    }

    def methodMissing(String name, def args) {
        try {
            def caller = this.metaClass.getProperty(this, name)
            return caller.call(args)
        } catch(MissingPropertyException e) {
            throw new MissingMethodException(name, Glow, args, false)
        }
    }
}

abstract class StepListener implements EventListener {
    abstract void stepFinished(StepEvent event)
    void stepStarted(StepEvent event){}
    void stepSkipped(StepEvent event){}
    void stepFailed(StepEvent event){}
    void stepCanceled(StepEvent event){}
    void stepRetrying(StepEvent event){}
}

class StepEvent {
    Step source
    String path
    GlowActionType type
    Throwable exception
    Step jumpStep = null
    Object[] arguments = null
    int retryMaximum = 0
    int count

    StepEvent(Step source, Throwable exception, int count = 0) {
        this.source = source
        this.path = source.path
        this.type = GlowActionType.EXCEPTION
        this.exception = exception
        this.count = count
    }

    StepEvent(Step source, GlowException exception, int count = 0) {
        this.source = source
        this.path = source.path
        this.type = exception?.type ?: GlowActionType.START
        this.exception = exception
        this.jumpStep = exception?.jumpStep
        this.retryMaximum = exception?.maximum ?: 0
        this.count = count
    }

    StepEvent(Step source, GlowActionType type, Object[] arguments, int count = 0) {
        this.source = source
        this.path = source.path
        this.type = type
        this.arguments = arguments
        this.exception = arguments.find{ it instanceof Throwable }
        this.count = count
    }
}