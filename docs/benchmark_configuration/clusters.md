---
---

Clusters
--------

Clusters is the elemement of benchmark configuration file which defines all clusters each configuration will be ran on. The clusters can be listed one by one, or defined as increments on scale, or both. Each cluster can be further divided into groups, usually to allow division into different roles (server-client) or configurations (smaller VM memory/thread limit) within the cluster.

#### Basic cluster

    <clusters>
      <cluster size="2" />
    </clusters>

This configuration defines one cluster of 2 machines.

#### Scaled clusters

    <clusters>
      <scale from="4" to="12" inc="4">
        <cluster />
      </scale>
    </clusters>

This configuration defines three clusters of sizes 4, 8 and 12. `Size` property for cluster is set by `scale` element for each incerement.

#### Grouped clusters

    <clusters>
      <cluster size="4">
        <group name="clients" size="2"/>
        <group name="servers" size="2"/>
      </cluster> 
      <cluster size="5">
        <group name="clients" size="2"/>
        <group name="servers" size="3"/>
      </cluster>
    </clusters>

This configuration defines two clusters, each contaning two groups. `Name` and `size` properties are required for `group` elements. `Name` property is reqired to be unique withing the cluster. 
The `size` property of the `cluster` element has to be the sum of `size` properties of all groups within that cluster (after evaluation - more below), otherwise an error will occur during configuration parsing.

#### Scaled grouped clusters

    <clusters>
      <scale from="3" to="15" inc="6">
        <cluster>
          <group name="clients" size="#{${cluster.size}/3}"/>
          <group name="servers" size="#{${cluster.size}/3}"/>
          <group name="monitors" size="#{${cluster.size}/3}"/>
        </cluster> 
      </scale>
    </clusters>

This configuration defines three clusters of sizes 3, 9 and 15, each having three groups with equal numbers of slaves. `Scale` element sets a `cluster.size` [property](./properties.html) for each increment, which can be used for group naming (**discouraged**) and size definition.

#### A bit more complex example

    <clusters>
      <cluster size="2">
        <group name="clients" size="1"/>
        <group name="servers" size="1"/>
      </cluster> 
      <scale from="3" to="7" inc="2">
        <cluster>
          <group name="clients" size="#{${cluster.size}-2}"/>
          <group name="servers" size="2"/>
        </cluster> 
        <cluster>
          <group name="clients" size="2"/>
          <group name="servers" size="#{${cluster.size}-2}"/>
        </cluster> 
      </scale>
    </clusters>

This configuration defines a total of 7 clusters with 2 groups each, one of size 2 and two for each scale increment - 3, 5 and 7. Each scale-generated cluster will have one of the groups size set to 2 and other size set to remainder after subtraction 2 from cluster size.

### Notes

* `scale` increment defaults to 1
* `cluster` element inside `scale` has `size` property set by scale value only, no other change possible
