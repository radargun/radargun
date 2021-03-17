package org.radargun.marshaller;

import org.infinispan.protostream.annotations.ProtoFactory;
import org.infinispan.protostream.annotations.ProtoField;

public class Book {

   @ProtoField(number = 1)
   final String title;

   @ProtoFactory
   public Book(String text) {
      this.title = text;
   }

   public String getTitle() {
      return title;
   }

   @Override
   public String toString() {
      return "Book{" + title + '}';
   }
}
