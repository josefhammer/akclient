#!/usr/bin/env groovy

/** A client for the AK protocol used in the automotive industry - allows both scripted and interactive use.

    <p>
    No configuration required! AK commands are detected using a regular expression; by default a single word consisting
    of 4 or 5 uppercase letters (A-Z). For additional convenience a JSON file can be loaded containing meta data 
    about the device, in particular the names of the response variables.
    
    <p>
    Ideal for writing complex test scripts in Groovy to automate the testing of devices. Groovy is a very powerful
    scripting language, and allows a syntax familiar to all the C++ developers in the automotive industry.
    
    <p>
    The default connection is accessible via the `ak` variable. AK commands can be issued using either their "raw" form 
    in a separate line (e.g. `AKEN K0`) or inline as methods of an AKConnection object (e.g. `ak.AKEN()`). Raw AK 
    commands always use the default connection; the result can be retrieved with `ak()`.
    
    <p>
    Scripted and interactive use can be combined in any order. For example, a user can run a script to prepare the 
    correct state of the device, continue interactively using the open connection, followed by a scripted shutdown of 
    the device.
    
    <p>
    If several files are given as input, they will be executed sequentially. Input can also be read from a pipe using 
    shell redirection. Use 'exit' or 'continue' to leave the interactive mode.
    
    <p>
    __Usage__ 
    <pre>{@code
 
    akclient [options] [scriptfile(s)]
     -h,--help           Show usage information
     -i,--interactive    Interactive mode
     -p,--port <arg>     AK server port
     -s,--server <arg>   AK server host
 
    }</pre>
   
    __NOTE__ The main reason for having this class is to avoid namespace issues with the AK scripts.
    

    <pre>
    
    MIT License

    Copyright (c) 2017 Josef Hammer

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.   
    
    </pre>
*/
class AK
{
    /** The regular expression used to detect AK commands in the Groovy code (customizable). */
    static cmdRegex = /[A-Z]{4,5}/              
    /** The list of available error codes that will be stored in AKResult.errorCode (customizable). */
    static errorCodes = ['BS', 'SE', 'NA', 'DF', 'OF']
    /** The path to the AK script being evaluated */
    static scriptDir = ""

    
    /** Uses cmdRegex to determine whether the missing method is an AK command; if so, the corresponding AK command
        is executed.
        
        As a result, any command that matches the regular expression can be used without having to know the available
        commands in advance.
    */
    static methodMissing(obj, AKConnection ak, String name, args)
    {
        if (name ==~ /${AK.cmdRegex}/) {
        
            ak(name + (ak.channel >= 0 ? " K$ak.channel" : "") + (args ? " ${args.join(' ')}" : ""))
        } 
        else {
            throw new MissingMethodException(name, obj.class, args)
        }
    }
    

    /** A little utility to measure the execution time of arbitrary code blocks.
    
        __USAGE__ `def duration = AK.benchmark { your code here }`
    */
    static benchmark = { closure ->  
        def start = System.currentTimeMillis()
        closure.call()  
        System.currentTimeMillis() - start  
    } 


    /** Parses the given script for raw AK commands, replaces them with method calls, and evaluates the entire script.
    */
    static evaluateScript(obj, String script, String scriptDir = "")
    {
        this.scriptDir = scriptDir
        def sb = new StringBuilder()
        sb.append("def methodMissing(String name, args) { AK.methodMissing(this, ak, name, args) }\n") // REVIEW
        
        script.eachLine { sb.append(parse(it) + '\n') }
        obj.evaluate(sb.toString())
    }


    /** Checks a single line whether it consists solely of a raw AK command. If so, the entire line is replaced with
        the appropriate method call; otherwise, the original line is returned.
    */
    static String parse(String line)
    {
        ((line =~ /^\s*${AK.cmdRegex}(\s+[^\(]|$)/) ? "ak \"${line.trim()}\"" : line)
    }


    /** Interactive mode: Waits for user input and executes each line separately. Use 'exit' or 'continue' to leave.
    */
    static interactive(obj)
    {
        while (true) {
            print "> "
            def line = System.console().readLine()
            
            if (line in ['exit', 'continue']) {
                break
            }
            
            try {
                evaluateScript(obj, line)
            }
            catch (all) {    // do not crash if the user made a typo in interactive mode
                println all
            }
        }
    }
}


/** Provides the parsed result of an AK command.
*/
class AKResult
{
    String request = ""
    String response = ""
    String command = ""
    int errorStatus = 0
    String errorCode = ""

    private meta                ///< The device description (@see AKConnection.loadDeviceDescription)
    private values = []         ///< The values from the response message (excluding command and errorStatus).
    
    
    /** Creates a new AKResult object and parses the response message.
      
        @param meta The device description.
        @param request The full request message sent.
        @param response The full response message received.
    */
    AKResult(meta, String request, String response)
    {
        assert request && response
        this.meta = meta
        this.request = request
        this.response = response
        
        parseResponse()
    }
    
    
    /** Returns the value at the requested position from the response (zero-based index).
    */
    def getAt(int i) 
    {
        return (i < values.size()) ? values[i] : ""
    }
    
    
    /** Returns the value (from the response) of the given property.

        Does the command used to obtain this result contain the requested property in the device description?
        If yes, then the position of the property is used to obtain the corresponding value from the result.
        Furthermore, the value is converted to the type defined in the device description.
      
        @throws A MissingPropertyException if the command does not have the requested property.
        @return The value of the property converted to the type defined in the device description.
    */
    def propertyMissing(String name) 
    { 
        if (meta?.AK?.containsKey(command)) {
            
            def index = meta.AK[command].Response.findIndexOf { it.containsKey(name) }
            def type = meta.AK[command].Response[index][name]
            
            if ((index >= 0) && (index < values.size())) {
                
                def result = values[index]
                return ("Int" == type) ? result as int : (("Float" == type) ? result as BigDecimal : result)
            }
        }
        throw new MissingPropertyException("$command does not have $name.")
    }
    
    
    /** Returns the response message: Convenience method to avoid having to type `.response`.
    */
    String call()
    {
        response
    }

    
    /** Returns the response message.
    */
    String toString() 
    {
        response
    }

    
    /** Custom asBoolean: Allows to check the result easily (e.g. `assert SREM()`).
    */
    boolean asBoolean()
    {
        !errorCode
    }
    

    /** Parses the errorCode; can be customized in scripts with:

        `AKResult.metaClass.parseErrorCode = { ... }`
    */
    String parseErrorCode()
    {
        (values.size() && (values[0] in AK.errorCodes)) ? values[0] : ""
    }
    
    
    /** Parses and analyzes the response message.
    */
    private void parseResponse()
    {
        command = (request =~ /^${AK.cmdRegex}/)[0]
        
        def matcher = (response =~ /^(${AK.cmdRegex})( \d)?(.*)$/)

        // did the device recognize the command? (command name in the request must match the one in the response)
        //
        if (!matcher || (command != matcher[0][1])) {  
            errorCode = '????'
            return
        }
        
        // errorStatus available? (should always be the case, but to be safe)
        //
        if (matcher[0][2]) {
            errorStatus = matcher[0][2] as int
        }

        // anything else (= any values) available?
        //
        values = (matcher[0][3] ?: '').split()
        
        // calculate the errorCode
        //
        errorCode = parseErrorCode()
    }
}


/** Allows to open a connection to a device and send commands.
*/
class AKConnection
{
    public String host = 'localhost'
    public int port = 2500
    public int channel = 0    ///< `Kn` (n=channel) is added to all AK commands if >= 0; does not affect raw AK commands
    public final String name             ///< A name to identify the connection in log messages.
    public boolean logRequest = false    ///< Shall the request message be printed to stdout?
    public boolean logResponse = true    ///< Shall the response message be printed to stdout?
    public meta = [:]                    ///< Meta data: the device description (@see loadDeviceDescription()).
    public AKResult result = null        ///< Stores the result of the previous request.
    
    private connection
    private reader
    private writer
    

    /** Creates a new connection object, but does not connect to it yet.
    */
    AKConnection(String name)
    {
        this.name = name
    }
    

    /** Clones an existing connection object (i.e. all the configuration data), but does not connect to the device.
    
        Useful to open multiple connections to the device defined on the command line.
    */
    AKConnection clone(String newName)
    {
        def ak = new AKConnection(newName)
        ak.host = this.host
        ak.port = this.port
        ak.channel = this.channel
        ak.logRequest = this.logRequest
        ak.logResponse = this.logResponse
        ak.meta = this.meta
        return ak
    }
    

    /** Connects the defined device.
    */
    AKConnection connect()
    {
        disconnect()
        if (name) { print "[$name] " }
        print "Connecting to $host:$port..."
        
        connection = new Socket(host, port)
        reader = connection.inputStream.newReader()
        writer = connection.outputStream.newWriter()

        println "done.\n"
        this
    }
    

    /** Closes the active connection.
    */
    AKConnection disconnect()
    {
        connection?.close()
        this
    }
    
    
    /** Call operator without parameter - allows to conveniently get the result of the previous request.
    */
    AKResult call()
    {
        result
    }

    
    /** Call operator with a string parameter - sends an AK command (including all parameters defined in the request).
    
        Request and response will be printed to stdout depending on whether logRequest and logResponse are set to true.
    */
    AKResult call(String request) 
    {
        def timestamp = { new Date().format("HH:mm:ss.SSS  ") }
        
        def id = name ? "[$name] " : ""
        if (logRequest) { println timestamp() + id + request }
        
        def response = process(request)
        if (logResponse) { println "${timestamp()}$id: $response" }
        
        result = new AKResult(meta, request, response)
    }
    

    /** Allows to execute any AK command as a method of this class (@see AK.methodMissing).
    */
    def methodMissing(String name, args) 
    { 
        AK.methodMissing(this, this, name, args) 
    }
    
    
    /** Loads a device description (optional) for the device currently connected.
     
        Having a device description is not required; however, it enables to access response values by name instead of
        by index only. Furthermore, the value is converted to the corresponding type automatically.
        
        Unless filename contains an absolute path, files are loaded relative to the location of the AK script being
        evaluated.
      
        Expects a JSON file with the following structure:
      
        <pre>{@code
        { "AK": {
            "$commandName1": {
                "Parameters": [
                    {"$paramName1" : "$type1"},
                    {"$paramName2" : "$type2"},
                    ...
                ],
                "Optional": [
                    {"$optionalParamName1" : "$type1"},
                    {"$optionalParamName2" : "$type2"},
                    ...
                ]
                "Response": [
                    {"$propertyName1": "$type1"},
                    {"$propertyName2": "$type2"},
                    ...
                ]
            },
            "$commandName2": {
                ...
            }
        }
        }</pre>
        
        __NOTE__ All property names will be uncapitalized to achieve a consistent coding style.
    */
    void loadDeviceDescription(String filename)
    {
        def file = new File(filename)
        if (!file.isAbsolute()) { file = new File("$AK.scriptDir/$filename") }     // prepend scriptDir if relative path
        
        def dd = new groovy.json.JsonSlurper().parseText(file.text)
        assert dd?.AK?.keySet()

        meta = dd
        
        // uncapitalize all property names (for a consistent coding style)
        //
        meta.AK = dd.AK.collectEntries { cmd, map -> [(cmd): map.collectEntries {
            inOut, list -> [(inOut): list.collect {
                prop -> prop.collectEntries { key, value -> [(key.uncapitalize()): value] }
            } ]
        } ] }
    }
    

    /** Prints info about a specific command (basically, the data from the device description).
    */
    void help(String command)
    {
        if (meta?.AK?.containsKey(command)) {
            
            println "[$command]"
            meta.AK[command].each { it.each { 
                println "\t[$it.key]"
                println it.value.eachWithIndex { prop, index -> println "\t\t$index: $prop" }
            } }
        }
        else {
            println "No description available for $command."
        }
    }

    
    /** Sends an AK command to the device and reads the response.
     
        @returns The response to the AK command.
    */
    private String process(String msg) 
    {
        final char kTelegramStartChar = 2       // Start of Text
        final char kTelegramStopChar = 3        // End of Text
            
        writer.write("$kTelegramStartChar $msg$kTelegramStopChar")
        writer.flush()
        
        def sb = new StringBuilder()
        def value

        // ignore everything until (and including) the TelegramStartChar
        //
        while (kTelegramStartChar != (value = reader.read())) {
            // do nothing
        }
        
        // ignore the 'ignore' character that comes right after the TelegramStartChar
        //
        reader.read()  

        // read the actual response up to the TelegramStopChar
        //
        while (kTelegramStopChar != (value = reader.read())) {
            sb.append((char) value)
        }
        sb.toString()
    }
}


// ***** MAIN PROGRAM *****
//
// specify command line parameters
//
def cli = new CliBuilder(usage: "${this.class.simpleName} [options] [scriptfile(s)]") 
cli.with {
    h longOpt: 'help', 'Show usage information'
    i longOpt: 'interactive', 'Interactive mode'
    s longOpt: 'server', args:1, 'AK server host'
    p longOpt: 'port', args:1, 'AK server port'
}
def hasPipeInput = !System.console()

// parse and process command line parameters
//
def options = cli.parse(args)

if (!options || options.help || (!hasPipeInput && !options.interactive && options.arguments().isEmpty())) {
    cli.usage()
    return
}

// connect to the AK server and make the connection object visible to all scripts
//
ak = new AKConnection()

if (options.server) { ak.host = options.server }
if (options.port) { ak.port = options.port as int }

ak.connect()

// process all files given as arguments
//
ak.logRequest = true
options.arguments().each {
    def file = new File(it)
    AK.evaluateScript(this, file.text, file.parent) 
}

// process any input from a pipe
//
if (hasPipeInput) {
    System.in.withReader {
        AK.evaluateScript(this, it.text)
    }
}

// and, finally, allow interactive input (__NOTE__ Console is not available with hasPipeInput)
//
else if (options.interactive) {
    
    ak.logRequest = false
    AK.interactive(this)
}
