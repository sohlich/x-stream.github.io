/*
 * Copyright (C) 2012 XStream Committers.
 * All rights reserved.
 *
 * The software in this package is published under the terms of the BSD
 * style license a copy of which has been included with this distribution in
 * the LICENSE.txt file.
 * 
 * Created on 15. May 2012 by Joerg Schaible
 */
package com.thoughtworks.xstream.io.json;

import java.io.Externalizable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.thoughtworks.xstream.io.AbstractDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.StreamException;
import com.thoughtworks.xstream.io.naming.NameCoder;
import com.thoughtworks.xstream.io.xml.QNameMap;
import com.thoughtworks.xstream.io.xml.StaxReader;
import com.thoughtworks.xstream.io.xml.StaxWriter;

import de.odysseus.staxon.json.JsonXMLConfig;
import de.odysseus.staxon.json.JsonXMLConfigBuilder;
import de.odysseus.staxon.json.JsonXMLInputFactory;
import de.odysseus.staxon.json.JsonXMLOutputFactory;
import de.odysseus.staxon.json.JsonXMLStreamConstants;
import de.odysseus.staxon.json.util.XMLMultipleStreamWriter;


/**
 * Simple XStream driver wrapping StAXON's reader and writer. Serializes object from and to
 * JSON.
 * <p>
 * Triggering arrays during serialization is as follows:
 * </p>
 * <ul>
 * <li>if mutiplePaths are specified, they will trigger an array (this requires
 * <code>config.multiplePI() == true</code>)</li>
 * <li>if <code>config.multiplePI() && !config.isAutoArray()</code>, the writer will trigger an
 * array when starting a node for a collection, map or array class</li>
 * <li>if <code>config.autoArray() == true</code>, StAXON will auto-detect multiple elements
 * with same name and trigger arrays accordingly (not recommended, this costs space and time and
 * will fail to write single element arrays)</li>
 * </ul>
 * <p>
 * When using implicit collections, the writer will not be able to trigger an array for that
 * node. Thus, you'll need to pass the paths of those collections to the driver at construction
 * time.
 * </p>
 * 
 * <pre>
 * XStream xstream = new XStream(new StAXONDriver("phoneList"));
 * xstream.addImplicitCollection(Customer.class, "phoneList", "phone", String.class);
 * ...
 * xstream.toXML(...);
 * </pre>
 * <p>
 * This basic implementation fails if a collection wrapper is marshaled with a
 * <code>class</code> attribute, i.e.
 * <code>&lt;content class="java.util.Arrays$ArrayList"&gt;...&lt;content/&gt;</code>. This is
 * because the writer issues an <code>&lt;?xml-multiple?&gt;</code> processing instruction
 * (thereby closing the <code>&lt;content&gt;</code> start tag) before the attribute has
 * written. An improved implementation would delay issuing the processing instruction until the
 * first component is written.
 * </p>
 * 
 * @author Christoph Beck
 */
public class StAXONDriver extends AbstractDriver {
    public static class MultipleStaxWriter extends StaxWriter {
        MultipleStaxWriter(QNameMap qnameMap, XMLStreamWriter out, NameCoder nameCoder)
            throws XMLStreamException {
            super(qnameMap, out, nameCoder);
        }

        /**
         * trigger array if class is collection, map or array
         */
        @Override
        public void startNode(String name, Class clazz) {
            super.startNode(name, clazz);
            if (isMultipleType(clazz)) {
                try {
                    getXMLStreamWriter().writeProcessingInstruction(
                        JsonXMLStreamConstants.MULTIPLE_PI_TARGET);
                } catch (XMLStreamException e) {
                    throw new StreamException(e);
                }
            }
        }

        protected boolean isMultipleType(Class<?> type) {
            if (type == null) {
                return false;
            }
            return Collection.class.isAssignableFrom(type)
                || Map.class.isAssignableFrom(type)
//                || Externalizable.class.isAssignableFrom(type)
                || type.isArray();
        }
    }

    private final JsonXMLInputFactory inputFactory;
    private final JsonXMLOutputFactory outputFactory;
    private final String[] multiplePaths;
    private final boolean multipleWriter;

    public StAXONDriver(String ... multiplePaths) {
        this(new JsonXMLConfigBuilder().build(), multiplePaths);
    }

    public StAXONDriver(JsonXMLConfig config, String ... multiplePaths) {
        this.inputFactory = new JsonXMLInputFactory(config);
        this.outputFactory = new JsonXMLOutputFactory(config);
        this.multiplePaths = multiplePaths;
        this.multipleWriter = config.isMultiplePI() && !config.isAutoArray();

        if (multiplePaths.length > 0 && !config.isMultiplePI()) {
            throw new IllegalArgumentException(
                "Cannot apply multiplePaths (config.isMultiplePI() == false)");
        }
    }

    public HierarchicalStreamReader createReader(final Reader reader) {
        try {
            return new StaxReader(
                new QNameMap(), inputFactory.createXMLStreamReader(reader), getNameCoder());
        } catch (XMLStreamException e) {
            throw new StreamException(e);
        }
    }

    public HierarchicalStreamReader createReader(InputStream input) {
        try {
            return new StaxReader(
                new QNameMap(), inputFactory.createXMLStreamReader(input), getNameCoder());
        } catch (XMLStreamException e) {
            throw new StreamException(e);
        }
    }

    private XMLStreamWriter decorate(XMLStreamWriter writer) throws XMLStreamException {
        if (multiplePaths.length > 0) { // is root element included in (full) paths?
            boolean matchRoot = outputFactory
                .getProperty(JsonXMLOutputFactory.PROP_VIRTUAL_ROOT) == null;
            writer = new XMLMultipleStreamWriter(writer, matchRoot, multiplePaths);
        }
        return writer;
    }

    public HierarchicalStreamWriter createWriter(Writer writer) {
        try {
            XMLStreamWriter streamWriter = decorate(outputFactory.createXMLStreamWriter(writer));
            if (multipleWriter) {
                return new MultipleStaxWriter(new QNameMap(), streamWriter, getNameCoder());
            } else {
                return new StaxWriter(new QNameMap(), streamWriter, getNameCoder());
            }
        } catch (XMLStreamException e) {
            throw new StreamException(e);
        }
    }

    public HierarchicalStreamWriter createWriter(OutputStream output) {
        try {
            XMLStreamWriter streamWriter = decorate(outputFactory.createXMLStreamWriter(output));
            if (multipleWriter) {
                return new MultipleStaxWriter(new QNameMap(), streamWriter, getNameCoder());
            } else {
                return new StaxWriter(new QNameMap(), streamWriter, getNameCoder());
            }
        } catch (XMLStreamException e) {
            throw new StreamException(e);
        }
    }
}
