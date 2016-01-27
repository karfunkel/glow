package org.aklein.glow

class GlowBuilder extends FactoryBuilderSupport {

    public GlowBuilder(boolean init = true) {
        super(init)
    }

    def registerFactories() {
        registerFactory("glow", new GlowFactory())
        registerFactory("step", new StepFactory())
        registerFactory("setup", new ClosureFactory("setup"))
        registerFactory("onError", new ClosureFactory("onError"))
        registerFactory("onSuccess", new ClosureFactory("onSuccess"))
        registerFactory("action", new ClosureFactory("action"))
        registerFactory("cleanup", new ClosureFactory("cleanup"))
    }

    def registerConstants() {
        registerExplicitProperty('CONTINUE', { return Glow.CONTINUE }, {})
        registerExplicitProperty('CANCEL', { return Glow.CANCEL }, {})
    }

    public Object build(Closure c) {
        c.setDelegate(this)
        return c.call()
    }
}

// Factories
class GlowFactory extends AbstractFactory {
    @Override
    Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
        return new Glow()
    }

    @Override
    void onNodeCompleted( FactoryBuilderSupport builder, Object parent, Object node ) {
        Glow glow = node
        glow.current = glow.firstChild
        Step lastStep = glow.firstChild
        Step nextStep
        while(nextStep = lastStep?.next) {
            nextStep.previousStep = lastStep
            lastStep = nextStep
        }
    }
}

class StepFactory extends AbstractFactory {
    @Override
    Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
        String id = attributes.remove('id') ?: value
        Closure action = attributes.remove('action')
        Closure onSuccess = attributes.remove('onSuccess')
        Closure onError = attributes.remove('onError')
        Step step = new Step(attributes: [*:attributes])
        if (id) step.id = id
        if (action) step.action = action
        if (onSuccess) step.onSuccess = onSuccess
        if (onError) step.onError = onError
        attributes.clear()
        return step
    }

    @Override
    void setParent(FactoryBuilderSupport builder, Object parent, Object child) {
        if (parent instanceof Step) {
            Step step = child
            if (step.id == null) {
                step.id = "_${parent.children.size()}"
            }
            step.glow = parent.glow

            Step lastChild = parent?.lastChild
            step.previousSibling = lastChild
            lastChild?.nextSibling = step

            parent.children[step.id] = step
            step.parent = parent
            if (!parent.firstChild)
                parent.firstChild = step
        } else {
            if (parent instanceof Glow) {
                Step step = child
                if (step.id == null) {
                    step.id = "_${parent.steps.size()}"
                }
                step.glow = parent

                Step lastChild = parent?.lastChild
                step.previousSibling = lastChild
                lastChild?.nextSibling = step

                parent.steps[step.id] = step
                step.parent = null
                if (!parent.firstChild)
                    parent.firstChild = step
            }
        }
    }
}

class ClosureFactory extends AbstractFactory {
    String event

    ClosureFactory(String event) {
        this.event = event
    }

    @Override
    boolean isHandlesNodeChildren() {
        return true
    }

    @Override
    boolean onNodeChildren(FactoryBuilderSupport builder, Object node, Closure childContent) {
        node.closure = childContent
        return false
    }

    @Override
    Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
        [:]
    }

    @Override
    void setParent(FactoryBuilderSupport builder, Object parent, Object child) {
        if (parent instanceof Step) {
            if(!parent.hasProperty(event))
                parent.actions << child.closure
            else
                parent[event] = child.closure
        }
    }
}



