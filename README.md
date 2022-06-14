# Package Explorer Converter JSON

Converts AAS JSON files exported with AASX Package Explorer to a FA³ST-compatible version.

| :warning: **AASX Package Explorer uses AAS meta model v2.x while FA³ST uses v3.x. Converting a model might cause information loss!**<br>  **This toool is still expertimental and might fail for complex AAS model!**
|-----------------------------|

## Usage
```sh
java -jar package-explorer-converter-json-{version}.jar -i {path/to/exportedAAS.json} -o {path/to/output.json}
```

If no ouput file is provided, the output will be written to screen.

```
-i, --input=<inputFile>   The input file
-o, --output=<outputFile> The output file
-m, --merge               Merge all AAS models into single file (only applicable if input contains multiple files)
-d, --debug               Print additional debug information
-h, --help                Show this help message and exit.
-V, --version             Print version information and exit.
```

<p align="right">(<a href="#top">back to top</a>)</p>

## Contributors

| Name | Github Account |
|:--| -- |
| Michael Jacoby | [mjacoby](https://github.com/mjacoby) |

<p align="right">(<a href="#top">back to top</a>)</p>

## Contact

faaast@iosb.fraunhofer.de

<p align="right">(<a href="#top">back to top</a>)</p>

## License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.

Copyright (C) 2022 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131 Karlsruhe, Germany.

You should have received a copy of the Apache 2.0 License along with this program. If not, see https://www.apache.org/licenses/LICENSE-2.0.html.

<p align="right">(<a href="#top">back to top</a>)</p>
