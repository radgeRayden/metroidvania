using import glm
using import Option
using import struct

import .raydEngine.use
import HID
import filesystem
import .renderer

filesystem.init;
HID.init (HID.WindowOptions (visible? = true)) (HID.GfxAPI.OpenGL)
renderer.init;

while (not (HID.window.received-quit-event?))
    HID.window.poll-events;

    renderer.begin-frame (vec4 1.0 0.4 0.4 1.0)
    renderer.end-frame;

    HID.window.swap-buffers;
;
