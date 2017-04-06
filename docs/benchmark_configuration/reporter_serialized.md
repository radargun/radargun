---
---

Serialized reporter
-------------------

Serialized reporter is an simple utility reporter used mostly for development of other reporters, but it does have other uses. It generates a set of .bin files (1 per configuration) containing a serialized Report object. These files can be subsequently used to generate report through other reporters again (or this one, but that seems rather pointless), possibly after programming or configuration change. This saves time on running the actual benchmark.

#### Parameters
* **targetDir**	- Directory where the results should be stored. Default is `results/serialized`.

### Usage

In order to re-generate reports from serialized files one has to provide the directory with the serialized files and a location of the configuration file to `bin/report.sh` [script]({{page.path_to_root}}getting_started/using_the_scripts.html), use parameter `-h` for help on actual usage. 
