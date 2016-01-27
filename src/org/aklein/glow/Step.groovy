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
    Closure action
    Closure onSuccess
    Closure onError
    Map attributes

    Glow getGlow() {
        if (glow == null && parent != null) {
            glow = parent.glow
        }
        return glow
    }

    Step getLastChild() {
        Step last = firstChild
        while (last?.nextChild)
            last = last?.nextChild
        return last
    }

    Step getNext() {
        glow.next
    }

    Step getPrevious() {
        glow.previous
    }

    Step current() {
        glow.current
    }

    def call() {
        if (action) {
            try {
                runClosure(action)
                runClosure(onSuccess ?: Glow.CONTINUE)
            } catch (e) {
                if (!e.is(GlowException.CANCEL))
                    runClosure(onError ?: Glow.CANCEL, e)
            }
        } else {
            runClosure(Glow.DEFAULT_ACTION)
        }
    }

    private def runClosure(Closure closure, Object... args) {
        closure.delegate = getGlow()
        closure.resolveStrategy = Closure.DELEGATE_FIRST
        return closure(*args)
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
}
