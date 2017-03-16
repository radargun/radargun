---
---

Configurations
--------------

Configurations is the element of benchmark configuration file which lists all configurations that are to be ran on clusters.  
Each configuration has its own `config` element containing as many `setup` elemens as is necessary to cover all specified groups of any cluster. 

**Setup element attributes**
> plugin (**required**) - specifies which plugin will be ran on the group (or cluster if no groups are used)  
> group (**optional**) - specifies which group the setup is for, required if the groups are used in [clusters](./clusters.html) configuration

The plugins themselves define their own configuration format in their separate namespaces and therefore there is little general information to be said.