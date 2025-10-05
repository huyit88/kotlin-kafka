package com.example

fun main(args: Array<String>){
    var appName = "app-cli"
    args.forEach{arg->
        if(arg.startsWith("--app=")){
            appName = arg.substringAfter("=")
        }
        if (arg == "--help" || arg == "-h") {
            println("Usage: run --args=\"--app=<name>\"")
            return
        }
    }

    val bootstrap = System.getenv("KAFKA_BOOTSTRAP") ?: "localhost:9092"
    LogCli(bootstrap).run(appName)
}