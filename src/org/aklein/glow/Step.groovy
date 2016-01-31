package org.aklein.glow

class Step {
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

    Closure setup
    Closure cleanup
    Closure onCancel
    Closure onError
    Map attributes

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
        def actionList = [] + actions
        if (autoNext) {
            actionList << Glow.DEFAULT_ACTION
            autoNext = false
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

    Step stepControl(Exception e, boolean rethrow = false) {
        try {
            switch (e) {
                case GlowException.NEXT:
                    return getGlow().nextStep
                case GlowException.PREVIOUS:
                    return getGlow().previousStep
                case GlowException.NEXT_SIBLING:
                    return getGlow().nextSiblingStep
                case GlowException.PREVIOUS_SIBLING:
                    return getGlow().previousSiblingStep
                case GlowException.CANCEL:
                    onEvent('onCancel')
                    return null
                default:
                    if (rethrow)
                        throw e
                    try {
                        onEvent('onError', e)
                        return null
                    } catch (ex) {
                        return stepControl(ex, true)
                    }
            }
        } catch (ex) {
            throw ex
        } finally {
            if(!rethrow)
                onEvent('cleanup', false)
        }
    }

    private def runClosure(Closure closure, Object... args) {
        if (closure) {
            closure.delegate = getGlow()
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            def oldBubble = getGlow().context.bubble
            getGlow().context.bubble = this
            def result = closure(*args)
            getGlow().context.bubble = oldBubble
            return result
        }
        return null
    }

    boolean onEvent(String event, boolean bubbling = true, Object... args) {
        Closure closure = this."$event"
        boolean bubble = true
        def oldBubble = getGlow().context.bubble
        getGlow().context.bubble = this
        try {
            if (closure)
                bubble = runClosure(closure, args)
            if (bubbling && bubble) {
                if (parent)
                    return parent.onEvent(event, args)
                else
                    return getGlow().onEvent(event, args)
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
}
