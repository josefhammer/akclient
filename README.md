# AK Client

A client for the AK protocol used in the automotive industry - allows both scripted and interactive use.

No configuration required! AK commands are detected using a regular expression; by default a single word consisting
of 4 or 5 uppercase letters (A-Z). For additional convenience a JSON file can be loaded containing meta data
about the device, in particular the names of the response variables.

Ideal for writing complex test scripts in Groovy to automate the testing of devices. Groovy is a very powerful
scripting language, and allows a syntax familiar to all the C++ developers in the automotive industry.


## Getting Started

[Apache Groovy](http://groovy-lang.org) must be installed to run the program, which in turn requires a [Java JDK or JRE](https://java.com), ideally version 7 or higher.  

To keep things simple, the entire program consists of a single file only, **`akclient.groovy`**. Depending on your system you may be able to start the script directly or by calling `groovy akclient.groovy`. 

The files in the `/examples` folder are a good option to get familiar with the syntax.


## Usage

    akclient [options] [scriptfile(s)]
     -h,--help           Show usage information
     -i,--interactive    Interactive mode
     -p,--port <arg>     AK server port
     -s,--server <arg>   AK server host

The default connection is accessible via the `ak` variable. AK commands can be issued using either their "raw" form
in a separate line (e.g. `AKEN K0`) or inline as methods of an AKConnection object (e.g. `ak.AKEN()`). Raw AK
commands always use the default connection; the result can be retrieved with `ak()`.

Scripted and interactive use can be combined in any order. For example, a user can run a script to prepare the
correct state of the device, continue interactively using the open connection, followed by a scripted shutdown of
the device. 

If several files are given as input, they will be executed sequentially. Input can also be read from a pipe using
shell redirection. Use `exit` or `continue` to leave the interactive mode.

See `/examples` for a few script files to learn more about the various features.


## License

This project is licensed under the [MIT License](LICENSE).
