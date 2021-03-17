package org.radargun.marshaller;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;
import org.infinispan.protostream.annotations.ProtoDoc;

import java.io.Serializable;

@ProtoDoc("@Indexed")
public class Book implements Serializable {

   @ProtoDoc("@Field(index=Index.YES, analyze = Analyze.YES, store = Store.NO)")
   @ProtoField(number = 1)
   final String title;

   @ProtoFactory
   public Book(String title) {
      this.title = title;
   }

   public String getTitle() {
      return title;
   }

   @Override
   public String toString() {
      return "Book{" + title + '}';
   }
}
