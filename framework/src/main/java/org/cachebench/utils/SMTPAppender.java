/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.cachebench.utils;

import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.TriggeringEventEvaluator;
import org.apache.log4j.spi.LoggingEvent;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;


public class SMTPAppender extends org.apache.log4j.net.SMTPAppender
{

   public SMTPAppender()
   {
      //support all message types
      super(new TriggeringEventEvaluator()
      {
         public boolean isTriggeringEvent(LoggingEvent event)
         {
            return true;
         }
      });
   }

   /**
    * Activate the specified options, such as the smtp host, the
    * recipient, from, etc.
    */
   public void activateOptions()
   {
      final Properties props = new Properties();
      try
      {
         props.load(getClass().getClassLoader().getResourceAsStream("email.properties"));
      } catch (Exception e)
      {
         e.printStackTrace();
         throw new RuntimeException(e);
      }

      try
      {
         Session session = Session.getInstance(props, new Authenticator()
         {
            @Override
            protected PasswordAuthentication getPasswordAuthentication()
            {
               return new PasswordAuthentication(props.getProperty("mail.smtp.user"),props.getProperty("mail.smtp.password"));
            }
         });
         //session.setDebug(true);
         msg = new MimeMessage(session);


         if (getFrom() != null)
            msg.setFrom(new InternetAddress(getFrom()));
         else
            msg.setFrom();

         msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(getTo(), true));
         if (getSubject() != null)
            msg.setSubject(getSubject());
      } catch (Throwable e)
      {
         LogLog.error("Could not activate SMTPAppender options.", e);
      }
   }


}
