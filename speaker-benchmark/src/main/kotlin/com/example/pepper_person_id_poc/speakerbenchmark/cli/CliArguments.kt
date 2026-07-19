package com.example.pepper_person_id_poc.speakerbenchmark.cli

import java.nio.file.Path

sealed interface CliCommand {
    data object Help : CliCommand

    data class Run(
        val configPath: Path,
        val validateOnly: Boolean,
    ) : CliCommand

    data class InspectModel(
        val modelPath: Path,
    ) : CliCommand
}

class CliArgumentException(message: String) : IllegalArgumentException(message)

object CliArgumentsParser {
    fun parse(arguments: Array<String>): CliCommand {
        var configPath: Path? = null
        var inspectModelPath: Path? = null
        var validateOnly = false
        var help = false

        var index = 0
        while (index < arguments.size) {
            val argument = arguments[index]
            when {
                argument == "--help" || argument == "-h" -> {
                    help = true
                    index += 1
                }

                argument == "--validate-only" -> {
                    if (validateOnly) {
                        throw CliArgumentException("--validate-only may only be supplied once")
                    }
                    validateOnly = true
                    index += 1
                }

                argument == "--config" -> {
                    if (configPath != null) {
                        throw CliArgumentException("--config may only be supplied once")
                    }
                    configPath = Path.of(requireValue(arguments, index, "--config"))
                    index += 2
                }

                argument.startsWith("--config=") -> {
                    if (configPath != null) {
                        throw CliArgumentException("--config may only be supplied once")
                    }
                    configPath = Path.of(requireInlineValue(argument, "--config"))
                    index += 1
                }

                argument == "--inspect-model" -> {
                    if (inspectModelPath != null) {
                        throw CliArgumentException("--inspect-model may only be supplied once")
                    }
                    inspectModelPath = Path.of(requireValue(arguments, index, "--inspect-model"))
                    index += 2
                }

                argument.startsWith("--inspect-model=") -> {
                    if (inspectModelPath != null) {
                        throw CliArgumentException("--inspect-model may only be supplied once")
                    }
                    inspectModelPath = Path.of(requireInlineValue(argument, "--inspect-model"))
                    index += 1
                }

                argument.startsWith("-") -> throw CliArgumentException("Unknown option: $argument")
                else -> throw CliArgumentException("Unexpected positional argument: $argument")
            }
        }

        if (help) {
            if (arguments.size != 1) {
                throw CliArgumentException("--help cannot be combined with other options")
            }
            return CliCommand.Help
        }

        if (inspectModelPath != null) {
            if (configPath != null || validateOnly) {
                throw CliArgumentException("--inspect-model cannot be combined with --config or --validate-only")
            }
            return CliCommand.InspectModel(inspectModelPath)
        }

        val requiredConfigPath = configPath
            ?: throw CliArgumentException("--config is required")
        return CliCommand.Run(requiredConfigPath, validateOnly)
    }

    private fun requireValue(arguments: Array<String>, optionIndex: Int, optionName: String): String {
        val value = arguments.getOrNull(optionIndex + 1)
            ?: throw CliArgumentException("$optionName requires a value")
        if (value.isBlank() || value.startsWith("-")) {
            throw CliArgumentException("$optionName requires a non-empty value")
        }
        return value
    }

    private fun requireInlineValue(argument: String, optionName: String): String {
        val value = argument.substringAfter('=', missingDelimiterValue = "")
        if (value.isBlank()) {
            throw CliArgumentException("$optionName requires a non-empty value")
        }
        return value
    }
}

object CliUsage {
    val text: String = """
        Usage:
          speaker-benchmark --config <config.json> [--validate-only]
          speaker-benchmark --inspect-model <model.onnx>
          speaker-benchmark --help

        Options:
          --config <path>         Read benchmark settings from a strict JSON document.
          --validate-only         Validate config, model artifacts, manifest, and WAV files without inference.
          --inspect-model <path>  Print stable file metadata for one model artifact.
          --help, -h              Show this help.
    """.trimIndent()
}
