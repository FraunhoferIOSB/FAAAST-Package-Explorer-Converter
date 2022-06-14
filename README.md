# FA³ST Package Explorer Converter
![FA³ST Logo Light](./misc/images/Fa3st_positiv.png/#gh-light-mode-only "FA³ST Logo")
![FA³ST Logo Dark](./misc/images/Fa3st_negativ.png/#gh-dark-mode-only "FA³ST Logo")
Converts AAS JSON files created with/exported from AASX Package Explorer to a FA³ST-compatible version.

| :warning: **AASX Package Explorer uses AAS meta model v2.x while FA³ST uses v3.x. Converting a model might cause information loss!**<br>  **This toool is still expertimental and might fail for complex AAS model!**
|-----------------------------|

## Supported Version
The FA³ST Package Explorer Converter supports AAS JSON files created with/exported from AASX Package Explorer with version `AASX Package Explorer 2022-05-10.alpha`.

## Usage

[Download latest version](https://search.maven.org/remote_content?g=de.fraunhofer.iosb.ilt.faaast.service&a=package-explorer-converter&v=LATEST)

```
-i, --input=<inputFile>   Input file or directory (mandatory)
-o, --output=<outputFile> Output file or directory
-m, --merge               Merge all AAS models into single file (only applicable if input contains multiple files)
-d, --debug               Print additional debug information
-h, --help                Show this help message and exit.
-V, --version             Print version information and exit.
```

If no ouput file is provided, the output will be written to screen.

<p align="right">(<a href="#top">back to top</a>)</p>

### Example: Convert single file

```sh
java -jar package-explorer-converter-{version}.jar -i {path/to/exportedAAS.json} -o {path/to/output.json}
```

### Example: Convert and merge multiple files

```sh
java -jar package-explorer-converter-{version}.jar -i {path/to/files/} -o {path/to/output/} --merge
```

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
