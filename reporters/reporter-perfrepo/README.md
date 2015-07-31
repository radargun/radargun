PerfRepo reporter
=================
[Radargun] (https://github.com/radargun/radargun/wiki) reporter implementation providing functionality to upload test results to performance repository (PerfRepo).

## Configuration
Include reporter of `perfrepo` type in Radargun benchmark configuration (section `reports`). Individual tests that should be included in the uploading process need to be referenced via `perfrepo.tests` attribute. 
Use `<metric-name-mapping>` section to configure mapping between individual representation types and PerfRepo-defined metrics. Refer to `reporter-perfrepo-x.y.xsd` schema file generated in `target` folder 
to see all available options.

Example configuration

    <reports>
        <reporter type="perfrepo">
            <report>
                <perfrepo xmlns="urn:radargun:reporters:reporter-perfrepo:2.2"
                    perf-repo-host="perfrepo.host.com"
                    perf-repo-port="8080"
                    perf-repo-auth="base64authString"
                    perf-repo-test="my_perfrepo_test"
                    perf-repo-tag="custom-tag;perfrepo-test"
                    tests="stress-test1;stress-test2">
                    <metric-name-mapping>
                        <map operation="BasicOperations.Get" representation="response-time-mean" to="read_response_time_mean"/>
                        <map operation="BasicOperations.Get" representation="throughput-net" to="read_actual_throughput"/>
                        <map operation="BasicOperations.Put" representation="response-time-mean" to="write_response_time_mean"/>
                        <map operation="BasicOperations.Put" representation="throughput-net" to="write_actual_throughput"/>
                    </metric-name-mapping>
                </perfrepo>
            </report>
        </reporter>
    <reports>
