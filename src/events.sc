using import struct
using import Map
using import enum
using import Array
using import Rc

spice has-symbol? (T sym)
    T as:= type
    sym as:= Symbol
    try
        let sym = ('@ T sym)
        `true
    else
        `false
run-stage;

# example, input events:
# Component PlayerController registers itself (or is registered) as a subscriber
# module input.sc posts an event to the input event queue
# the dispatcher

inline on-trigger-enter-subscribe (...)
    print ...

inline on-trigger-exit-subscribe (...)
    print ...

fn register-subscriptions (component-ref)
    let Component = ((typeof component-ref) . Type)
    'apply (imply component-ref Component)
        inline (ft component)
            let T = (elementof ft.Type 0)
            inline subscribe (callback-name subscribe-fn)
                static-if (has-symbol? T callback-name)
                    # TODO: change to static-typify events.EventCallback once that is implemented
                    subscribe-fn (copy component-ref) (imply (getattr T callback-name) Closure)

            subscribe 'on-trigger-enter on-trigger-enter-subscribe
            subscribe 'on-trigger-exit on-trigger-exit-subscribe

do
    let
        register-subscriptions
    locals;
