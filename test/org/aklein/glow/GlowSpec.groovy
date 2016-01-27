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
                step() {
                }
            }
            step {
                step() {
                    step()
                    step() {
                    }
                }
            }
            step {
                step {
                    step()
                    step()
                }
                step('hello', name: 'Blah') {
                    step()
                    step()
                }
            }
        }


        then:
        glow.steps.size() == 3
        glow.steps.iterator()[0].key == '_0'
        glow.steps.iterator()[1].key == '_1'
        glow.steps._0.id == '_0'
        glow.steps._1.id == '_1'
        glow._0.id == '_0'
        glow._1.id == '_1'
        //glow._3.firstChild.id == 'hello'
        glow._3.hello.name == 'Blah'
    }
}
