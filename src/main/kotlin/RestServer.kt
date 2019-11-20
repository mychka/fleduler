package com.kiko.flatviewingscheduler

import com.sun.net.httpserver.HttpServer
import java.lang.reflect.InvocationTargetException
import java.net.InetSocketAddress
import java.net.URLDecoder
import javax.script.*
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.primaryConstructor

enum class Method {
    GET, POST, PUT, DELETE
}

@Target(AnnotationTarget.FUNCTION)
@Retention
annotation class Mapping(val value: String, val method: Method = Method.GET) {
}

class HandleContext(val pathParams: List<Long>, val queryParams: Map<String, String>, val bodyParams: Map<String, *>) {

    var responseCode: Int = 200

    private val sb = StringBuilder()

    fun append(any: Any): HandleContext = apply { sb.append(any) }

    fun getBytes() = sb.toString().toByteArray()
}

private class RestMapping(val path: Regex, val method: Method, val handler: (HandleContext) -> Unit)

open class Controller(private val context: HandleContext) {

    val bodyParams: Map<String, *>
        get() = context.bodyParams

    val pathParams: List<Long>
        get() = context.pathParams

    val queryParams: Map<String, String>
        get() = context.queryParams

    var responseCode: Int
        get() = context.responseCode
        set(value) {
            context.responseCode = value
        }

    fun append(any: Any): Controller = apply { context.append(any) }
}

object RestServer {

    const val PORT = 8081

    private val contexts = mutableListOf<RestMapping>()

    fun registerController(clazz: KClass<out Controller>) = apply {
        contexts += clazz.members.mapNotNull { callable ->
            callable.findAnnotation<Mapping>()?.run {
                RestMapping(value.toRegex(), method) {
                    try {
                        callable.call(
                            clazz.primaryConstructor!!.call(it)
                        )
                    } catch (e: InvocationTargetException) {
                        throw e.targetException
                    }
                }
            }
        }
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
