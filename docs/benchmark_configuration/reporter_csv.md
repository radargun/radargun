---
---

CSV reporter
------------

CSV reporter is used to store benchmark data into .csv files, usually for further processing.

#### Properties
* **targetDir**		- Directory into which will be report files written. Default is `results/csv`.
* **ignore**		- List of indexes of slaves whose results will be ignored. Default is none.
* **separator**		- Separator of columns in the CSV file. Default is ','
* **computeTotal**	- Compute aggregated statistics from all nodes. Default is true
* **percentiles**	- List of percentiles to compute response times at. Default is 95% and 99%.
* **columnOrder**	- List od comma separated column name regex patterns which should be reordered to the left side, use '\\\\' to escape the commas if needed. Default is ".*[Put\|Get]\\\\.Throughput", ".*[Put\|Get]\\\\.ResponseTimeMean" and ".*Get.*"
