# Changelog

## Current development version (0.3.0-SNAPSHOT)

**New Features**
* replaces non-standard-conformant idTypes with default (IRI)
* removes View elements if present
* now correctly converts MultiLanguageProperty value

**Internal changes & Bugfixes**
* Fixed handling idType ID_SHORT
* Now correctly sets type of Reference for globalAssetId in assetInformation

## Release version 0.2.0

**New Features**
* add additional transformation to fix embedded data specifications with missing type information

**Internal changes & Bugfixes**
* Fixed wrong return code when converting single file (now correctly returning 0 instead of 1)
* Fixed error that occurs when creating single output file to which the parent folder does not exist yet


## Release version 0.1.0

First release!
