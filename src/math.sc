using import glm

inline... rotate
case (v : vec2, angle : f32)
    let rcos rsin = (cos angle) (sin angle)
    vec2
        (rcos * v.x) - (rsin * v.y)
        (rsin * v.x) + (rcos * v.y)

inline ortho (width height)
    # https://www.scratchapixel.com/lessons/3d-basic-rendering/perspective-and-orthographic-projection-matrix/orthographic-projection-matrix
    # right, top
    let r t = (width / 2) (height / 2)
    # left, bottom
    let l b = -r -t
    # far, near
    let f n = 100 -100
    mat4
        vec4
            (2 / (r - l))
            0.0
            0.0
            -((r + l) / (r - l))
        vec4
            0.0
            (2 / (t - b))
            0.0
            0.0
        vec4
            -((t + b) / (t - b))
            0.0
            (-2 / (f - n))
            -((f + n) / (f - n))
        vec4 0.0 0.0 0.0 1.0

do
    let
        rotate
        ortho
    locals;
