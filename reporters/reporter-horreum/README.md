Horreum reporter
=================
[Radargun] (https://github.com/radargun/radargun/wiki) reporter implementation providing functionality to upload test results to Horreum.

## Configuration
Include reporter of `horreum` type in Radargun benchmark configuration (section `reports`).

Example configuration

```xml
<reporter type="horreum">
    <report>
        <horreum xmlns="urn:radargun:reporters:reporter-horreum:3.0"
                  horreum-url="https://horreum"
                  keycloak-url="https://horreum-keycloak"
                  keycloak-realm="horreum"
                  horreum-user="my-user"
                  horreum-password="my-pwd"
                  client-id="horreum-ui"
                  horreum-test="horreum-test"
                  horreum-owner="my-owner"
                  horreum-access="PUBLIC"
                  tags="mode:library;version:15.0.0-SNAPSHOT"
                  build-params-file="build.properties"
                  build-params="myparam1:1;myparam2:2">
        </horreum>
    </report>
</reporter>
```

`build.properties` are the key values used to benchmark. Example
```properties
java.version=openjdk
maven.version=Apache Maven 3.6.3
ispn.version=15.0.0-SNAPSHOT
build.dg-qe_git_commit=6bfcbb1a0bd92ad0cee697d1f5f13bb1dc339ad3
```