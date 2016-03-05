package org.aklein.glow

class Step {
    static final String CANCEL_REASON_MANUAL = 'MANUAL'
    static final String CANCEL_REASON_RETRY = 'RETRY'
    static final String CANCEL_REASON_ERROR = 'ERROR'

    String id
    Step parent
    Glow glow
    boolean autoNext
    Step firstChild
    Step nextSibling
    Step previousSibling
    Step previousStep
    Map<String, Step> children = [:]
    List<Closure> actions = []
    int retriesLeft = -1

    Closure setup
    Closure cleanup
    Closure onCancel
    Closure onError
    Map attributes
    Map<String, Map<String, Object>> $info = [:]

    private String _status
    private Map<String, Object> currentInfo

    Glow getGlow() {
        if (glow == null && parent != null) {
            glow = parent.glow
        }
        return glow
    }

    String getPath() {
        return parent ? "${parent.getPath()}.$id" : id
    }

    Step getLastChild() {
        Step last = firstChild
        while (last?.nextChild)
            last = last?.nextChild
        return last
    }

    Step getLastSibling() {
        Step last = this
        while (last?.nextSibling)
            last = last?.nextSibling
        return last
    }

    Step getNext() {
        glow.nextStep
    }

    Step getPrevious() {
        glow.previousStep
    }

    Step current() {
        glow.current
    }

    def call(Object... args) {
        glow.fireStepStarted(new StepEvent(this, null, glow.eventCount))
        def actionList = [] + actions
        if (autoNext) {
            Closure cls = Glow.DEFAULT_ACTION.clone()
            cls.delegate = [name: 'action']
            actionList << cls
        }
        try {
            onEvent('setup', false)
            actionList.each { action ->
                runClosure(action)
            }
        } catch (e) {
            Step next = stepControl(e)
            if (next)
                getGlow().call(next)
        }
    }

    Step stepControl(Exception e, boolean rethrow = false, Exception lastException = null) {
        try {
            switch (e) {
                case GlowException.NEXT:
                    getGlow().current.retriesLeft = -1
                    onEvent('cleanup', false)
                    return getGlow().nextStep
                case GlowException.PREVIOUS:
                    getGlow().current.retriesLeft = -1
                    onEvent('cleanup', false)
                    return getGlow().previousStep
                case GlowException.NEXT_SIBLING:
                    getGlow().current.retriesLeft = -1
                    onEvent('cleanup', false)
                    return getGlow().nextSiblingStep
                case GlowException.PREVIOUS_SIBLING:
                    getGlow().current.retriesLeft = -1
                    onEvent('cleanup', false)
                    return getGlow().previousSiblingStep
                case GlowException.CANCEL:
                    getGlow().current.retriesLeft = -1
                    onEvent('onCancel', CANCEL_REASON_MANUAL)
                    onEvent('cleanup', false)
                    return null
                case { it instanceof GlowException && it.type == GlowActionType.JUMP }:
                    getGlow().current.retriesLeft = -1
                    onEvent('cleanup', false)
                    return e.jumpStep
                case { it instanceof GlowException && it.type == GlowActionType.RETRY }:
                    glow.fireStepRetrying(new StepEvent(this, e, glow.eventCount))
                    Step cur = getGlow().current
                    if (cur.retriesLeft == -1) { // First time
                        cur.retriesLeft = e.maximum
                        cur.retriesLeft--
                        return cur
                    } else if (cur.retriesLeft == 0) { // Last time
                        cur.retriesLeft--
                        try {
                            onEvent('onError', lastException)
                            return null
                        } catch (ex) {
                            if (ex instanceof GlowException && ex.maximum) {  // Retry
                                onEvent('onCancel', CANCEL_REASON_RETRY, lastException)
                                onEvent('cleanup', false)
                                return null
                            } else
                                onEvent('cleanup', false)
                            return stepControl(ex, true)
                        }
                    } else {
                        cur.retriesLeft--
                        return cur
                    }
                default:
                    if (rethrow)
                        throw e
                    try {
                        onEvent('onError', e)
                        onEvent('onCancel', CANCEL_REASON_ERROR, e)
                        onEvent('cleanup', false)
                        return null
                    } catch (ex) {
                        return stepControl(ex, true, e)
                    }
            }
        } catch (ex) {
            throw ex
        } finally {
            glow.fireStepFinished(new StepEvent(this, e, glow.eventCount++))
        }
    }

    Object getStatus() {
        return _status
    }

    void setStatus(Object status) {
        this._status = status
        currentInfo?.status = status
    }

    private def runClosure(Closure closure, Object... args) {
        if (closure) {
            def meta = closure.delegate
            def oldBubble = getGlow().context.bubble

            boolean isRetry = retriesLeft >= 0

            String key = meta.name
            def info = this.$info[key]
            if (info == null) {
                info = [name: key]
                this.$info[key] = info
            } else if (info instanceof List) {
                def list = info
                if (isRetry) {
                    info = list.last()
                } else {
                    info = [name: key]
                    list << info
                }
            } else {
                if (!isRetry) {
                    def old = info
                    info = [name: key]
                    this.$info[key] = [old, info]
                }
            }
            currentInfo = info

            try {
                closure.delegate = getGlow()
                closure.resolveStrategy = Closure.DELEGATE_FIRST
                getGlow().context.bubble = this
                currentInfo.remove('exception')
                currentInfo.start = new Date()
                if (args) {
                    if (currentInfo.name == 'onError')
                        currentInfo.exception = args[0]
                    else
                        currentInfo.argument = args.size() == 1 ? args[0] : [] + (args as List)
                }
                def result
                if (closure.maximumNumberOfParameters < args.size())
                    result = closure(args)
                else
                    result = closure(*args)
                currentInfo.end = new Date()
                return result
            } catch (e) {
                currentInfo.end = new Date()
                switch (e) {
                    case GlowException.NEXT:
                    case GlowException.PREVIOUS:
                    case GlowException.NEXT_SIBLING:
                    case GlowException.PREVIOUS_SIBLING:
                    case GlowException.CANCEL:
                    case { it instanceof GlowException && it.type == GlowActionType.JUMP }:
                    case { it instanceof GlowException && it.type == GlowActionType.RETRY }:
                        break
                    default:
                        currentInfo.exception = e
                }
                throw e
            } finally {
                currentInfo.duration = currentInfo.end.time - currentInfo.start.time
                getGlow().context.bubble = oldBubble
                closure.delegate = meta
            }
        }
        return null
    }

    boolean onEvent(String event, boolean bubbling = true, Object... args) {
        Closure closure = this."$event"
        boolean bubble = true
        def oldBubble = getGlow().context.bubble
        getGlow().context.bubble = this
        try {
            if (closure) {
                if (event == 'onCancel')
                    glow.fireStepCanceled(new StepEvent(this, GlowActionType.CANCEL, args, glow.eventCount))
                else if (event == 'onError')
                    glow.fireStepFailed(new StepEvent(this, GlowActionType.EXCEPTION, args, glow.eventCount))
                def result = runClosure(closure, args)
                if (result == null)
                    bubble = true
                else
                    bubble = result
            }
            if (bubbling && bubble) {
                if (parent)
                    return parent.onEvent(event, args)
                else if (event == 'onError') {
                    def result = getGlow().onEvent(event, args)
                    if (result == null || result)
                        throw args[0]
                } else {
                    return getGlow().onEvent(event, args)
                }
            }
            return false
        } finally {
            getGlow().context.bubble = oldBubble
        }
    }

    def propertyMissing(String name) {
        def attr = attributes[name]
        def child = children[name]
        if (attr != null && child != null)
            throw new GlowException("Step '$step.path' has both, child and attribute.")
        return attr ?: child
    }

    def propertyMissing(String name, def arg) {
        attributes[name] = arg
    }

    def methodMissing(String name, def args) {
        def caller = this."$name"
        return caller?.call(args)
    }

    String toString() {
        return "Step(${this.path})"
    }
}
