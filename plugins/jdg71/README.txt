JBoss Data Grid 7.1.x RadarGun Plug-in
--------------------------------------

To install RadarGun with JDG (JBoss Data Grid) 7.1.x you need to download JDG 7.1.x binaries from Red Hat Customer Portal http://access.redhat.com/
section JBoss Enterprise Middleware Downloads, product "Data Grid", file "JBoss Data Grid 7.1.0 Maven Repository" (or later version)
and copy them to your local maven repository.

after that you need to rebuild RadarGun with

mvn clean install -Pjdg
