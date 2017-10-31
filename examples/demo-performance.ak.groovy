//
// Performance test script for the Demo device.
//
ak.loadDeviceDescription("device.json")

ak.logRequest = ak.logResponse = false      // disable logging to measure the real performance

// run AKEN benchmark / load test
//
print "Calling AKEN 10.000 times: "

def duration = AK.benchmark {
    for (i in 1..10000) {                   // Another Groovy way to write a for loop.
        AKEN
    }
}
println "$duration ms"


// run parallel SREX load test
//
println "\nLaunching 4 clients, each calling SREX+ASTA 1.000 times:\n"

duration = AK.benchmark {
    def clients = []
    4.times {
        clients << ak.clone(it as String).connect()
    }

    def numRemote = 0
    def threads = []
    4.times { i ->
        threads << new Thread({
            1000.times {
                if (clients[i].SREX()) {
                    assert ++numRemote == 1                         // only one client may have SREX
                    assert clients[i].ASTA().userLevel == 0
                    sleep(10)
                    assert --numRemote == 0
                    clients[i].SMAN()                               // release SREX again
                }
                else {
                    assert clients[i].ASTA().userLevel == -1
                }
            }
        })
    }

    threads.each { it.start() }
    threads.each { it.join() }
    clients.each { it.disconnect() }
}
println "Duration: $duration ms"

ak.logRequest = ak.logResponse = true
