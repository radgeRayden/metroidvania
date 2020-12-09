using import struct
using import Map

let glfw = (import .FFI.glfw)

struct InputState plain
    A : bool
    B : bool
    Left : bool
    Right : bool
    Up : bool
    Down : bool

global previous-state : InputState
global current-state : InputState

global main-window : (mutable@ glfw.window)
fn key-down? (code)
    (glfw.GetKey main-window code) as bool

fn init (window)
    main-window = window
    # TODO: load bindings from config file
    ;

fn update ()
    previous-state = current-state
    current-state =
        # TODO: make bindings replaceable
        InputState
            A = (key-down? glfw.GLFW_KEY_Z)
            B = (key-down? glfw.GLFW_KEY_X)
            Left = (key-down? glfw.GLFW_KEY_LEFT)
            Right = (key-down? glfw.GLFW_KEY_RIGHT)
            Up = (key-down? glfw.GLFW_KEY_UP)
            Down = (key-down? glfw.GLFW_KEY_DOWN)

inline down? (button)
    deref (getattr current-state button)

inline pressed? (button)
    (deref (getattr current-state button)) and (not (getattr previous-state button))

inline released? (button)
    (not (getattr current-state button)) and (deref (getattr previous-state button))

do
    let
        init
        update
        down?
        pressed?
        released?
    locals;
