/* Copyright (c) 2012 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.csw.response;

import java.util.Collection;
import java.util.Set;

import net.opengis.cat.csw20.RequestBaseType;

import org.geoserver.csw.records.CSWRecordDescriptor;
import org.geoserver.platform.ServiceException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.xml.transform.Translator;
import org.opengis.feature.ComplexAttribute;
import org.opengis.feature.Feature;
import org.opengis.feature.Property;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Encodes a FeatureCollection containing {@link CSWRecordDescriptor#RECORD} features into the specified
 * XML according to the chosen profile, brief, summary or full
 * 
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class CSWRecordTransformer extends AbstractRecordTransformer {

    static final String CSW_ROOT_LOCATION = "http://schemas.opengis.net/csw/2.0.2/";
    
    private static final AttributeDescriptor DC_TITLE = CSWRecordDescriptor.getDescriptor("title");

    public CSWRecordTransformer(RequestBaseType request, boolean canonicalSchemaLocation) {
        super(request, canonicalSchemaLocation);
    }

    @Override
    public Translator createTranslator(ContentHandler handler) {
        return new CSWRecordTranslator(handler);
    }
    
    @Override
    public boolean canHandleRespose(CSWRecordsResult response) {
        return true;
    }

    class CSWRecordTranslator extends AbstractRecordTranslator {

        public CSWRecordTranslator(ContentHandler handler) {
            super(handler);
        }

        public void encode(CSWRecordsResult response, Feature f) {
            String element = "csw:" + getRecordElement(response);
            start(element);
            Set<Name> elements = getElements(response);
            
            // encode all elements besides bbox
            if(elements != null && ! element.isEmpty()) {
                // brief and summary have a specific order
                for (Name name : elements) {
                    Collection<Property> properties = f.getProperties(name);
                    if(properties != null && !properties.isEmpty()) {
                        for (Property p : properties) {
                            encodProperty(f, p);
                        }
                    } else if(DC_TITLE.getName().equals(name)) {
                        // dc:title is mandatory even if we don't have a value for it
                        element("dc:title", null);
                    }
                }
            } else {
                // csw:Record has freeform order
                for (Property p : f.getProperties()) {
                    if (elements == null || elements.contains(p.getName())) {
                        encodProperty(f, p);
                    }
                }
            }
            
            // encode the bbox if present
            if(elements == null || elements.contains(CSWRecordDescriptor.RECORD_BBOX_NAME)) {
                Collection<Property> bboxes = f.getProperties(CSWRecordDescriptor.RECORD_BBOX_NAME);
                if(bboxes != null) {
                    for (Property p : bboxes) {
                        try {
                            ReferencedEnvelope re = (ReferencedEnvelope) p.getValue();
                            ReferencedEnvelope wgs84re = re.transform(
                                    CRS.decode(CSWRecordDescriptor.DEFAULT_CRS_NAME), true);

                            String minx = String.valueOf(wgs84re.getMinX());
                            String miny = String.valueOf(wgs84re.getMinY());
                            String maxx = String.valueOf(wgs84re.getMaxX());
                            String maxy = String.valueOf(wgs84re.getMaxY());

                            AttributesImpl attributes = new AttributesImpl();
                            addAttribute(attributes, "crs", CSWRecordDescriptor.DEFAULT_CRS_NAME);
                            start("ows:BoundingBox");
                            element("ows:LowerCorner", minx + " " + miny);
                            element("ows:UpperCorner", maxx + " " + maxy);
                            end("ows:BoundingBox");
                        } catch (Exception e) {
                            throw new ServiceException("Failed to encode the current record: " + f,
                                    e);
                        }
                    }
                }

            }
            end(element);
        }

        private void encodProperty(Feature f, Property p) {
            if (p.getType() == CSWRecordDescriptor.SIMPLE_LITERAL) {
                encodeSimpleLiteral(p);
            } else if (CSWRecordDescriptor.RECORD_BBOX_NAME.equals(p.getName())) {
                // skip it for the moment, it is constrained to be last
            } else if(CSWRecordDescriptor.RECORD_GEOMETRY_NAME.equals(p.getName())) {
                // skip it, we only use it for filtering
            } else {
                throw new IllegalArgumentException("Don't know how to encode property " + p
                        + " in record " + f);
            }
        }

        private void encodeSimpleLiteral(Property p) {
            ComplexAttribute sl = (ComplexAttribute) p;
            String scheme = (String) sl.getProperty("scheme").getValue();
            String value = (String) sl.getProperty("value").getValue();
            Name dn = p.getDescriptor().getName();
            String name = dn.getLocalPart();
            String prefix = CSWRecordDescriptor.NAMESPACES.getPrefix(dn.getNamespaceURI());
            if (scheme == null) {
                element(prefix + ":" + name, value);
            } else {
                AttributesImpl attributes = new AttributesImpl();
                addAttribute(attributes, "scheme", scheme);
                element(prefix + ":" + name, value, attributes);
            }
        }

        private String getRecordElement(CSWRecordsResult response) {
            switch (response.getElementSet()) {
            case BRIEF:
                return "BriefRecord";
            case SUMMARY:
                return "SummaryRecord";
            default:
                return "Record";
            }
        }

        private Set<Name> getElements(CSWRecordsResult response) {
            switch (response.getElementSet()) {
            case BRIEF:
                return CSWRecordDescriptor.BRIEF_ELEMENTS;
            case SUMMARY:
                return CSWRecordDescriptor.SUMMARY_ELEMENTS;
            default:
                return null;
            }
        }

    }

    

}
