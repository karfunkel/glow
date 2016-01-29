package org.aklein.glow

class Step {
    String id
    Step parent
    Glow glow
    Step firstChild
    Step nextSibling
    Step previousSibling
    Step previousStep
    Map<String, Step> children = [:]
    List<Closure> actions = []

    Closure setup
    Closure cleanup
    Closure onSuccess
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
        if (!actionList)
            actionList << Glow.DEFAULT_ACTION

        onEvent('setup', false)
        try {
            actionList.each { action ->
                runClosure(action)
            }
            onEvent('onSuccess', false)
        } catch (e) {
            stepControl(e)
        } finally {
            // TODO: Handle exception in finally
            onEvent('cleanup', false)
        }
    }

    def stepControl(Exception e) {
        try {
            switch (e) {
                case GlowException.NEXT:
                    onEvent('onSuccess', false)
                    getGlow().call(getGlow().nextStep)
                    break
                case GlowException.PREVIOUS:
                    onEvent('onSuccess', false)
                    getGlow().call(getGlow().previousStep)
                    break
                case GlowException.NEXT_SIBLING:
                    onEvent('onSuccess', false)
                    getGlow().call(getGlow().nextSiblingStep)
                    break
                case GlowException.PREVIOUS_SIBLING:
                    onEvent('onSuccess', false)
                    getGlow().call(getGlow().previousSiblingStep)
                    break
                case GlowException.CANCEL:
                    onEvent('onCancel')
                    break
                default:
                    onEvent('onError', e)
            }
        } catch(ex) {
            stepControl(ex)
        }
    }

    private def runClosure(Closure closure, Object... args) {
        if(closure) {
            closure.delegate = getGlow()
            closure.resolveStrategy = Closure.DELEGATE_FIRST
            return closure(*args)
        }
        return null
    }

    boolean onEvent(String event, boolean bubbling = true,  Object... args) {
        Closure closure = this."$event"
        boolean bubble = true
        def oldBubble = getGlow().bubble
        getGlow().bubble = this
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
            getGlow().bubble = oldBubble
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
