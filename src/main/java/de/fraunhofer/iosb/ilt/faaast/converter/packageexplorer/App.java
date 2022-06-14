/*
 * Copyright (c) 2021 Fraunhofer IOSB, eine rechtlich nicht selbstaendige
 * Einrichtung der Fraunhofer-Gesellschaft zur Foerderung der angewandten
 * Forschung e.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.fraunhofer.iosb.ilt.faaast.converter.packageexplorer;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.LevelFilter;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.FilterReply;
import io.adminshell.aas.v3.dataformat.DeserializationException;
import io.adminshell.aas.v3.dataformat.SerializationException;
import io.adminshell.aas.v3.dataformat.json.JsonDeserializer;
import io.adminshell.aas.v3.dataformat.json.JsonSerializer;
import io.adminshell.aas.v3.model.AssetAdministrationShellEnvironment;
import io.adminshell.aas.v3.model.impl.DefaultAssetAdministrationShellEnvironment;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Option;


@CommandLine.Command(name = "FA³ST Package Explorer JSON Converter", mixinStandardHelpOptions = true, description = "Converts AAS JSON files exported with Package Explorer to a FA³ST-compatible version.", version = "0.0.1-SNAPSHOT", usageHelpAutoWidth = true)
public class App implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(App.class);
    private static final String JSON_FILE_EXTENSION = ".json";
    private static final String MERGE_FILE_NAME = "merged" + JSON_FILE_EXTENSION;

    @Option(names = {
            "-i",
            "--input"
    }, description = "input file or directory", required = true)
    private File input = null;

    @Option(names = {
            "-o",
            "--output"
    }, description = "output file or directory")
    private File output = null;

    @Option(names = {
            "-d",
            "--debug"
    }, description = "print additional debug information")
    private boolean debug;

    @Option(names = {
            "-m",
            "--merge"
    }, description = "merge all AAS models into a single file called '" + MERGE_FILE_NAME
            + "' additionally to converting each file seperately (only applicable if input contains multiple files)")
    private boolean merge;

    public static void main(String[] args) throws ScriptException, DeserializationException, SerializationException {
        int exitCode = new CommandLine(new App()).execute(args);
        System.exit(exitCode);
    }


    private Integer convertSingleFile() {
        if (merge) {
            LOGGER.warn("Merging not supported when converting single file - command will be ignored");
        }
        return convert(input, output) != null ? 1 : 0;
    }


    private boolean output(AssetAdministrationShellEnvironment env, File outputFile) {
        if (outputFile == null) {
            LOGGER.info("");
            try {
                LOGGER.info("Result: \n{}", new JsonSerializer().write(env));
            }
            catch (SerializationException e) {
                LOGGER.error("Serialization with FA³ST failed", e);
            }
        }
        else {
            try {
                new JsonSerializer().write(outputFile, env);
            }
            catch (IOException | SerializationException e) {
                LOGGER.error("Error writing to output file", e);
                return false;
            }
            LOGGER.info("Output written to {}", outputFile);
        }
        return true;
    }


    private AssetAdministrationShellEnvironment convert(File inputFile, File outputFile) {
        LOGGER.info("Input file: {}", inputFile);
        if (outputFile != null) {
            LOGGER.info("Output file: {}", outputFile);
        }
        LOGGER.info("");
        AssetAdministrationShellEnvironment aasConverted = null;
        try {
            aasConverted = new JsonDeserializer().read(inputFile);
            LOGGER.info("File is already FA³ST-compliant");
        }
        catch (DeserializationException | FileNotFoundException e) {
            // ignore
        }
        if (aasConverted == null) {
            LOGGER.info("Converting file...");
            String converted = "";
            try {
                converted = new String(PackageExplorerConverter.toFaaast(new FileInputStream(inputFile)).readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (FileNotFoundException e) {
                LOGGER.error("Input file not found", e);
                return null;
            }
            catch (IOException e) {
                LOGGER.error("Error reading input file", e);
                return null;
            }
            LOGGER.info("Testing deserialization with FA³ST...");
            try {
                aasConverted = new JsonDeserializer().read(converted);
            }
            catch (DeserializationException e) {
                LOGGER.warn("Conversion result could not be deserialized using FA³ST", e);
                return null;
            }
            LOGGER.info("Conversion successfully finished");
        }
        return output(aasConverted, outputFile)
                ? aasConverted
                : null;
    }


    private Integer convertBatch() {
        LOGGER.info("Scanning input directory '{}' for JSON files...:", input);
        File[] inputFiles = input.listFiles((File file, String name) -> name.endsWith(JSON_FILE_EXTENSION));
        LOGGER.info("Found {} files in input directory:{}{}",
                inputFiles.length,
                System.lineSeparator(),
                Stream.of(inputFiles).map(x -> x.getName()).collect(Collectors.joining(System.lineSeparator())));
        if (output != null) {
            if (!output.exists()) {
                if (!output.mkdirs()) {
                    LOGGER.error("output directory '{}' could not be created", output);
                    return 1;
                }
            }
            if (!output.isDirectory()) {
                LOGGER.error("Output is not a directory! When using batch mode, output must be a directory or be omitted");
                return 1;
            }
        }
        List<File> failures = new ArrayList<>();
        List<AssetAdministrationShellEnvironment> aass = new ArrayList<>();
        for (int i = 0; i < inputFiles.length; i++) {
            File in = inputFiles[i];
            File out = output != null
                    ? new File(output, in.getName())
                    : null;
            LOGGER.info("Processing file [{}/{}]: '{}'...:", i + 1, inputFiles.length, in.getName());
            try {
                LOGGER.info("");
                AssetAdministrationShellEnvironment conversionResult = convert(in, out);
                if (conversionResult == null) {
                    failures.add(in);
                }
                else {
                    aass.add(conversionResult);
                }
                LOGGER.info("");
                LOGGER.info("");
            }
            catch (Exception e) {
                LOGGER.error("unexpected error while converting", e);
                return 1;
            }
        }
        LOGGER.info("Successfully converted [{}/{}] files", inputFiles.length - failures.size(), inputFiles.length);
        if (merge && failures.isEmpty() && aass.size() > 1) {
            File mergeFile = new File(output, MERGE_FILE_NAME);
            LOGGER.info("Merging files...");
            Map<String, Integer> duplicateCounter = new HashMap<>();
            AssetAdministrationShellEnvironment mergeResult = aass.stream().reduce((a, b) -> merge(a, b, duplicateCounter)).get();
            duplicateCounter.forEach((id, count) -> LOGGER.warn("Found {} elements with same identifier but different content (Identifier: {})", count, id));
            output(mergeResult, mergeFile);
        }
        else if (!failures.isEmpty()) {
            LOGGER.info("The following files could not be converted: {}{}",
                    System.lineSeparator(),
                    failures.stream().map(x -> x.getName()).collect(Collectors.joining(System.lineSeparator())));
            if (merge) {
                LOGGER.info("Merging will not be performed because of conversion errors.");
            }
        }
        return 0;
    }


    private static <T, U> List<T> merge(List<T> coll1, List<T> coll2, Function<T, U> idExtractor, Map<U, Integer> duplicateCounter) {
        coll1.stream()
                .forEach(a -> {
                    U id = idExtractor.apply(a);
                    Optional<T> duplicate = coll2.stream()
                            .filter(b -> Objects.equals(id, idExtractor.apply(b)))
                            .findFirst();
                    if (duplicate.isPresent() && !Objects.equals(a, duplicate.get())) {
                        int count = duplicateCounter.containsKey(id)
                                ? duplicateCounter.get(id)
                                : 0;
                        count++;
                        duplicateCounter.put(id, count);
                    }
                });
        return Stream.concat(coll1.stream(), coll2.stream()).distinct().collect(Collectors.toList());
    }


    private static AssetAdministrationShellEnvironment merge(AssetAdministrationShellEnvironment aas1, AssetAdministrationShellEnvironment aas2,
                                                             Map<String, Integer> duplicateCounter) {
        return new DefaultAssetAdministrationShellEnvironment.Builder()
                .assetAdministrationShells(
                        merge(aas1.getAssetAdministrationShells(), aas2.getAssetAdministrationShells(), x -> x.getIdentification().getIdentifier(), duplicateCounter))
                .conceptDescriptions(merge(aas1.getConceptDescriptions(), aas2.getConceptDescriptions(), x -> x.getIdentification().getIdentifier(), duplicateCounter))
                .submodels(merge(aas1.getSubmodels(), aas2.getSubmodels(), x -> x.getIdentification().getIdentifier(), duplicateCounter))
                .build();
    }


    private static void enableDebug() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        LevelFilter filter = new LevelFilter();
        filter.setLevel(Level.DEBUG);
        filter.setOnMatch(FilterReply.ACCEPT);
        filter.setOnMismatch(FilterReply.ACCEPT);
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern("%date{yyyy-MM-dd HH:mm:ss} %msg %n");
        encoder.setContext(context);
        encoder.start();
        ConsoleAppender appender = new ConsoleAppender();
        appender.addFilter(filter);
        appender.setEncoder(encoder);
        appender.setContext(context);
        appender.start();
        ch.qos.logback.classic.Logger logger = context.getLogger(PackageExplorerConverter.class);
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(true);
        logger.addAppender(appender);
    }


    @Override
    public Integer call() {
        printHeader();
        if (debug) {
            enableDebug();
        }
        return input.isDirectory()
                ? convertBatch()
                : convertSingleFile();
    }


    private void printHeader() {
        LOGGER.info("             _____                                                      ");
        LOGGER.info("            |___ /                                                      ");
        LOGGER.info("  ______      |_ \\   _____ _______                                      ");
        LOGGER.info(" |  ____/\\   ___) | / ____|__   __|                                     ");
        LOGGER.info(" | |__ /  \\ |____/ | (___    | |                                        ");
        LOGGER.info(" |  __/ /\\ \\        \\___ \\   | |                                        ");
        LOGGER.info(" | | / ____ \\       ____) |  | |                                        ");
        LOGGER.info(" |_|/_/    \\_\\     |_____/   |_|                                        ");
        LOGGER.info("  ___         _                   ___          _                        ");
        LOGGER.info(" | _ \\__ _ __| |____ _ __ _ ___  | __|_ ___ __| |___ _ _ ___ _ _        ");
        LOGGER.info(" |  _/ _` / _| / / _` / _` / -_) | _|\\ \\ / '_ \\ / _ \\ '_/ -_) '_|       ");
        LOGGER.info(" |_| \\__,_\\__|_\\_\\__,_\\__, \\___| |___/_\\_\\ .__/_\\___/_| \\___|_|         ");
        LOGGER.info("                      |___/              |_|                            ");
        LOGGER.info("     _ ___  ___  _  _    ___                     _                      ");
        LOGGER.info("  _ | / __|/ _ \\| \\| |  / __|___ _ ___ _____ _ _| |_ ___ _ _            ");
        LOGGER.info(" | || \\__ \\ (_) | .` | | (__/ _ \\ ' \\ V / -_) '_|  _/ -_) '_|           ");
        LOGGER.info("  \\__/|___/\\___/|_|\\_|  \\___\\___/_||_\\_/\\___|_|  \\__\\___|_|             ");
        LOGGER.info("");
        LOGGER.info("");
    }
}
