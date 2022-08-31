package org.radargun.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.jgroups.Header;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.blocks.RequestCorrelator;
import org.jgroups.conf.ClassConfigurator;
import org.jgroups.util.Util;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.Clustered;
import org.radargun.traits.Lifecycle;
import org.radargun.traits.ProvidesTrait;
import org.radargun.utils.StringArrayConverter;

@Service(doc = "JGroupsService faking cache operations")
public class JGroups42Service implements Lifecycle, Clustered, BasicOperations.Cache {

   private static final short REPLY_FLAGS =
         (short) (Message.Flag.NO_FC.value() | Message.Flag.OOB.value() |
               Message.Flag.NO_TOTAL_ORDER.value() | Message.Flag.DONT_BUNDLE.value());

   protected static final short HEADER_ID = ClassConfigurator.getProtocolId(RequestCorrelator.class);

   private ExecutorService executor = Executors.newFixedThreadPool(200);

   @Property(name = "file", doc = "Configuration file for JGroups.")
   protected String configFile;

   @Property(doc = "Flags that will be used in all requests.", converter = StringArrayConverter.class)
   protected String[] flags;

   @Property(doc = "Transient flags that will be used in all requests.", converter = StringArrayConverter.class)
   protected String[] transientFlags;

   @Property(doc = "replicated.")
   protected boolean replicated = true;

   @Property(name = "nThreads", doc = "nThreads")
   protected int nThreads = 200;

   @Property(name = "nThreads", doc = "nThreads")
   protected boolean sendResponse = false;

   protected JChannel ch;
   protected JGroupsReceiver receiver;
   protected AtomicLong requestId = new AtomicLong(1);

   @ProvidesTrait
   public JGroups42Service getSelf() {
      return this;
   }

   @ProvidesTrait
   public BasicOperations createOperations() {
      return new BasicOperations() {
         @Override
         public <K, V> Cache<K, V> getCache(String cacheName) {
            return JGroups42Service.this;
         }
      };
   }

   @Override
   public boolean isCoordinator() {
      View view = ch.getView();
      return view == null || view.getMembers() == null || view.getMembers().isEmpty()
            || ch.getAddress().equals(view.getMembers().get(0));
   }

   @Override
   public Collection<Member> getMembers() {
      if (receiver.getMembershipHistory().isEmpty()) return null;
      return receiver.getMembershipHistory().get(receiver.getMembershipHistory().size() - 1).members;
   }

   @Override
   public List<Membership> getMembershipHistory() {
      return new ArrayList<>(receiver.getMembershipHistory());
   }

   @Override
   public void start() {
      executor = Executors.newFixedThreadPool(nThreads);
      try {
         ch = new JChannel(configFile);
         receiver = new JGroups36Receiver(ch);
         setReceiver();
         ch.connect("x");
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      receiver.updateLocalAddr(ch.getAddress());
      receiver.updateMyRank(Util.getRank(ch.getView(), ch.getAddress()) - 1);
   }

   @Override
   public void stop() {
      Util.close(ch);
      synchronized (this) {
         receiver.getMembershipHistory().add(Membership.empty());
      }
   }

   @Override
   public boolean isRunning() {
      return ch != null && ch.isConnected();
   }

   @Override
   public Object get(Object key) {
      throw new UnsupportedOperationException("");
   }

   @Override
   public boolean containsKey(Object key) {
      throw new UnsupportedOperationException("");
   }

   @Override
   public void put(Object key, Object value) {
      if (replicated) {
         if (nThreads > 1) {
            executor.execute(() -> {
               Map kv = new HashMap();
               kv.put(key, value);
               Message message = newMessage(kv);
               sendMessage(message);
            });
         } else {
            Map kv = new HashMap();
            kv.put(key, value);
            Message message = newMessage(kv);
            sendMessage(message);
         }
      } else {
         throw new UnsupportedOperationException("");
      }
   }

   @Override
   public Object getAndPut(Object key, Object value) {
      throw new UnsupportedOperationException("");
   }

   @Override
   public boolean remove(Object key) {
      throw new UnsupportedOperationException("");
   }

   @Override
   public Object getAndRemove(Object key) {
      throw new UnsupportedOperationException("");
   }

   @Override
   public void clear() {
      throw new UnsupportedOperationException("");
   }

   // IncompatibleClassChangeError
   protected void setReceiver() {
      ch.setReceiver(new Receiver() {
         @Override
         public void receive(Message message) {

            if (sendResponse && message.getSrc() != null && !message.getSrc().equals(ch.getAddress())) {
               RequestCorrelator.Header header = message.getHeader(HEADER_ID);
               if (header != null && header.requestId() > 0) {
                  Message response = new Message(message.getSrc()).setFlag(REPLY_FLAGS);
                  try {
                     ch.send(response);
                  } catch (Exception e) {
                     throw new RuntimeException(e);
                  }
               }
            }
         }
         @Override
         public void viewAccepted(View newView) {
            receiver.viewAccepted(newView);
         }
      });
   }

   // IncompatibleClassChangeError
   protected void sendMessage(Message message) {
      try {
         if (ch.isConnected()) {
            ch.send(message);
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   // IncompatibleClassChangeError
   protected Message newMessage(Object object) {
      Message message = new Message();
      for (String flag : flags) {
         message.setFlag(Message.Flag.valueOf(flag));
      }
      for (String transientFlag : transientFlags) {
         message.setTransientFlag(Message.TransientFlag.valueOf(transientFlag));
      }
      Header header = new RequestCorrelator.Header((byte) 0, requestId.getAndIncrement(), (short) 0);
      message.putHeader(HEADER_ID, header);
      message.setObject(object);
      return message;
   }
}
