using import struct
using import enum
using import Array
using import glm

enum EventPayload
    Direction : vec2
    Position : vec2
    Empty

    inline __typecall (cls)
        this-type.Empty;

    inline __copy (self)
        let st = (storagecast (dupe self))
        bitcast st this-type

struct Event
    source : u32
    target : u32
    payload : (array EventPayload 4)

    inline __copy (self)
        local arr : (array EventPayload 4)
        for i el in (enumerate self.payload)
            arr @ i = (copy el)
        this-type (copy self.target) (copy self.target) arr

enum EventType plain
    Collision
    TriggerEnter
    TriggerExit

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
