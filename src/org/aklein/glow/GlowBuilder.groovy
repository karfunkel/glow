package org.aklein.glow

class GlowBuilder extends FactoryBuilderSupport {

    public GlowBuilder(boolean init = true) {
        super(init)
    }

    def registerFactories() {
        registerFactory("glow", new GlowFactory())
        registerFactory("step", new StepFactory())
        registerFactory("setup", new ClosureFactory("setup"))
        registerFactory("cleanup", new ClosureFactory("cleanup"))
        registerFactory("onError", new ClosureFactory("onError"))
        registerFactory("onCancel", new ClosureFactory("onCancel"))
        registerFactory("action", new ClosureFactory("action"))
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
        Closure setup = attributes.remove('setup')
        Closure cleanup = attributes.remove('cleanup')
        Closure onError = attributes.remove('onError')
        Closure onCancel = attributes.remove('onCancel')
        Glow glow = new Glow()
        if (setup) glow.setup = setup
        if (cleanup) glow.cleanup = cleanup
        if (onError) glow.onError = onError
        if (onCancel) glow.onCancel = onCancel
        return glow
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
            glow.current = glow.nextStep
        }
        glow.reset()
    }
}

class StepFactory extends AbstractFactory {
    @Override
    Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
        String id = attributes.remove('id') ?: value
        Closure action = attributes.remove('action')
        Closure setup = attributes.remove('setup')
        Closure cleanup = attributes.remove('cleanup')
        Closure onError = attributes.remove('onError')
        Closure onCancel = attributes.remove('onCancel')
        Boolean autoNext = attributes.remove('autoNext')
        autoNext = autoNext == null ? true : autoNext
        Step step = new Step(attributes: [*:attributes])
        step.autoNext = autoNext
        if (id) step.id = id
        if (action) step.actions << action
        if (setup) step.setup = setup
        if (cleanup) step.cleanup = cleanup
        if (onError) step.onError = onError
        if (onCancel) step.onCancel = onCancel
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
            lastChild?.lastSibling?.nextSibling = step

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
                lastChild?.lastSibling?.nextSibling = step

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
        def parent = builder.current
        if (parent instanceof Step) {
            if(!parent.hasProperty(event))
                parent.actions << childContent
            else
                parent[event] = childContent
        } else if (parent instanceof Glow) {
            if(parent.hasProperty(event))
                parent[event] = childContent
        }
        return false
    }

    @Override
    Object newInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) throws InstantiationException, IllegalAccessException {
        [:]
    }
}



