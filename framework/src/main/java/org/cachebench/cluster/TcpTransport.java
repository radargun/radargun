package org.cachebench.cluster;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.config.ClusterConfig;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * note: this code is copyed and adapted from jgroups test.
 *
 * @author Bela Ban Jan 22
 * @author 2004
 * @version $Id: TcpTransport.java,v 1.16 2006/12/19 08:51:46 belaban Exp $
 */
public class TcpTransport implements Transport
{

   private static Log log = LogFactory.getLog(TcpTransport.class);

   Receiver receiver = null;
   ClusterConfig config = null;
   int max_receiver_buffer_size = 500000;
   int max_send_buffer_size = 500000;
   List<InetSocketAddress> nodes;
   ConnectionTable connectionTable;
   int startPort = 7800;
   ServerSocket srvSock = null;
   InetAddress bindAddr = null;
   InetSocketAddress localAddr = null;
   List receivers = new ArrayList();
   private ServerSocket server;
   private boolean isStoping;


   public TcpTransport()
   {
   }

   public Object getLocalAddress()
   {
      return localAddr;
   }

   public void create(ClusterConfig clusterConfig) throws Exception
   {
      this.config = clusterConfig;
      startPort = config.getPortForThisNode();
      String bindAddrStr = config.getAddressForThisNode().getHost();
      if (bindAddrStr == null) //happens for local benchmarks
      {
         bindAddr = InetAddress.getLocalHost();
      }
      else
      {
         bindAddr = InetAddress.getByName(bindAddrStr);
      }
      clusterConfig.validateMembers();
      log.trace("Bind address is:" + bindAddr + "; startPort is:" + startPort);
      nodes = clusterConfig.getMemberAddresses();
      connectionTable = new ConnectionTable(nodes);
   }


   public void start() throws Exception
   {
      srvSock = createServerSocket();
      if (log.isTraceEnabled()) log.trace("ServerSock created, listening on: " + srvSock.getLocalSocketAddress());
      localAddr = new InetSocketAddress(srvSock.getInetAddress(), srvSock.getLocalPort());
      connectionTable.init();

      // accept connections and start 1 Receiver per connection
      Thread acceptor = new Thread()
      {
         public void run()
         {
            while (true)
            {
               try
               {
                  Socket s = srvSock.accept();
                  if (log.isTraceEnabled()) log.trace("Accepted client " + s.getRemoteSocketAddress());
                  ReceiverThread r = new ReceiverThread(s);
                  r.setDaemon(true);
                  receivers.add(r);
                  r.start();
               }
               catch (Exception ex)
               {
                  if (!isStoping)
                  {
                     log.warn("Exception whilst accepting new threads", ex);
                  }
                  break;
               }
            }
         }
      };
      acceptor.setDaemon(true);
      acceptor.start();
   }

   private ServerSocket createServerSocket()
   {
      int start_port1 = startPort;
      server = null;

      while (true)
      {
         try
         {
            server = new ServerSocket(start_port1, 50, bindAddr);
         }
         catch (BindException bindEx)
         {
            log.trace("Binding exception, most likely port " + start_port1 + " is in use. Trying next value. Error:"
                  + bindEx.getMessage());
            start_port1++;
            continue;
         }
         catch (IOException ioEx)
         {
            log.trace("An exception appeared whilst trying to create server socket on port " + start_port1 + ", error:"
                  + ioEx.getMessage());
         }
         break;
      }
      return server;
   }

   public void stop()
   {
      try
      {
         isStoping = true;
         server.close();
         if (log.isTraceEnabled()) log.trace("Successfully closed server socket " + server);
      } catch (IOException e)
      {
         log.warn("Failed to close servet socket for " + server + ", error is " + e.getMessage());
      }
      connectionTable.close();
      for (Iterator it = receivers.iterator(); it.hasNext();)
      {
         ReceiverThread thread = (ReceiverThread) it.next();
         thread.stopThread();
      }
   }

   public void destroy()
   {
      ;
   }

   public void setReceiver(Receiver r)
   {
      this.receiver = r;
   }

   public Map dumpStats()
   {
      return null;
   }

   public void send(Object payload) throws Exception
   {
      connectionTable.writeMessage(payload);
   }


   class ConnectionTable
   {
      List<InetSocketAddress> myNodes;
      final Connection[] connections;

      ConnectionTable(List<InetSocketAddress> nodes) throws Exception
      {
         this.myNodes = nodes;
         connections = new Connection[nodes.size()];
      }


      void init() throws Exception
      {
         int i = 0;
         log.trace("Nodes is " + myNodes);
         for (InetSocketAddress addr : myNodes)
         {
            if (connections[i] == null)
            {
               try
               {
                  connections[i] = new Connection(addr);
                  connections[i].createSocket();
               }
               catch (ConnectException connect_ex)
               {
                  log.trace("-- failed to connect to " + addr);
               }
            }
            i++;
         }
      }

      // todo: parallelize
      void writeMessage(Object msg) throws Exception
      {
         int recieversCount = 0;
         for (Connection c : connections)
         {
            if (c != null)
            {
               try
               {
                  c.writeMessage(msg);
                  recieversCount++;
               }
               catch (Exception e)
               {
                  log.trace("failure(" + e.getMessage() + ") sending message to " + c);
               }
            }
         }
         log.trace("Message successfully sent to " + recieversCount + "/" + connections.length);
      }

      void close()
      {
         for (int i = 0; i < connections.length; i++)
         {
            Connection c = connections[i];
            if (c != null)
               c.close();
         }
      }

      public String toString()
      {
         StringBuffer sb = new StringBuffer();
         for (Iterator it = myNodes.iterator(); it.hasNext();)
         {
            sb.append(it.next()).append(' ');
         }
         return sb.toString();
      }

      public boolean isLocalConnection(SocketAddress socketAddress)
      {
         for (Connection conn : connections)
         {
            SocketAddress addr = conn.sock != null ? conn.sock.getLocalSocketAddress() : null;
            if (addr != null && addr.equals(socketAddress))
            {
               return true;
            }
         }
         return false;
      }
   }

   class Connection
   {
      Socket sock = null;
      DataOutputStream out;
      InetSocketAddress to;
      final Object mutex = new Object();

      Connection(InetSocketAddress addr)
      {
         this.to = addr;
      }

      void createSocket() throws IOException
      {
         log.trace("creating socket connection to host: '" + to.getAddress() + "', port:'" + to.getPort() + "'");
         sock = new Socket(to.getAddress(), to.getPort());
         sock.setSendBufferSize(max_send_buffer_size);
         sock.setReceiveBufferSize(max_receiver_buffer_size);
         out = new DataOutputStream(new BufferedOutputStream(sock.getOutputStream()));
         log.trace("-- connected to " + to + ". Local address is " + sock.getLocalSocketAddress());
      }

      void writeMessage(Object msg) throws Exception
      {
         synchronized (mutex)
         {
            if (sock == null)
            {
               createSocket();
            }
            ObjectOutputStream oos = new ObjectOutputStream(out);
            Message message = new Message(localAddr, msg);
            oos.writeObject(message);
         }
         out.flush();
      }


      void close()
      {
         try
         {
            out.flush();
            sock.close();
         }
         catch (Exception ex)
         {
//            log.warn("problems closing the connection", ex);
         }
      }

      public String toString()
      {
         return "Connection from " + localAddr + " to " + to;
      }
   }


   class ReceiverThread extends Thread
   {
      Socket sock;
      DataInputStream in;
      SocketAddress remote;

      ReceiverThread(Socket sock) throws Exception
      {
         this.sock = sock;
         // sock.setSoTimeout(5000);
         in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
         remote = sock.getRemoteSocketAddress();
      }

      public void run()
      {
         while (sock != null)
         {
            try
            {
               Message message = (Message) new ObjectInputStream(in).readObject();
               if (receiver != null)
                  receiver.receive(message.getSource(), message.getPayload());
            }
            catch (Exception e)
            {
               break;
            }
         }
         log.trace("-- receiver thread for " + remote + " terminated");
      }

      void stopThread()
      {
         try
         {
            log.trace("Closing receiver thread for: " + sock);
            sock.close();
            in.close();
            sock = null;
            this.interrupt();
         }
         catch (Exception ex)
         {
            log.warn("Exception while closing the thread", ex);
         }
      }
   }

   public List parseCommaDelimitedList(String s) throws Exception
   {
      List retval = new ArrayList();
      StringTokenizer tok;
      String hostname, tmp;
      int port;
      InetSocketAddress addr;
      int index;

      if (s == null) return null;
      tok = new StringTokenizer(s, ",");
      while (tok.hasMoreTokens())
      {
         tmp = tok.nextToken();
         index = tmp.indexOf(':');
         if (index == -1)
            throw new Exception("host must be in format <host:port>, was " + tmp);
         hostname = tmp.substring(0, index);
         port = Integer.parseInt(tmp.substring(index + 1));
         addr = new InetSocketAddress(hostname, port);
         retval.add(addr);
      }
      return retval;
   }

   public boolean isLocal(SocketAddress sa)
   {
      return connectionTable.isLocalConnection(sa);
   }

}
