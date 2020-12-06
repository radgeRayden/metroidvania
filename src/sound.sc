let soloud = (import .FFI.soloud)

global soloud-instance : (mutable@ soloud.Soloud)
fn init ()
    soloud-instance = (soloud.create)
    if (soloud.init soloud-instance)
        error "sound system could not be initialized"

fn cleanup ()
    soloud.deinit soloud-instance
    soloud.destroy soloud-instance

do
    let
        init
        cleanup
    locals;
