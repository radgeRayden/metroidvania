using import struct
using import enum
using import Array

enum EventPayload
    EntityId : u32

    inline __copy (self)
        'apply self
            (T payload) -> (T (copy payload))

struct Event
    target : u32
    payload : EventPayload

    inline __copy (self)
        this-type (copy self.target) (copy self.payload)

enum EventType plain
    Collision

    inline __hash (self)
        hash (storagecast self)

let EventQueue = (Array Event)
global event-queues : (array EventQueue (va-countof EventType.__fields__))

fn push-event (evtype event)
    let evlist = (event-queues @ (storagecast evtype))
    'append evlist event
    ;

fn peek-events (evtype)
    event-queues @ (storagecast evtype)

fn poll-events (evtype)
    let queue = (event-queues @ (storagecast evtype))
    local result = (EventQueue)
    # NOTE: swap is bugged / doesn't work as I expect. So we're gonna be
    # copying the array instead.
    # swap result (event-queues @ (storagecast evtype))
    for ev in queue
        'append result (copy ev)
    'clear queue
    result

do
    let
        EventPayload
        Event
        EventType
        push-event
        peek-events
        poll-events
    locals;
