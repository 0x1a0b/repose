/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openrepose.commons.config.parser;

import org.openrepose.commons.config.ConfigurationResourceException;
import org.openrepose.commons.config.parser.common.ConfigurationParser;
import org.openrepose.commons.config.parser.inputstream.InputStreamConfigurationParser;
import org.openrepose.commons.config.parser.jaxb.Element;
import org.openrepose.commons.config.parser.jaxb.JaxbConfigurationParser;
import org.openrepose.commons.config.parser.properties.PropertiesFileConfigurationParser;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;

import javax.xml.bind.JAXBException;

import static org.junit.Assert.*;


/**
 *
 * @author kush5342
 */
public class ConfigurationParserFactoryTest {
    
    
    /**
     * Test of newConfigurationParser method, of class ConfigurationParserFactory.
     */
    @Test
    public void testNewConfigurationParser() {
       
        Throwable caught = null;
      
       try {
            ConfigurationParser result = ConfigurationParserFactory.newConfigurationParser(ConfigurationParserType.valueOf("test"),null);
         } catch (Throwable t) {
            caught = t;
         }
         assertNotNull(caught);
         assertSame(IllegalArgumentException.class, caught.getClass());
    }

    @Test
    public void testNewConfigurationParserWithRawType() throws JAXBException {
        ConfigurationParser result = ConfigurationParserFactory.newConfigurationParser(ConfigurationParserType.RAW, null);
        assertThat(result, IsInstanceOf.instanceOf(InputStreamConfigurationParser.class));
    }

    @Test
    public void testNewConfigurationParserWithPropertiesType() throws JAXBException {
        ConfigurationParser result = ConfigurationParserFactory.newConfigurationParser(ConfigurationParserType.PROPERTIES, null);
        assertThat(result, IsInstanceOf.instanceOf(PropertiesFileConfigurationParser.class));
    }

    /**
     * Test of newInputStreamConfigurationParser method, of class ConfigurationParserFactory.
     */
    @Test
    public void testNewInputStreamConfigurationParser() {

        ConfigurationParser result = ConfigurationParserFactory.newInputStreamConfigurationParser();
        assertThat(result,IsInstanceOf.instanceOf(InputStreamConfigurationParser.class));
      
        
    }

    /**
     * Test of newPropertiesFileConfigurationParser method, of class ConfigurationParserFactory.
     */
    @Test
    public void testNewPropertiesFileConfigurationParser() {
       
        ConfigurationParser result = ConfigurationParserFactory.newPropertiesFileConfigurationParser();
        assertThat(result,IsInstanceOf.instanceOf(PropertiesFileConfigurationParser.class));
    }

    /**
     * Test of getXmlConfigurationParser method, of class ConfigurationParserFactory.
     */
    @Test
    public void testGetXmlConfigurationParser() {
     Throwable caught = null;
      
        try {
        JaxbConfigurationParser result = ConfigurationParserFactory.getXmlConfigurationParser(Element.class,  null);
        
         } catch (Throwable t) {
            caught = t;
         }
         assertNotNull(caught);
         assertSame(ConfigurationResourceException.class, caught.getClass());
        
    }
}
