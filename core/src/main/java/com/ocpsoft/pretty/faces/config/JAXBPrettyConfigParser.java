package com.ocpsoft.pretty.faces.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import javax.servlet.ServletContext;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlSchema;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import com.ocpsoft.pretty.faces.config.types.PrettyConfigElement;

/**
 * Implementation of {@link PrettyConfigParser} that uses the JAXB API to parse
 * the PrettyFaces configuration.
 * 
 * @author Christian Kaltepoth <christian@kaltepoth.de>
 */
public class JAXBPrettyConfigParser implements PrettyConfigParser
{

   private static final String JAXB_CONTEXT_ATTRIBUTE = JAXBPrettyConfigParser.class.getName() + ".JAXBContext";
   
   private static final String SCHEMA_LOCATION = "META-INF/ocpsoft-pretty-faces-3.3.1.xsd";

   private final static String JAXB_CLASS_PACKAGE = "com.ocpsoft.pretty.faces.config.types";
   
   private final SAXParserFactory saxParserFactory;

   private JAXBContext jaxbContext;

   private Schema schema;
   
   /**
    * Creates a new {@link JAXBPrettyConfigParser}
    */
   public JAXBPrettyConfigParser(ServletContext servletContext)
   {

      // try to find the cached JAXBContext
      jaxbContext = (JAXBContext) servletContext.getAttribute(JAXB_CONTEXT_ATTRIBUTE);

      // no existing JAXBContext? We will create a new one!
      if (jaxbContext == null)
      {

         try
         {
            // build JAXBContext using the package we generate the classes to
            jaxbContext = JAXBContext.newInstance(JAXB_CLASS_PACKAGE);

            // cache the JAXBContext using the ServletContext
            servletContext.setAttribute(JAXB_CONTEXT_ATTRIBUTE, jaxbContext);

         }
         catch (JAXBException e)
         {
            throw new IllegalStateException(e);
         }

      }
      
      // SAXParserFactory is NOT thread-safe
      saxParserFactory = SAXParserFactory.newInstance();
      saxParserFactory.setNamespaceAware(true);
   
   }

   /*
    * @see
    * com.ocpsoft.pretty.faces.config.PrettyConfigParser#parse(com.ocpsoft.pretty
    * .faces.config.PrettyConfigBuilder, java.io.InputStream)
    */
   public void parse(PrettyConfigBuilder builder, InputStream resource, boolean validate) throws IOException,
         SAXException
   {

      try
      {

         // create the JAXB unmarshaller
         Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();

         // use a special XMLFilter to do the namespace replacement
         XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();
         XMLFilter namespaceFilter = new NamespaceFilter(xmlReader, getCurrentPrettyFacesNamespaceURI());
         xmlReader.setContentHandler(unmarshaller.getUnmarshallerHandler());
         SAXSource source = new SAXSource(namespaceFilter, new InputSource(resource));

         // optional validation
         if (validate)
         {

            // lazily load the schema from the classpath
            if (schema == null)
            {
               schema = loadSchema();
            }
            
            // enable validation
            unmarshaller.setSchema( schema );
            
         }
         
         // parse the document and get the PrettyConfigElement
         JAXBElement<?> e = (JAXBElement<?>) unmarshaller.unmarshal(source);
         PrettyConfigElement prettyConfigElement = (PrettyConfigElement) e.getValue();

         // build the PrettyConfig and append it to the builder
         PrettyConfig config = new PrettyConfig(prettyConfigElement);
         builder.addFromConfig(config);

      }
      catch (JAXBException e)
      {
         throw new IOException(e);
      }
      catch (ParserConfigurationException e)
      {
         throw new IOException(e);
      }
   }

   /**
    * Loads the PrettyFaces XML schema from the classpath.
    */
   private static Schema loadSchema() throws SAXException
   {
      // load schema from classpath
      SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      URL schemaUrl = classLoader.getResource(SCHEMA_LOCATION);
      if(schemaUrl == null) {
         throw new IllegalStateException("Unable to load schema from: "+SCHEMA_LOCATION);
      }
      return schemaFactory.newSchema(schemaUrl);
   }

   /**
    * Returns the current PrettyFaces XML configuration namespace URI.
    */
   private String getCurrentPrettyFacesNamespaceURI()
   {
      Package jaxbClassPackage = Package.getPackage(JAXB_CLASS_PACKAGE);
      XmlSchema xmlSchemaAnnotation = jaxbClassPackage.getAnnotation(XmlSchema.class);
      return xmlSchemaAnnotation.namespace().intern();
   }

   /**
    * This class is used to replace older PrettyFaces namespace URIs with the
    * most current one. This is required so that the JAXB classes generated by
    * the most current schema work correctly.
    * 
    * @author Christian Kaltepoth <christian@kaltepoth.de>
    */
   private class NamespaceFilter extends XMLFilterImpl
   {

      /**
       * The prefix of namespaces that have to be replaced
       */
      private final static String EXPECTED_URI_PREFIX = "http://ocpsoft.com/prettyfaces/";

      /**
       * The most new namespace
       */
      private final String newUri;

      public NamespaceFilter(XMLReader reader, String newUri)
      {
         super(reader);
         this.newUri = newUri;
      }

      public void startPrefixMapping(String prefix, String uri) throws SAXException
      {
         super.startPrefixMapping(prefix, replaceNamespace(uri));
      }

      public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException
      {

         super.startElement(replaceNamespace(uri), localName, qName, attributes);
      }

      public void endElement(String uri, String localName, String qName) throws SAXException
      {
         super.endElement(replaceNamespace(uri), localName, qName);
      }

      private String replaceNamespace(String uri)
      {
         if (uri != null)
         {
            return uri.startsWith(EXPECTED_URI_PREFIX) ? newUri : uri;
         }
         return null;
      }

   }

}
