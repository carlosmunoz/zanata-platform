/*
 * Copyright 2013, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.zanata.adapter.po;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.fedorahosted.tennera.jgettext.HeaderFields;
import org.fedorahosted.tennera.jgettext.Message;
import org.fedorahosted.tennera.jgettext.PoWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.ContentState;
import org.zanata.common.io.DigestWriter;
import org.zanata.common.io.FileDetails;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.HeaderEntry;
import org.zanata.rest.dto.extensions.gettext.PoHeader;
import org.zanata.rest.dto.extensions.gettext.PoTargetHeader;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.util.PathUtil;

public class PoWriter2
{
   private static final Logger log = LoggerFactory.getLogger(PoWriter2.class);
   private final PoWriter poWriter;
   private boolean mapIdToMsgctxt;
   private static final int DEFAULT_NPLURALS = 1;

   // TODO Expose and use the one in org.fedorahosted.tennera.jgettext.HeaderFields
   // Modified version to extract the nplurals value
   private static final Pattern pluralPattern = Pattern.compile(
         "nplurals(\\s*?)=(\\s*?)(\\d*?)(\\s*?);(\\s*?)(.*)",
         Pattern.CASE_INSENSITIVE);

   /**
    * @param encodeTabs
    * @param mapIdToMsgctxt true to output zanata id as msgctxt, which can
    *           be used by {@link PoReader2} to correctly match the ID for
    *           text flows that are not originally from po documents. This
    *           should be false if the documents to be written were originally
    *           in po files.
    */
   public PoWriter2(boolean encodeTabs, boolean mapIdToMsgctxt)
   {
      this.poWriter = new PoWriter(encodeTabs);
      this.mapIdToMsgctxt = mapIdToMsgctxt;
   }

   public PoWriter2(boolean encodeTabs)
   {
      this(encodeTabs, false);
   }

   public PoWriter2()
   {
      this(false);
   }

   /**
    * Creates dir and its parents
    * @param dir
    * @throws IOException
    */
   private void makeDirs(File dir) throws IOException
   {
      if (!dir.exists())
      {
         if (!dir.mkdirs())
            throw new IOException("unable to create output directory: " + dir);
      }
   }

   /**
    * Generates a pot file from Resource (document), using the publican
    * directory layout.
    * 
    * @param baseDir
    * @param doc
    * @throws IOException
    */
   @Deprecated
   public void writePot(File baseDir, Resource doc) throws IOException
   {
      // write the POT file to pot/$name.pot
      File potDir = new File(baseDir, "pot");
      writePotToDir(potDir, doc);
   }

   /**
    * Generates a pot file from Resource (document), in the specified directory.
    * 
    * @param potDir
    * @param doc
    * @throws IOException
    */
   @Deprecated
   public void writePotToDir(File potDir, Resource doc) throws IOException
   {
      // write the POT file to $potDir/$name.pot
      File potFile = new File(potDir, doc.getName() + ".pot");
      writePotToFile(potFile, doc);
   }

   /**
    * Generates a pot file from Resource (document).
    *
    * @param doc
    * @param potFile file to be written
    * @throws IOException
    */
   public void writePotToFile(File potFile, Resource doc) throws IOException
   {
      PathUtil.makeParents(potFile);
      FileWriter fWriter = new FileWriter(potFile);
      write(fWriter, "UTF-8", doc, null);
   }

   /**
    * Generates a pot file from a Resource, writing it directly to an output stream.
    */
   public void writePot(OutputStream stream, String charset, Resource doc) throws IOException
   {
      OutputStreamWriter osWriter = new OutputStreamWriter(stream, charset);
      write(osWriter, charset, doc, null);
   }

   /**
    * Generates a po file from a Resource and a TranslationsResource, using the
    * publican directory layout.
    * 
    * @param baseDir
    * @param doc
    * @param locale
    * @param targetDoc
    * @throws IOException
    */
   @Deprecated
   public void writePo(File baseDir, Resource doc, String locale, TranslationsResource targetDoc) throws IOException
   {
      // write the PO file to $locale/$name.po
      File localeDir = new File(baseDir, locale);
      File poFile = new File(localeDir, doc.getName() + ".po");
      writePoToFile(poFile, doc, targetDoc);
   }

   /**
    * Generates a po file from a Resource and a TranslationsResource.
    *
    * @param poFile file to be written
    * @param doc a source Resource whose translation is to be written
    * @param targetDoc translated document to be written
    * @return
    * @throws IOException
    */
   public FileDetails writePoToFile(File poFile, Resource doc, TranslationsResource targetDoc) throws IOException
   {
      makeDirs(poFile.getParentFile());
      MessageDigest md5Digest = null;
      try
      {
         md5Digest = MessageDigest.getInstance("MD5");
      }
      catch (NoSuchAlgorithmException e)
      {
         throw new RuntimeException(e);
      }
      FileWriter fWriter = new FileWriter(poFile);
      DigestWriter dWriter = new DigestWriter(fWriter, md5Digest);
      write(dWriter, "UTF-8", doc, targetDoc);

      FileDetails details = new FileDetails(poFile);
      details.setMd5(new String(Hex.encodeHex(md5Digest.digest())));
      return details;
   }

   /**
    * Generates a po file from a Resource and a TranslationsResource, writing it
    * directly to an output stream.
    * 
    * @param stream
    * @param doc
    * @param targetDoc
    * @throws IOException
    */
   public void writePo(OutputStream stream, String charset, Resource doc, TranslationsResource targetDoc) throws IOException
   {
      OutputStreamWriter osWriter = new OutputStreamWriter(stream, charset);
      write(osWriter, charset, doc, targetDoc);
   }

   /**
    * Generates a pot or po file from a Resource and/or TranslationsResource.
    * If targetDoc is non-null, a po file will be generated from the
    * Resource and TranslationsResource, otherwise a pot file
    * will be generated from the Resource only.
    * 
    * @param writer
    * @param document
    * @param targetDoc
    * @throws IOException
    */
   private void write(Writer writer, String charset, Resource document, TranslationsResource targetDoc) throws IOException
   {
      PoHeader poHeader = document.getExtensions(true).findByType(PoHeader.class);
      HeaderFields hf = new HeaderFields();
      // we don't expect a pot header for mapped non-pot documents
      if (poHeader == null)
      {
         if (!mapIdToMsgctxt)
         {
            log.warn("No PO header in document named " + document.getName());
         }
      }
      else
      {
         copyToHeaderFields(hf, poHeader.getEntries());
      }
      setEncodingHeaderFields(hf, charset);
      Map<String, TextFlowTarget> targets = new HashMap<String, TextFlowTarget>();
      Message headerMessage = null;
      int nPlurals = DEFAULT_NPLURALS;
      if (targetDoc != null)
      {
         PoTargetHeader poTargetHeader = null;
         poTargetHeader = targetDoc.getExtensions(true).findByType(PoTargetHeader.class);
         if (poTargetHeader != null)
         {
            copyToHeaderFields(hf, poTargetHeader.getEntries());
            headerMessage = hf.unwrap();
            headerMessage.setFuzzy(false); // By default, header message unwraps as fuzzy, so avoid it
            copyCommentsToHeader(poTargetHeader, headerMessage);
            nPlurals = extractNPlurals(poTargetHeader);
         }
         for (TextFlowTarget target : targetDoc.getTextFlowTargets())
         {
            targets.put(target.getResId(), target);
         }
      }
      if (headerMessage == null)
      {
         headerMessage = hf.unwrap();
      }
      poWriter.write(headerMessage, writer);
      writer.write("\n");

      // first write header
      for (TextFlow textFlow : document.getTextFlows())
      {
         PotEntryHeader entryData = textFlow.getExtensions(true).findByType(PotEntryHeader.class);
         SimpleComment srcComment = textFlow.getExtensions().findByType(SimpleComment.class);
         Message message = new Message();
         copyTFContentsToMessage(textFlow, message);

         List<String> tftContents = new ArrayList<String>();
         TextFlowTarget tfTarget = targets.get(textFlow.getId());
         if (tfTarget != null)
         {
            if (!tfTarget.getResId().equals(textFlow.getId()))
            {
               throw new RuntimeException("ID from target doesn't match text-flow ID");
            }
            tftContents.addAll(tfTarget.getContents());
            if (tfTarget.getState() == ContentState.NeedReview)
            {
               message.setFuzzy(true);
            }
            copyCommentsToMessage(tfTarget, message);
         }
         copyTFTContentsToMessage(textFlow, tftContents, nPlurals, message);

         if (entryData != null)
         {
            copyMetadataToMessage(entryData, srcComment, message);
         }
         else
         {
            // we don't expect a pot header for mapped non-pot documents
            if (!mapIdToMsgctxt)
            {
               log.warn("Missing POT entry for text-flow ID " + textFlow.getId());
            }
         }

         if (mapIdToMsgctxt)
         {
            mapIdToMsgctxt(message, textFlow.getId());
         }

         poWriter.write(message, writer);
         writer.write("\n");
      }
   }

   /**
    * Populate msgctxt with text flow id.
    * 
    * @throws RuntimeException if there is already a value in msgctxt
    */
   private void mapIdToMsgctxt(Message message, String textFlowId)
   {
      // safety check to avoid clobbering existing msgctxt
      // (this mapping should not be used for resources from po files)
      if (message.getMsgctxt() != null)
      {
         throw new RuntimeException("Mapping id to msgctxt, but there is already a msgctxt for text flow id: " + textFlowId);
      }
      message.setMsgctxt(textFlowId);
   }

   private static void copyCommentsToHeader(PoTargetHeader poTargetHeader, Message headerMessage)
   {
      for (String s : poTargetHeader.getComment().split("\n"))
      {
         headerMessage.addComment(s);
      }
   }

   private void copyTFContentsToMessage(TextFlow textFlow, Message message)
   {
      List<String> tfContents = textFlow.getContents();
      message.setMsgid(tfContents.get(0));

      if (textFlow.isPlural())
      {
         if (tfContents.size() < 1)
         {
            throw new RuntimeException("textflow has plural flag but only has one form: resId=" + textFlow.getId());
         }
         message.setMsgidPlural(tfContents.get(1));
      }
      else
      {
         if (tfContents.size() > 1)
         {
            throw new RuntimeException("textflow has no plural flag but multiple plural forms: resId=" + textFlow.getId());
         }
      }

      if (tfContents.size() > 2)
      {
         throw new RuntimeException("POT format only supports 2 plural forms: resId=" + textFlow.getId());
      }
   }

   private void copyCommentsToMessage(TextFlowTarget tfTarget, Message message)
   {
      SimpleComment poComment = tfTarget.getExtensions().findByType(SimpleComment.class);
      if (poComment != null)
      {
         String[] comments = poComment.getValue().split("\n");
         if (comments.length == 1 && comments[0].isEmpty())
         {
            // nothing
         }
         else
         {
            for (String comment : comments)
            {
               message.getComments().add(comment);
            }
         }
      }
   }

   private void copyTFTContentsToMessage(TextFlow textFlow, List<String> tftContents, int nPlurals, Message message)
   {
      if (message.isPlural())
      {
         while (tftContents.size() < nPlurals)
         {
            tftContents.add("");
         }
         for (int i = 0; i < tftContents.size(); i++)
         {
            message.addMsgstrPlural(tftContents.get(i), i);
         }
         if (tftContents.size() > nPlurals)
         {
            log.warn("too many plural forms for text flow: resId=" + textFlow.getId());
         }
      }
      else
      {
         if (tftContents.size() == 0)
         {
            message.setMsgstr("");
         }
         else
         {
            message.setMsgstr(tftContents.get(0));
            if (tftContents.size() > 1)
            {
               throw new RuntimeException("plural forms not enabled for this text flow: resId=" + textFlow.getId());
            }
         }
      }
   }

   static void setEncodingHeaderFields(HeaderFields hf, String charset)
   {
      hf.setValue(HeaderFields.KEY_MimeVersion, "1.0");
      hf.setValue(HeaderFields.KEY_ContentTransferEncoding, "8bit");

      String ct, contentType = hf.getValue(HeaderFields.KEY_ContentType);
      if (contentType == null)
      {
         ct = "text/plain; charset=" + charset;
      }
      else
      {
         ct = contentType.replaceFirst("charset=[^;]*", "charset=" + charset);
      }
      hf.setValue(HeaderFields.KEY_ContentType, ct);
   }

   static void copyToHeaderFields(HeaderFields hf, final List<HeaderEntry> entries)
   {
      for (HeaderEntry e : entries)
      {
         hf.setValue(e.getKey(), e.getValue());
      }
   }

   private static void copyMetadataToMessage(PotEntryHeader data, SimpleComment simpleComment, Message message)
   {
      if (data != null)
      {
         String context = data.getContext();
         if (context != null)
            message.setMsgctxt(context);
         for (String flag : data.getFlags())
         {
            message.addFormat(flag);
         }
         for (String ref : data.getReferences())
         {
            message.addSourceReference(ref);
         }
      }
      if (simpleComment != null)
      {
         String[] comments = StringUtils.splitPreserveAllTokens(simpleComment.getValue(), "\n");
         if (!(comments.length == 1 && comments[0].isEmpty()))
         {
            for (String comment : comments)
            {
               message.addExtractedComment(comment);
            }
         }
      }
   }

   /**
    * Determines the number of plural entries to fill for the TransResource.
    * If this value can't be found, this method will provide a sensible default.
    */
   /*
    * TODO This method is similar to org.zanata.rest.service.ResourceUtils,
    * so perhaps it should be placed in a common class.
    */
   private static int extractNPlurals(PoTargetHeader header)
   {
      for (HeaderEntry entry : header.getEntries())
      {
         if (entry.getKey().equals("Plural-Forms"))
         {
            Matcher pluralMatcher = pluralPattern.matcher(entry.getValue());
            if (pluralMatcher.find())
            {
               String pluralStr = pluralMatcher.group(3);
               return Integer.parseInt(pluralStr);
            }
         }
      }

      // No suitable nplural entry found. return default
      return DEFAULT_NPLURALS;
   }

}