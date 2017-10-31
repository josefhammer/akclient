//
// Test script for the Demo device.
//
ak.loadDeviceDescription("device.json")


// check that we are connected to the correct device
//
assert AKEN() =~ /DEMO/                     // Implicit type coercion from AKResult to the response string!
assert AKEN()() == "AKEN 0 DEMO 1.00 0"     // Not possible with '==' though, only with regex matcher.

ak.help "ASTA"                              // Use help(command) to print a command's meta data


// create a second connection
//
ak2 = ak.clone("ak2")
ak2.connect()
assert ak2.AKEN()                           // Type coercion to boolean works too (you can also use !AKEN()).


// test mutual SREM / SREX
//
assert ak2.ASTA().userLevel == -1           // 'userLevel' is available only with a loaded device description.
assert ak2.ASTA()[2] == "-1"                // Alternative syntax if no device description is available.
ak2.SREM()
assert ak2.ASTA().userLevel == 0
assert ASTA().userLevel == -1
// 
// try E command without SREM               // Raw AK commands can be sent if they are the only thing in a line!
//
EMUL 50
assert ak().errorCode == "OF"               // It's possible to use the cached result from the previous command.
//
// ak takes remote away from ak2            
//
SREM
assert ak2.ASTA().userLevel == -1
assert ASTA().userLevel == 0
//
// after ak.SREX, ak2 is not able to call SREM anymore
//
SREX
assert ak2.SREM().errorCode == "OF"
assert ak2.ASTA().userLevel == -1
assert ASTA().userLevel == 0
//
// also, it's forbidden to downgrade from SREX to SREM
//
assert !SREM()
//
// after ak.SMAN, ak2 can SREX again
//
SMAN
assert ASTA().userLevel == -1
assert ak2.SREX() && ak2.ASTA().userLevel == 0
//
// close connection and thus release SREX
//
ak2.disconnect()
//
// SREM should be allowed multiple times
//
3.times {                                   // Execute this loop three times.
    assert SREM()
}
//
// SREX should be allowed multiple times too
//
for (int i=0; i < 3; ++i) {                 // If you are unfamiliar with Groovy: use regular Java syntax.
    assert SREX()
}
SMAN


// test EUSR: Try to upgrade user to Engineer
//
assert EUSR(1, "password").errorCode == "OF"            // remote missing
SREM
assert EUSR(1, System.getenv("PASSWORD_ENGINEER"))
assert ASTA().userLevel == 1                            // user level Engineer
//
// downgrade to Operator again
//
assert EUSR(0) && ASTA().userLevel == 0


// set and read back a value
//
assert EMUL(50)
assert AMUL()[0] == "50"


// trigger and wait for state changes
//
// create some convenience methods first    
//
void waitForReady()                   // Methods can be written in plain Java (will be visible in this script only).
{
    while (ASTA().status != "R") {
        sleep(500)
    }
}
isInState = { name ->                 // To allow usage in successive scripts, use this closure syntax (without 'def'!).    
    ASTA().deviceState == ak.meta.States.DeviceState[name]
}
//
// go to Standby
//
while (!isInState("Standby")) {
    sleep(500)
    STBY
}
waitForReady()
//
// go to Measurement
//
assert SMES()
waitForReady()
assert isInState("Measurement")
//
// go back to Pause
//
assert STBY()
waitForReady()
assert SPAU()
waitForReady()
assert isInState("Pause")

