---
---

Reports
-------

`Reports` element of the benchmark configuration file holds a set of `reporter` elements which define separate reporter configurations. When all scenarios are processed the gathered data is passed on to each reporter to convert into reporters format.  
  
Default reporters (csv, html and serialized) generally work ok out of the box, but arguments can be, somewhat ambiguously for historic reasons, passed inside `reporter` element in tags named after them (see examples). Reporters can also be instructed to produce multiple reports using `report` tag.

**Reporter element attributes**
> type (**required**) - specifies reporter used

#### Basic CSV reporter

    <reports xmlns="urn:radargun:reporters:reporter-default:3.0">
      <reporter type="serialized"/>
    </reports>

For this benchmark the data will be processed by default serialized reporter.

#### Parametrized reporter

    <reports xmlns="urn:radargun:reporters:reporter-default:3.0">
      <reporter type="csv">
        <csv>
          <compute-total>false</compute-total>
        </csv>
      </reporter>
    </reports>

For this benchmark the data will be processed by CSV reporter with no totals computed.

#### Parametrized reporter with multiple reports

    <reports xmlns="urn:radargun:reporters:reporter-default:3.0">
      <reporter type="html">
        <html>
          <target-dir>/home/reporter/reports</target-dir>
        </html>
        <report>
          <html>
            <timeline>
              <chart-width>1000</chart-width>
            </timeline>
            <target-dir>/home/reporter/reports/longer_timelines</target-dir>
          </html>
        </report>
        <report>
          <html>
            <timeline>
              <chart-width>200</chart-width>
            </timeline>
          </html>
        </report>
      </reporter>
    </reports>

For this benchmark HTML reporter will produce two reports, one with charts of width 1000, stored into longer_timelines folder, other with charts of width 200, stored into reports folder.

* When reporter-specific configuration is placed directly under `reporter` element it acts as default settings override for the whole reporter, when placed under `report` element it is used specifically for that one report
* It logically follows, but to remove any ambiguity: when no `report` element is specified for the reporter it acs exactly the same as if one was specified with no arguments

