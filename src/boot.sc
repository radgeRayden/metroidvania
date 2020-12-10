using import .radlib.core-extensions
using import .radlib.stringtools

using import enum
using import struct
using import glm
using import Array
using import Rc
using import Map
using import String
using import itertools
using import Option

import .math
import .entity
import .filesystem
import .renderer
import .collision
import .component
import .sound
import .input
using import .common
using renderer

let C = (import .radlib.libc)

let glfw = (import .FFI.glfw)
let gl = (import .FFI.glad)
let stbi = (import .FFI.stbi)
let cjson = (import .FFI.cjson)
let c2 = (import .FFI.c2)
let ig = (import .FFI.imgui)

inline json-array->generator (arr)
    Generator
        inline "start" ()
            arr.child
        inline "valid?" (self)
            self != null
        inline "at" (self)
            self
        inline "next" (self)
            self.next

typedef+ (mutable@ cjson.cJSON)
    inline __as (selfT otherT)
        static-if (otherT == Generator)
            json-array->generator

semantically-bind-types ig.ImVec2 vec2
    inline "conv-from" (self)
        vec2 self.x self.y
    inline "conv-to" (other)
        ig.ImVec2 other.x other.y

let argc argv = (launch-args)

fn update (dt)
fn draw ()

fn main (argc argv)
    filesystem.init argv
    glfw.SetErrorCallback
        fn "glfw-error" (error-code message)
            assert false (string message)

    glfw.Init;
    glfw.WindowHint glfw.GLFW_CLIENT_API glfw.GLFW_OPENGL_API
    glfw.WindowHint glfw.GLFW_DOUBLEBUFFER true
    glfw.WindowHint glfw.GLFW_OPENGL_FORWARD_COMPAT true
    glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MAJOR 4
    glfw.WindowHint glfw.GLFW_CONTEXT_VERSION_MINOR 5
    glfw.WindowHint glfw.GLFW_OPENGL_DEBUG_CONTEXT true
    glfw.WindowHint glfw.GLFW_OPENGL_PROFILE glfw.GLFW_OPENGL_CORE_PROFILE
    # glfw.WindowHint glfw.GLFW_SAMPLES 4

    let main-window = (glfw.CreateWindow 1280 720 "untitled metroidvania" null null)
    if (main-window == null)
        assert false "Failed to create a window with specified settings."
    glfw.MakeContextCurrent main-window
    glfw.SwapInterval 1

    renderer.init main-window
    sound.init;
    input.init main-window

    ig.CreateContext null
    local io = (ig.GetIO)
    ig.impl.Glfw_InitForOpenGL main-window true
    ig.impl.OpenGL3_Init null

    local last-time = (glfw.GetTime)
    while (not (glfw.WindowShouldClose main-window))
        glfw.PollEvents;

        global dt-accum : f64
        global fps-time-accum : f64
        global fps-samples-counter : u64
        global avg-fps : f32
        let time-scale = 1
        let now = (glfw.GetTime)
        # at 15 fps the game just slows down, to avoid spiral of death / deal with sudden spikes.
        let real-dt = (min (now - last-time) (1 / 15:f64))
        last-time = now
        dt-accum += real-dt * time-scale

        if (fps-time-accum > 0.5)
            avg-fps = (1.0:f64 / (fps-time-accum / (fps-samples-counter as f64))) as f32
            fps-time-accum = 0.0
            fps-samples-counter = 0
        fps-time-accum += real-dt
        fps-samples-counter += 1

        step-size := 1 / 60

        while (dt-accum >= step-size)
            dt-accum -= step-size
            update step-size

        renderer.begin;
        draw;
        renderer.submit;
        renderer.present;

        glfw.SwapBuffers main-window

    glfw.DestroyWindow main-window
    glfw.Terminate;

    sound.cleanup;

static-if PROTO_MODE?
    switch operating-system
    case 'linux
        load-library "../lib/libgame.so"
        load-library "../lib/libglfw.so"
        load-library "../lib/libphysfs.so"
        load-library "../lib/cimgui.so"
        load-library "../lib/libsoloud_x64.so"
    case 'windows
        load-library "../lib/libgame.dll"
        load-library "../lib/glfw3.dll"
        load-library "../lib/libphysfs.dll"
        load-library "../lib/cimgui.dll"
        load-library "../lib/soloud_x64.dll"
    default
        error "Unsupported OS."
    run-stage;
    main argc argv
else
    static-if AOT_MODE?
        compile-object
            default-target-triple
            compiler-file-kind-object
            module-dir .. "/game.o"
            do
                let main = (static-typify main i32 (mutable@ rawstring))
                locals;
