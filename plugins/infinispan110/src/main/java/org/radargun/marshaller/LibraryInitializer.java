package org.radargun.marshaller;

import org.infinispan.protostream.SerializationContextInitializer;
import org.infinispan.protostream.annotations.AutoProtoSchemaBuilder;

@AutoProtoSchemaBuilder(
      includeClasses = {
            Book.class
      },
      schemaFileName = "book.proto",
      schemaFilePath = "proto/",
      schemaPackageName = "book_sample")
interface LibraryInitializer extends SerializationContextInitializer {
}