package org.radargun.service;

import org.radargun.Service;
import org.radargun.config.Property;

import java.nio.file.FileSystems;

@Service(doc = InfinispanServerService.SERVICE_DESCRIPTION)
public class Infinispan121ServerService extends Infinispan110ServerService {

    @Property(doc="Comma separated list of proto schema to be register. Example: book.proto,author.proto,user.proto")
    private String protobufFile;

    protected InfinispanServerLifecycle createServerLifecyle() {
        return new Infinispan121ServerLifecycle(this);
    }

    public String getProtobufFile() {
        return protobufFile;
    }
}
