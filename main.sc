using import glm

import .raydEngine.use
import app
let gfx = (import gfx.webgpu.backend)

@@ 'on app.update
fn (dt)
    ;

@@ 'on app.draw
fn (fb-pass)
    ;

@@ 'on app.init
fn ()
    gfx.set-clear-color (vec4 0.017 0.017 0.017 1.0)
    ;

app.run;
