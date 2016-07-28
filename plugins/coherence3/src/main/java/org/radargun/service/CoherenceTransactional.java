package org.radargun.service;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import com.tangosol.coherence.transaction.Connection;
import com.tangosol.coherence.transaction.ConnectionFactory;
import com.tangosol.coherence.transaction.DefaultConnectionFactory;
import com.tangosol.net.NamedCache;
import org.radargun.traits.Transactional;

public class CoherenceTransactional implements Transactional {
   protected final Coherence3Service service;
   protected volatile ConnectionFactory connectionFactory;

   public CoherenceTransactional(Coherence3Service service) {
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      return Configuration.TRANSACTIONS_ENABLED;
   }

   @Override
   public Transaction getTransaction() {
      ensureConnectionFactory();
      return new ConnectionTx();
   }

   private void ensureConnectionFactory() {
      if (connectionFactory == null) {
         synchronized (this) {
            if (connectionFactory == null) {
               if (service.connectionFactory == null) {
                  connectionFactory = new DefaultConnectionFactory();
               } else {
                  try {
                     connectionFactory = InitialContext.doLookup(service.connectionFactory);
                  } catch (NamingException e) {
                     throw new IllegalArgumentException("Failed to lookup connection factory", e);
                  }
               }
            }
         }
      }
   }

   protected class ConnectionTx implements Transaction {
      private Connection connection;

      public ConnectionTx() {
      }

      @Override
      public <T> T wrap(T resource) {
         if (resource == null) {
            return null;
         } else if (connection == null) {
            throw new IllegalStateException("Transaction had not begun yet");
         } else if (resource instanceof NamedCache) {
            String cacheName = ((NamedCache) resource).getCacheName();
            return (T) connection.getNamedCache(cacheName);
         } else if (resource instanceof CoherenceOperations.Cache) {
            String cacheName = ((CoherenceOperations.Cache) resource).cache.getCacheName();
            return (T) new CoherenceOperations.Cache(connection.getNamedCache(cacheName));
         } else if (resource instanceof CoherenceQueryable.QueryContextImpl) {
            String cacheName = ((CoherenceQueryable.QueryContextImpl) resource).cache.getCacheName();
            return (T) new CoherenceQueryable.QueryContextImpl(connection.getNamedCache(cacheName));
         } else {
            throw new IllegalArgumentException(String.valueOf(resource));
         }
      }

      @Override
      public void begin() {
         if (service.transactionalService == null) {
            this.connection = connectionFactory.createConnection();
         } else {
            connection = connectionFactory.createConnection(service.transactionalService);
         }
         connection.setAutoCommit(false);
      }

      @Override
      public void commit() {
         endTransaction(true);
      }

      @Override
      public void rollback() {
         endTransaction(false);
      }

      private void endTransaction(boolean successful) {
         if (connection == null) {
            throw new IllegalStateException("Transaction was not started");
         }
         try {
            if (successful) connection.commit();
            else connection.rollback();
         } finally {
            connection.close();
         }
      }
   }
}
