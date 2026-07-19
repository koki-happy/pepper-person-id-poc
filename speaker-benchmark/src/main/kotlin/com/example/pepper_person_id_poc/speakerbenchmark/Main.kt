package com.example.pepper_person_id_poc.speakerbenchmark

import com.example.pepper_person_id_poc.speakerbenchmark.cli.CliArgumentException
import com.example.pepper_person_id_poc.speakerbenchmark.cli.CliArgumentsParser
import com.example.pepper_person_id_poc.speakerbenchmark.cli.CliCommand
import com.example.pepper_person_id_poc.speakerbenchmark.cli.CliUsage
import com.example.pepper_person_id_poc.speakerbenchmark.config.BenchmarkConfigLoader
import com.example.pepper_person_id_poc.speakerbenchmark.model.ModelInspector
import com.example.pepper_person_id_poc.speakerbenchmark.model.OnnxModelInspector
import com.example.pepper_person_id_poc.speakerbenchmark.report.BenchmarkReportWriter
import com.example.pepper_person_id_poc.speakerbenchmark.validation.BenchmarkValidator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.PrintStream
import java.util.ServiceLoader
import kotlin.system.exitProcess

fun main(arguments: Array<String>) {
    val exitCode = BenchmarkCliApplication().run(arguments, System.out, System.err)
    if (exitCode != 0) exitProcess(exitCode)
}

class BenchmarkCliApplication(
    private val modelInspector: ModelInspector = OnnxModelInspector(),
    private val runnerProvider: () -> BenchmarkRunner = ::loadBenchmarkRunner,
) {
    private val json = Json {
        prettyPrint = true
    }

    fun run(arguments: Array<String>, output: PrintStream, error: PrintStream): Int {
        val command = try {
            CliArgumentsParser.parse(arguments)
        } catch (exception: CliArgumentException) {
            error.println("Argument error: ${exception.message}")
            error.println()
            error.println(CliUsage.text)
            return EXIT_USAGE
        }

        return try {
            when (command) {
                CliCommand.Help -> output.println(CliUsage.text)
                is CliCommand.InspectModel -> {
                    output.println(json.encodeToString(modelInspector.inspect(command.modelPath)))
                }

                is CliCommand.Run -> {
                    val config = BenchmarkConfigLoader.load(command.configPath)
                    val benchmark = BenchmarkValidator.validate(config)
                    if (command.validateOnly) {
                        output.println(json.encodeToString(benchmark.summary))
                    } else {
                        val results = runnerProvider().run(benchmark)
                        BenchmarkReportWriter.write(config.outputDirectory, results)
                        output.println("Benchmark results written to ${config.outputDirectory}")
                    }
                }
            }
            EXIT_SUCCESS
        } catch (exception: IllegalArgumentException) {
            error.println(exception.message ?: exception::class.simpleName)
            EXIT_DATA_ERROR
        } catch (exception: BenchmarkRunnerUnavailableException) {
            error.println(exception.message)
            EXIT_UNAVAILABLE
        } catch (exception: Exception) {
            error.println("Benchmark failed: ${exception.message ?: exception::class.simpleName}")
            EXIT_FAILURE
        }
    }

    private companion object {
        const val EXIT_SUCCESS = 0
        const val EXIT_FAILURE = 1
        const val EXIT_USAGE = 2
        const val EXIT_DATA_ERROR = 3
        const val EXIT_UNAVAILABLE = 4
    }
}

class BenchmarkRunnerUnavailableException(message: String) : IllegalStateException(message)

private fun loadBenchmarkRunner(): BenchmarkRunner {
    val runners = ServiceLoader.load(BenchmarkRunner::class.java).toList()
    return when (runners.size) {
        1 -> runners.single()
        0 -> throw BenchmarkRunnerUnavailableException(
            "No inference BenchmarkRunner provider is installed. Use --validate-only, or add exactly one " +
                "META-INF/services/${BenchmarkRunner::class.qualifiedName} provider.",
        )

        else -> throw BenchmarkRunnerUnavailableException(
            "Multiple inference BenchmarkRunner providers are installed: " +
                runners.joinToString { it::class.qualifiedName ?: it::class.simpleName.orEmpty() },
        )
    }
}
