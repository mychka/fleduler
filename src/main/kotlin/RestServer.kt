package com.kiko.flatviewingscheduler

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.script.*

private typealias Handler = (context: HandleContext) -> Unit

class HandleContext(val pathParams: List<Long>, val queryParams: Map<String, String>, val bodyParams: Map<String, *>) {

    var responseCode: Int = 200

    private val sb = StringBuilder()

    fun append(any: Any): HandleContext {
        sb.append(any)
        return this
    }

    fun getBytes() = sb.toString().toByteArray()
}

private class RestContext(val path: Regex, val method: Method, val handler: Handler)

enum class Method {
    GET, POST, PUT, DELETE
}

object RestServer {

    const val PORT = 8081

    private val contexts = mutableListOf<RestContext>()

    fun registerContext(path: String, method: Method, handler: Handler): RestServer {
        contexts += RestContext(path.toRegex(), method, handler)
        return this
    }

    fun start() {
        HttpServer.create(InetSocketAddress(PORT), 0).apply {
            createContext("/") { exchange ->
                exchange.apply {
                    try {
                        val context = contexts.find { it.path.matches(requestURI.path) && it.method.name == requestMethod } ?: run {
                            // Didn't find a matching path pattern, return 404.
                            sendResponseHeaders(404, 0)
                            return@createContext
                        }

                        val handleContext = HandleContext(
                            context.path.matchEntire(requestURI.path)!!.groupValues.drop(1).map { it.toLong() },
                            parseQuery(requestURI.query),
                            requestBody.bufferedReader().readText().run { parseJson(this) }
                        )

                        context.handler(handleContext)

                        if (handleContext.responseCode in 200..299) {
                            responseHeaders.add("Content-type", "application/json")
                        }
                        val bytes = handleContext.getBytes()
                        sendResponseHeaders(handleContext.responseCode, bytes.size.toLong())
                        if (bytes.isNotEmpty()) { // Avoid exception on 204 status code.
                            responseBody.write(bytes)
                        }
                    } catch (e: Exception) {
                        sendResponseHeaders(
                            when (e) {
                                is UniqueConstraintException, is ForeignConstraintException, is IllegalArgumentException, is NullPointerException -> 400
                                else -> 500
                            }, 0
                        )
                    } finally {
                        exchange.close()
                    }
                }
            }
        }.start()
    }
}

private val script = (ScriptEngineManager().getEngineByName("JavaScript") as Compilable).compile("JSON.parse(x)")

private fun parseJson(json: String): Map<String, *> {
    return if (json.isEmpty()) emptyMap<String, Any>() else script.eval(
        SimpleScriptContext().apply { setAttribute("x", json, ScriptContext.ENGINE_SCOPE) }
    ) as Bindings
}

private fun parseQuery(query: String?): Map<String, String> = mutableMapOf<String, String>().apply {
    if (query == null) {
        emptyMap<String, String>()
    } else {
        query.split("&").forEach {
            val key2value = it.split("=")
            if (key2value.size == 2) {
                this[URLDecoder.decode(key2value[0], "UTF-8")] = URLDecoder.decode(key2value[1], "UTF-8")
            }
        }
    }
}
