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
        steps._1._0._1.actions.collect { it() } == ['A', 'B', 'C']

    }

    void "Does Eventbubbling work?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            onError { msg << 'err_glow' }
            setup { msg << 'setup_glow' }
            cleanup { msg << 'cleanup_glow' }
            step('a') {
                onError { msg << "err_$bubble.path" }
                setup { msg << "setup_$bubble.path" }
                cleanup { msg << "cleanup_$bubble.path" }
                step('aa') {
                    onError { msg << "err_$bubble.path" }
                    setup { msg << "setup_$bubble.path" }
                    cleanup { msg << "cleanup_$bubble.path" }
                    step('aaa') {
                        onError { msg << "err_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                        action {
                            msg << "action_$bubble.path"
                            msg << "action_$current.path"
                        }
                    }
                    step('aab') {
                        action {
                            msg << "action_$bubble.path"
                            throw new RuntimeException('Test')
                        }
                        onError { msg << "err_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                    }
                }
            }
        }
        glow.start()

        then:
        msg == ['setup_a', 'cleanup_a', 'setup_a.aa', 'cleanup_a.aa', 'setup_a.aa.aaa', 'action_a.aa.aaa', 'action_a.aa.aaa', 'cleanup_a.aa.aaa', 'setup_a.aa.aab', 'action_a.aa.aab', 'err_a.aa.aab', 'err_a.aa', 'err_a', 'err_glow', 'cleanup_a.aa.aab']
    }

    void "Do exceptions in onError be handled correct?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            onError { msg << 'err_glow' }
            step('a') {
                onError { msg << "err_$bubble.path" }
                step('aa') {
                    onError { msg << "err_$bubble.path" }
                    step('aaa') {
                        onError { msg << "err_$bubble.path" }
                        action {
                            msg << "action_$bubble.path"
                        }
                    }
                    step('aab') {
                        action {
                            msg << "action_$bubble.path"
                            throw new RuntimeException('Test')
                        }
                        onError {
                            msg << "err_$bubble.path"
                            throw new RuntimeException('NULL')
                        }
                    }
                }
            }
        }
        glow.start()

        then:
        def ex = thrown(RuntimeException)
        ex.message == 'NULL'
        msg == ['action_a.aa.aaa', 'action_a.aa.aab', 'err_a.aa.aab']
    }

    void "Does cancel work?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            onCancel { msg << 'cancel_glow' }
            setup { msg << 'setup_glow' }
            cleanup { msg << 'cleanup_glow' }
            step('a') {
                onCancel { msg << "cancel_$bubble.path" }
                setup { msg << "setup_$bubble.path" }
                cleanup { msg << "cleanup_$bubble.path" }
                step('aa') {
                    onCancel { msg << "cancel_$bubble.path" }
                    setup { msg << "setup_$bubble.path" }
                    cleanup { msg << "cleanup_$bubble.path" }
                    step('aaa') {
                        onCancel { msg << "cancel_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                        action {
                            msg << "action_$bubble.path"
                        }
                    }
                    step('aab') {
                        action {
                            msg << "action_$bubble.path"
                            cancel()
                        }
                        onCancel { msg << "cancel_$bubble.path" }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                    }
                }
            }
        }
        glow.start()

        then:
        msg == ['setup_a', 'cleanup_a', 'setup_a.aa', 'cleanup_a.aa', 'setup_a.aa.aaa', 'action_a.aa.aaa', 'cleanup_a.aa.aaa', 'setup_a.aa.aab', 'action_a.aa.aab', 'cancel_a.aa.aab', 'cancel_a.aa', 'cancel_a', 'cancel_glow', 'cleanup_a.aa.aab']
    }

    void "Do multiple steps progress in correct order automatically using closure notation?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            step {
                setup { msg << 'setup1' }
                action { msg << 'action1' }
                cleanup { msg << 'cleanup1' }
            }
            step {
                setup { msg << 'setup2' }
                action { msg << 'action2' }
                cleanup { msg << 'cleanup2' }
            }
            step {
                setup { msg << 'setup3' }
                action { msg << 'action3' }
                cleanup { msg << 'cleanup3' }
            }
        }
        glow.start()
        then:
        msg == ['setup1', 'action1', 'cleanup1', 'setup2', 'action2', 'cleanup2', 'setup3', 'action3', 'cleanup3']
    }

    void "Do multiple steps progress in correct order automatically using paren notation?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            step(
                    setup: { msg << 'setup1' },
                    action: { msg << 'action1' },
                    cleanup: { msg << 'cleanup1' }
            )
            step(
                    setup: { msg << 'setup2' },
                    action: { msg << 'action2' },
                    cleanup: { msg << 'cleanup2' }
            )
        }
        glow.start()
        then:
        msg == ['setup1', 'action1', 'cleanup1', 'setup2', 'action2', 'cleanup2']
    }

    void "Do all notations function the same?"() {

        when:

        Glow glow = builder.glow() {
            step(
                    action: {
                        println "paren: owner: ${owner.getClass()}"
                        println "paren: delegate: ${delegate.getClass()}"
                    }
            )
            step {
                action {
                    println "closure: owner: ${owner.getClass()}"
                    println "closure: delegate: ${delegate.getClass()}"
                }
            }
        }
        glow.start()

        then:
        true
    }

    void "Does retry work?"() {

        when:
        def counter = 0

        Glow glow = builder.glow() {
            step {
                action {
                    counter ++
                    throw new RuntimeException('Test')
                }
                onError {
                    retry(2)
                }
            }
        }
        glow.start()

        then:
        counter == 3
    }
}