The idea of this plugin is to create a worker that can:
* monitor remote machine
* execute bash
* grep logs

#Monitor remote machine

```xml
<benchmark xmlns="urn:radargun:benchmark:3.0">
    <main bindAddress="${main.address:127.0.0.1}" port="${main.port:2103}"/>
    <clusters>
        <cluster size="3">
            <group name="echo" size="2" />
            <group name="client" size="1" />
        </cluster>
    </clusters>
    <configurations>

        <config name="my-example">
            <setup group="echo" plugin="echo">
                <echo xmlns="urn:radargun:plugins:${env.PLUGINNAME}:3.0" />
            </setup>
            <setup group="client" plugin="${env.PLUGINNAME}">
                <hotrod .....
            </setup>
        </config>

    </configurations>
    <init/>
    <rg:scenario ....

        <service-start />
        <remote-monitor-start jmx-service-url="service:jmx:rmi:///jndi/rmi://127.0.0.1:9999/jmxrmi" groups="echo" workers="0"/>
        <remote-monitor-start jmx-service-url="service:jmx:rmi:///jndi/rmi://127.0.0.1:9998/jmxrmi" groups="echo" workers="1"/>
        <monitor-start groups="client"/>

        <l:basic-operations-test ...

        <jvm-monitor-stop />
        <service-stop />
```