package edu.unh.cs980.misc

import edu.unh.cs980.identity
import net.sourceforge.argparse4j.inf.Namespace
import net.sourceforge.argparse4j.inf.Subparser

/**
 * All of the following is used to construct a DSL that makes my life easier when it comes to adding stuff
 * to the arg parser. It uses builder notation from Kotlin. It's very abstract so I'm not sure how to comment
 * on how it works... but the point is that I can use this to specify the resources / arguments / methods to be
 * added to the arg parser in a seamless way.
 */

@DslMarker annotation class DispatchDSL

data class ResourceContainer(val arg: String, val help: String, val default: String = "", val loader: (String) -> Any)

data class MethodCaller<T> (val choice: String, val method: T.() -> Unit)

data class MethodContainer<T>(val arg: String, val help: String,
                              val methodMap: Map<String, List<MethodCaller<T>>>) {


    fun getMethod(methodType: String, methodName: String): (T.() -> Unit)? =
            methodMap[methodType]!!
                .find { methodCaller -> methodCaller.choice == methodName}
                ?.method


    fun getMethodChoices(methodType: String): List<String> =
        methodMap[methodType]!!.map { method -> method.choice }
}




class KotlinResourceDispatcher(val resourceContainers: List<ResourceContainer>,
                               val methodContainer: MethodContainer<*>? = null) {

    fun generateArguments(methodType: String, parser: Subparser) {
        methodContainer?.let { container ->
            parser.addArgument("--${container.arg}")
                .choices(container.getMethodChoices(methodType))
                .help(container.help)
        }
        resourceContainers.forEach { container ->
            parser.addArgument("--${container.arg}")
                .setDefault(container.default)
                .help(container.help)
        }
    }

    fun loadResources(namespace: Namespace): HashMap<String, Any> {
        val resources = HashMap<String, Any>()

        resourceContainers.forEach { container ->
            val resourceArgument = namespace.get<String>(container.arg)
            resources[container.arg] = container.loader(resourceArgument)
        }

        return resources
    }

    operator fun plus(other: KotlinResourceDispatcher): KotlinResourceDispatcher {
        return KotlinResourceDispatcher(resourceContainers + other.resourceContainers)
    }
}


@DispatchDSL
class MethodContainerBuilder<T>(val arg: String) {
    var help: String = ""
    private val methods = HashMap<String, ArrayList<MethodCaller<T>>>()

    fun method(category: String, choice: String, method: T.() -> Unit) {
        methods.computeIfAbsent(category, { arrayListOf() }).add(MethodCaller(choice, method))
    }

    fun build(): MethodContainer<T> {
        return MethodContainer(arg, help, methodMap = methods)
    }

}


@DispatchDSL
class ResourceContainerBuilder(val arg: String) {
    var help: String = ""
    var default: String = ""
    var loader: (String) -> Any = ::identity

    fun build(): ResourceContainer {
        return ResourceContainer(arg = arg, help = help, default = default, loader = loader)
    }
}


@DispatchDSL
class ResourceDispatchBuilder() {
    private val resourceContainers = ArrayList<ResourceContainer>()
    private var methodContainer: MethodContainer<*>?  = null

    fun build(): KotlinResourceDispatcher {
        return KotlinResourceDispatcher(resourceContainers = resourceContainers, methodContainer = methodContainer)
    }

    fun<T> methods(arg: String = "method", setup: MethodContainerBuilder<T>.() -> Unit) {
        val methodContainerBuilder = MethodContainerBuilder<T>(arg)
        methodContainerBuilder.setup()
        methodContainer = methodContainerBuilder.build()
    }

    fun resource(arg: String, setup: ResourceContainerBuilder.() -> Unit) {
        val resourceBuilder = ResourceContainerBuilder(arg)
        resourceBuilder.setup()
        resourceContainers += resourceBuilder.build()
    }
}


@DispatchDSL
fun buildResourceDispatcher(setup: ResourceDispatchBuilder.() -> Unit): KotlinResourceDispatcher {
    val dispatchBuilder = ResourceDispatchBuilder()
    dispatchBuilder.setup()
    return dispatchBuilder.build()
}

