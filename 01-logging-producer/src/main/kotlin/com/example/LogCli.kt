package com.example

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

class LogCli(val bootstrapServers: String){
    fun run(app: String){
        println("run $app")
        val producer = BatchedLogProducer(bootstrapServers)
        val running = AtomicBoolean(true)

        val hook = Thread{
            producer.close()
            running.set(false)
        }
        Runtime.getRuntime().addShutdownHook(hook)

        val reader = BufferedReader(InputStreamReader(System.`in`))
        try{
            while(running.get()){
                val line = reader.readLine() ?: break
                if(line.isBlank()) break
                producer.sendLog(app,line)
            }
        }catch(t: Throwable){
            System.err.println("Error while reading/sending: ${t.message}")
        }finally{
            producer.close()
        }
    }
}