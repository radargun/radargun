---
---

HTML reporter
-------------

HTML reporter generates a complete set of HTML reports with comparison charts linked by index.html file. 

#### Properties
* **targetDir**	- Directory to put the reports. Default is `results/html`.
* testReport - child element
    * **separate-cluster-charts**	- Generate separate charts for different cluster sizes. Default is false.
    * **combined-tests**		- List of test names that should be reported together. Default is empty.
    * **histogram-buckets**		- Number of bars the histogram chart will show. Default is 40.
    * **histogram-percentile**		- Percentage of fastest responses that will be presented in the chart. Default is 99%.
    * **histogram-chart-width**		- Width of the histogram chart in pixels. Default is 800.
    * **histogram-chart-height**	- Height of the histogram chart in pixels. Default is 600.
    * **percentiles**			- Show response time at certain percentiles. Default is 95% and 99%.
    * **generate-node-stats**		- Generate statistics for each node (expandable menu). Default is true.
    * **generate-thread-stats**		- Generate statistics for each thread (expandable menu). Default is false.
    * **highlight-suspects**		- Highlight suspicious results in the report. Default is true.
* timeline - child element
    * **chart-width**	- Width of the chart in pixels. Default is 1024.
    * **chart-height**	- Height of the chart in pixels. Default is 500.
