package org.aklein.glow

import spock.lang.Specification

class GlowSpec extends Specification {
    GlowBuilder builder

    void setup() {
        builder = new GlowBuilder()
    }

    void "Can GlowBuilder be instantiated?"() {
        expect:
        new GlowBuilder() != null
    }

    void "Are constants available?"() {
        expect:
        builder.build { CANCEL } == Glow.CANCEL
        builder.build { CONTINUE } == Glow.CONTINUE
    }

    void "Is GlowFactory working?"() {
        expect:
        builder.build { glow() } instanceof Glow
    }

    void "Is StepFactory working?"() {
        when:
        Glow glow = builder.glow {
            step {
                step(onError: CANCEL)
                step {
                    onError { 'Blubb' }
                }
            }
            step {
                step() {
                    step()
                    step(action: { 'A' }) {
                        action { 'B' }
                        action { 'C' }
                    }
                }
            }
            step {
                step('hello', name: 'Blah') {
                    step()
                    step()
                }
                step {
                    step()
                    step()
                }
            }
        }

        def steps = glow.steps

        then:
        steps.size() == 3
        steps.iterator()[0].key == '_0'
        steps.iterator()[1].key == '_1'
        steps._0.id == '_0'
        steps._1.id == '_1'
        steps._2.firstChild.id == 'hello'
        steps._2.hello.name == 'Blah'
        steps._0._0.onError.is Glow.CANCEL
        steps._0._1.onError() == 'Blubb'
        steps._1._0._1.actions.size() == 3
        steps._1._0._1.actions.collect {it()} == ['A','B','C']

    }

    void "Does Eventbubbling work?"() {
        when:
        def msg = []
        println msg.dump()
        Glow glow = builder.glow {
            onError { msg << 'err_glow' }
            setup { msg << 'setup_glow' }
            cleanup { msg << 'cleanup_glow' }
            onSuccess { msg << 'suc_glow' }
            step('a') {
                onError { msg << "err_$bubble.path" }
                setup { msg << "setup_$bubble.path" }
                cleanup { msg << "cleanup_$bubble.path" }
                onSuccess { msg << "suc_$bubble.path" }
                step('aa') {
                    onError { msg << "err_$bubble.path" }
                    setup { msg << "setup_$bubble.path" }
                    cleanup { msg << "cleanup_$bubble.path" }
                    onSuccess { msg << "suc_$bubble.path" }
                    step('aaa') {
                        onError { msg << "err_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                        onSuccess { msg << "suc_$bubble.path" }
                    }
                    step('aab') {
                        action {
                            throw new RuntimeException('Test')
                        }
                        onError { msg << "err_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                        onSuccess { msg << "suc_$bubble.path" }
                    }
                }
            }
        }
        glow.start()

        // TODO: Think onSuccess through
        then:
        msg == ['setup_a', 'suc_a', 'setup_a.aa', 'suc_a.aa', 'setup_a.aa.aaa', 'suc_a.aa.aaa', 'setup_a.aa.aab', 'err_a.aa.aab', 'err_a.aa', 'err_a', 'err_glow', 'cleanup_a.aa.aab', 'cleanup_a.aa.aaa', 'cleanup_a.aa', 'cleanup_a']
    }
}
