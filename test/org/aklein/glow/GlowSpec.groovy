package org.aklein.glow

import spock.lang.Ignore
import spock.lang.Unroll
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
            onError {
                msg << 'err_glow'
                return false
            }
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
                onCancel {
                    throw new RuntimeException('NULL')
                }
                action {
                    counter++
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
        glow.steps._0.$info.size() == 3
        glow.steps._0.$info.action instanceof Map
        glow.steps._0.$info.onError instanceof List
        glow.steps._0.$info.onError.size() == 2
        glow.steps._0.$info.onError[0].exception.message == 'Test'
        glow.steps._0.$info.onError[1].exception.message == 'Test'
        glow.steps._0.$info.onCancel.argument[0] == 'RETRY'
        glow.steps._0.$info.onCancel.argument[1].message == 'Test'
        def ex = thrown(RuntimeException)
        ex.message == 'NULL'
    }

    @Unroll
    void "Does \$info work for #path #name?"() {
        when:
        def msg = []
        def count = 0
        Glow glow = builder.glow {
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
                            status 'Test'
                            msg << "action_$bubble.path"
                        }
                    }
                    step('aab') {
                        action {
                            msg << "action_$bubble.path"
                            if (count < 2) {
                                count++
                                throw new RuntimeException('NULL')
                            }
                        }
                        onError {
                            retry(3)
                        }
                        setup { msg << "setup_$bubble.path" }
                        cleanup { msg << "cleanup_$bubble.path" }
                    }
                    step('aac') {
                        action {
                            msg << "action_$bubble.path"
                            status 'OK'
                            throw new RuntimeException('NULL')
                        }
                        onError {
                            retry(3)
                        }
                        onCancel {
                            status 'Failed'
                            msg << "cancel_$bubble.path"
                        }
                        setup {
                            msg << "setup_$bubble.path"
                        }
                        cleanup {
                            msg << "cleanup_$bubble.path"
                        }
                    }
                }
            }
            step('b') {
                onCancel { msg << "cancel_$bubble.path" }
                step('ba') {
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
        glow.start()

        then:
        def info = path.split(/\./).inject(glow.steps) { parent, name -> return parent."$name" }
        testInfo(info.$info, name, keys, _status, argument, exception, message)

        where:
        path       | name       | keys                                                                                                   | _status        | argument                                | exception        | message
        'a'        | 'setup'    | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a'        | 'cleanup'  | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a'        | 'action'   | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa'     | 'setup'    | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa'     | 'cleanup'  | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa'     | 'action'   | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aaa' | 'action'   | [['name', 'start', 'end', 'duration', 'status'], ['name', 'start', 'end', 'duration']]                 | ['Test', null] | null                                    | null             | null
        'a.aa.aaa' | 'setup'    | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aaa' | 'cleanup'  | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aab' | 'setup'    | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aab' | 'cleanup'  | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aab' | 'action'   | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aab' | 'onError'  | ['name', 'start', 'end', 'duration', 'exception']                                                      | null           | null                                    | RuntimeException | 'NULL'
        'a.aa.aac' | 'action'   | ['name', 'start', 'end', 'duration', 'exception', 'status']                                            | 'OK'           | null                                    | RuntimeException | 'NULL'
        'a.aa.aac' | 'setup'    | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aac' | 'cleanup'  | ['name', 'start', 'end', 'duration']                                                                   | null           | null                                    | null             | null
        'a.aa.aac' | 'onError'  | [['name', 'start', 'end', 'duration', 'exception'], ['name', 'start', 'end', 'duration', 'exception']] | []             | null                                    | RuntimeException | 'NULL'
        'a.aa.aac' | 'onCancel' | ['name', 'start', 'end', 'duration', 'argument', 'status']                                             | 'Failed'       | ['RETRY', new RuntimeException('NULL')] | null             | null
    }

    @Unroll
    void "Does \$info work for #path #name with cancel?"() {
        when:
        def msg = []
        Glow glow = builder.glow {
            step('b') {
                onCancel { msg << "cancel_$bubble.path" }
                step('ba') {
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
        glow.start()

        then:
        def info = path.split(/\./).inject(glow.steps) { parent, name -> return parent."$name" }
        println info.$info
        testInfo(info.$info, name, keys, _status, argument, exception, message)

        where:
        path   | name       | keys                                             | _status | argument | exception | message
        'b'    | 'onCancel' | ['name', 'start', 'end', 'duration', 'argument'] | null    | 'MANUAL' | null      | null
        'b'    | 'action'   | ['name', 'start', 'end', 'duration']             | null    | null     | null      | null
        'b.ba' | 'action'   | ['name', 'start', 'end', 'duration']             | null    | null     | null      | null
        'b.ba' | 'setup'    | ['name', 'start', 'end', 'duration']             | null    | null     | null      | null
        'b.ba' | 'cleanup'  | ['name', 'start', 'end', 'duration']             | null    | null     | null      | null
        'b.ba' | 'onCancel' | ['name', 'start', 'end', 'duration', 'argument'] | null    | 'MANUAL' | null      | null
    }

    private void testInfo(
            def info, String name, List keys, def status,
            def argument, Class<? extends Throwable> exception, String message) {
        def part = info."$name"
        if (part instanceof List) {
            part.eachWithIndex { p, idx ->
                testPart(p, name, keys[idx], status[idx], argument, exception, message)
            }
        } else {
            testPart(part, name, keys, status, argument, exception, message)
        }
    }

    private void testPart(Map part, String name, List keys, def status,
                          def argument, Class<? extends Throwable> exception, String message) {
        assert part.keySet().sort() == keys.sort()
        assert part.name == name
        if ('start' in keys) assert part.start instanceof Date
        if ('end' in keys) assert part.end instanceof Date
        if ('duration' in keys) assert part.duration == part.end.time - part.start.time
        if (status) assert part.status == status
        if (argument) assert part.argument.toString() == argument.toString()
        if (exception) assert part.exception.getClass().isAssignableFrom(exception)
        if (message) assert part.exception.message == message
    }

    //@Ignore
    void "Does status work?"() {
        when:
        def count = 0
        Glow glow = builder.glow {
            step('a') {
                step('aa') {
                    action {
                        status 'aa'
                    }
                    step('aaa') {
                        action {
                            status 'aaa'
                        }
                    }
                    step('aab') {
                        action {
                            status 'aab'
                            if (count < 2) {
                                count++
                                throw new RuntimeException('NULL')
                            }
                        }
                        onError {
                            retry(3)
                        }
                    }
                    step('aac') {
                        action {
                            status 'aac'
                            throw new RuntimeException('NULL')
                        }
                        onError {
                            status 'Failed'
                            retry(3)
                        }
                    }
                }
            }
        }
        glow.start()

        then:
        glow.steps.a.aa.status == 'aa'
        glow.steps.a.aa.aaa.status == 'aaa'
        glow.steps.a.aa.aab.status == 'aab'
        glow.steps.a.aa.aac.status == 'Failed'

        when:
        def msg = []
        glow = builder.glow {
            step('b') {
                onCancel { msg << "cancel_$bubble.path" }
                step('ba') {
                    action {
                        status 'ba'
                        cancel()
                    }
                    onCancel {
                        println "------------------"
                        status 'Cancelled'
                    }
                }
                step('bb') {
                    action {
                        status 'bb'
                    }
                }
            }
        }
        glow.start()

        then:
        glow.steps.b.ba.status == 'Cancelled'
        glow.steps.b.bb.status == null
    }

    @Unroll
    def "Test stepwise iteration: path '#path', type '#type'"() {
        setup:
        def count = 0
        Glow glow = builder.glow {
            step('a') {
                step('aa') {
                    action {
                        status 'aa'
                    }
                    step('aaa') {
                        action {
                            status 'aaa'
                        }
                    }
                    step('aab') {
                        action {
                            status 'aab'
                            if (count < 2) {
                                count++
                                throw new RuntimeException('NULL')
                            }
                        }
                        onError {
                            status('Failed')
                            retry(3)
                        }
                    }
                    step('aac') {
                        action {
                            status 'aac'
                            throw new RuntimeException('NULL')
                        }
                        onError {
                            status 'Failed'
                            retry(3)
                        }
                    }
                }
            }
        }

        when:
        def eventFound = false
        glow.addStepListener { StepEvent event ->
            if (event.source.path == path && event.type == type && event.count == eventCount) {
                eventFound = true
                check(event)
            }
        }
        glow.start()

        then:
        eventFound

        where:

        path       | type                     | eventCount | check
        'a'        | GlowActionType.NEXT      | 0          | { StepEvent event -> assert true }
        'a.aa'     | GlowActionType.NEXT      | 1          | { StepEvent event -> assert event.source.status == 'aa' }
        'a.aa.aaa' | GlowActionType.NEXT      | 2          | { StepEvent event -> assert event.source.status == 'aaa' }
        'a.aa.aab' | GlowActionType.RETRY     | 3          | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aab' | GlowActionType.EXCEPTION | 4          | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
        'a.aa.aab' | GlowActionType.RETRY     | 5          | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aab' | GlowActionType.EXCEPTION | 6          | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
        'a.aa.aab' | GlowActionType.NEXT      | 7          | { StepEvent event -> assert event.source.status == 'aab' }
        'a.aa.aac' | GlowActionType.RETRY     | 8          | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aac' | GlowActionType.EXCEPTION | 9          | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
        'a.aa.aac' | GlowActionType.RETRY     | 10         | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aac' | GlowActionType.EXCEPTION | 11         | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
        'a.aa.aac' | GlowActionType.RETRY     | 12         | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aac' | GlowActionType.EXCEPTION | 13         | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
        'a.aa.aac' | GlowActionType.RETRY     | 14         | { StepEvent event -> assert event.source.status == 'Failed' }
        'a.aa.aac' | GlowActionType.EXCEPTION | 15         | { StepEvent event ->
            assert event.source.status == 'Failed'
            assert event.exception.toString() == new RuntimeException('NULL').toString()
        }
    }

    def "Test accessing outside methods"() {
        setup:
        Glow glow = builder.glow {
            step('a') {
                step('aa') {
                    action {
                        status method1()
                    }
                    onError {
                        it.printStackTrace()
                        status('Failed')
                    }
                }
            }
        }

        when:
        glow.start()

        then:
        glow.steps.a.aa.status == 'Method1'
    }

    private String method1() { return 'Method1'}

    @Ignore
    def "Test glow events"() {
        setup:
        Glow glow = builder.glow {
            step('a') {
                step('aa') {
                    action {
                        status 'aa'
                    }
                }
                step('ab') {
                    action {
                        status 'ab'
                    }
                    onError {
                        it.printStackTrace()
                        status('Failed')
                    }
                }
            }
        }

        when:
        glow.start()

        then:
        glow.steps.a.aa.status == 'Method1'
    }

    def "Does throwing without onError work?"() {
        when:

        Glow glow = builder.glow() {
            step {
                action {
                    throw new RuntimeException('Test')
                }
            }
        }
        glow.start()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Test'
    }

    def "Does throwing with onError work?"() {
        when:

        Glow glow = builder.glow() {
            step {
                action {
                    throw new RuntimeException('Test')
                }
                onError {
                    true
                }
            }
        }
        glow.start()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Test'
    }

    def "Does throwing with onError on higher level work?"() {
        when:

        Glow glow = builder.glow() {
            step {
                onError {
                    true
                }
                step {
                    action {
                        throw new RuntimeException('Test')
                    }
                }
            }
        }
        glow.start()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Test'
    }

    def "Does not throwing with onError on higher level work?"() {
        expect:
        Glow glow = builder.glow() {
            step {
                onError {
                    false
                }
                step {
                    action {
                        throw new RuntimeException('Test')
                    }
                }
            }
        }
        glow.start()
    }

    def "Does throwing with onError on top level work?"() {
        when:

        Glow glow = builder.glow() {
            onError {
                true
            }
            step {
                step {
                    action {
                        throw new RuntimeException('Test')
                    }
                }
            }
        }
        glow.start()

        then:
        def e = thrown(RuntimeException)
        e.message == 'Test'
    }

    def "Does not throwing with onError on top level work?"() {
        expect:
        Glow glow = builder.glow() {
            onError {
                false
            }
            step {
                step {
                    action {
                        throw new RuntimeException('Test')
                    }
                }
            }
        }
        glow.start()
    }

    def "Does not throwing with onError work?"() {
        expect:

        Glow glow = builder.glow() {
            step {
                action {
                    throw new RuntimeException('Test')
                }
                onError {
                    false
                }
            }
        }
        glow.start()
    }

    def "Does duration work?"() {
        when:
        Glow glow = builder.glow() {
            step (name: 'aa'){
                action {
                    Thread.sleep(1000)
                }
            }
            step (name: 'ab'){
                action {
                    Thread.sleep(2000)
                }
            }
        }
        glow.start()

        then:
        glow.steps.values().find{it.name=="aa"}.$info.action[0].duration > 1000
        glow.steps.values().find{it.name=="ab"}.$info.action[0].duration > 2000
    }

    def "Does jump onError work?"() {
        when:
        Glow glow = builder.glow() {
            step (name: 'aa'){
                onError {
                    jump(steps.find{it.value.description == "cleanup"}.value)
                }
                action{
                    throw new RuntimeException('Test')
                }
            }
            step (name: 'ab'){
                action{
                    status "complete"
                }
            }
            step (description: 'cleanup'){
                action {
                    status "complete"
                }
            }
        }
        glow.steps.each {
            it.value.status = "not started"
        }
        glow.start()

        then:
        glow.steps.find{it.value.name=="ab"}.value.status == "not started"
        glow.steps.find{it.value.description=="cleanup"}.value.status == "complete"

    }
}